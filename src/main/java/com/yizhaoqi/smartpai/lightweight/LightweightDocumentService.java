package com.yizhaoqi.smartpai.lightweight;

/* 旧原型（停用）：内存文档、散列向量与共享文件索引。
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.yizhaoqi.smartpai.entity.EsDocument;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.model.LightweightChunk;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.repository.LightweightChunkRepository;
import com.yizhaoqi.smartpai.service.ElasticsearchService;
import com.yizhaoqi.smartpai.service.index.KnowledgeIndexNames;
import org.springframework.scheduling.annotation.Async;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

// Local-disk upload, Tika extraction, and small sliding-window chunker.
@Service
public class LightweightDocumentService {
    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 120;
    private final LightweightStorageProperties properties;
    private final TaskExecutor parseExecutor;
    private final FileUploadRepository fileUploadRepository;
    private final LightweightChunkRepository chunkRepository;
    private final ElasticsearchClient esClient;
    private final ElasticsearchService elasticsearchService;
    private final Tika tika = new Tika();

    public LightweightDocumentService(LightweightStorageProperties properties,
                                      @Qualifier("documentParseExecutor") TaskExecutor parseExecutor,
                                      FileUploadRepository fileUploadRepository,
                                      LightweightChunkRepository chunkRepository,
                                      ElasticsearchClient esClient,
                                      ElasticsearchService elasticsearchService) {
        this.properties = properties;
        this.parseExecutor = parseExecutor;
        this.fileUploadRepository = fileUploadRepository;
        this.chunkRepository = chunkRepository;
        this.esClient = esClient;
        this.elasticsearchService = elasticsearchService;
    }

    public LightweightDocument accept(MultipartFile file, String ownerId, boolean isPublic) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("文件不能为空");
        if (file.getSize() > properties.getMaxFileSizeBytes()) throw new IllegalArgumentException("文件超过大小限制");
        String originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "document.txt");
        String extension = extensionOf(originalName);
        if (!properties.getAllowedExtensions().contains(extension)) throw new IllegalArgumentException("仅支持 PDF、Word、Markdown、TXT 文档");
        byte[] bytes = file.getBytes();
        String md5 = md5(bytes);
        Path root = properties.getRoot().toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path target = root.resolve(md5 + "." + extension).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("非法文件名");
        if (!Files.exists(target)) Files.write(target, bytes);
        String owner = Objects.requireNonNullElse(ownerId, "anonymous");
        Optional<FileUpload> existing = fileUploadRepository
                .findFirstByFileMd5AndUserIdOrderByCreatedAtDesc(md5, owner);
        if (existing.isPresent()) return toDocument(existing.get());

        FileUpload upload = new FileUpload();
        upload.setFileMd5(md5);
        upload.setFileName(originalName);
        upload.setTotalSize(bytes.length);
        upload.setStatus(FileUpload.STATUS_COMPLETED);
        upload.setUserId(owner);
        upload.setPublic(isPublic);
        upload.setVectorizationStatus(FileUpload.VECTORIZATION_STATUS_PROCESSING);
        upload.setEstimatedChunkCount(0);
        upload = fileUploadRepository.save(upload);
        // Submit through the named executor so the @Async parser is not bypassed
        // by a same-bean call (the upload request returns immediately).
        FileUpload persistedUpload = upload;
        parseExecutor.execute(() -> processAsync(persistedUpload, target));
        return toDocument(upload);
    }

    public List<LightweightDocument> list(String requester) {
        String user = Objects.requireNonNullElse(requester, "anonymous");
        return fileUploadRepository.findByUserIdOrIsPublicTrue(user).stream()
                .filter(file -> file.getStatus() == FileUpload.STATUS_COMPLETED)
                .map(this::toDocument)
                .sorted(Comparator.comparing(LightweightDocument::createdAt).reversed()).toList();
    }

    public List<SearchHit> search(String question, String requester) {
        try {
            return searchElasticsearch(question, Objects.requireNonNullElse(requester, "anonymous"));
        } catch (Exception ignored) {
            // ES 暂不可用时只做 MySQL 原文兜底，正常路径始终走 ES BM25 + KNN + RRF。
        }
        String[] terms = question.toLowerCase(Locale.ROOT).split("[\\s,，。！？、；：]+", -1);
        return list(requester).stream().filter(doc -> "READY".equals(doc.status()))
                .flatMap(doc -> doc.chunks().stream().map(chunk -> new SearchHit(doc, chunk, score(chunk, terms))))
                .filter(hit -> hit.score() > 0).sorted(Comparator.comparingDouble(SearchHit::score).reversed()).limit(5).toList();
    }

    @Async("documentParseExecutor")
    public void processAsync(FileUpload upload, Path path) {
        String id = upload.getFileMd5();
        try (InputStream input = Files.newInputStream(path)) {
            List<String> chunks = split(tika.parseToString(input));
            chunkRepository.deleteByFileMd5AndUserId(id, upload.getUserId());
            List<LightweightChunk> records = new ArrayList<>();
            List<EsDocument> indexed = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                LightweightChunk record = new LightweightChunk();
                record.setFileMd5(id);
                record.setUserId(upload.getUserId());
                record.setChunkIndex(i);
                record.setTextContent(chunks.get(i));
                records.add(record);
                indexed.add(new EsDocument(id + "_" + i, id, i, chunks.get(i), null, null,
                        vectorize(chunks.get(i)), "lightweight-hash-2048", upload.getUserId(), null, upload.isPublic()));
            }
            chunkRepository.saveAll(records);
            elasticsearchService.deleteByFileMd5(id);
            if (!indexed.isEmpty()) elasticsearchService.bulkIndex(indexed);
            upload.setEstimatedChunkCount(chunks.size());
            upload.setActualChunkCount(chunks.size());
            upload.setVectorizationStatus(FileUpload.VECTORIZATION_STATUS_COMPLETED);
            upload.setVectorizationErrorMessage(null);
            fileUploadRepository.save(upload);
        } catch (IOException | TikaException ex) {
            upload.setVectorizationStatus(FileUpload.VECTORIZATION_STATUS_FAILED);
            upload.setVectorizationErrorMessage(ex.getMessage());
            fileUploadRepository.save(upload);
        }
    }

    private List<SearchHit> searchElasticsearch(String question, String requester) throws IOException {
        Query permission = Query.of(q -> q.bool(b -> b
                .should(s -> s.term(t -> t.field("userId").value(requester)))
                .should(s -> s.term(t -> t.field("isPublic").value(true)))
                .minimumShouldMatch("1")));
        SearchRequest bm25Request = SearchRequest.of(s -> s
                .index(KnowledgeIndexNames.ALIAS)
                .source(src -> src.filter(f -> f.excludes("vector")))
                .query(q -> q.bool(b -> b.must(m -> m.match(v -> v.field("textContent").query(question)))
                        .filter(permission)))
                .size(50));
        List<Float> queryVector = toVectorList(vectorize(question));
        SearchRequest knnRequest = SearchRequest.of(s -> s
                .index(KnowledgeIndexNames.ALIAS)
                .source(src -> src.filter(f -> f.excludes("vector")))
                .knn(k -> k.field("vector").queryVector(queryVector).k(50).numCandidates(100).filter(permission))
                .size(50));
        SearchResponse<EsDocument> bm25 = esClient.search(bm25Request, EsDocument.class);
        SearchResponse<EsDocument> knn = esClient.search(knnRequest, EsDocument.class);

        Map<String, RankedHit> fused = new LinkedHashMap<>();
        addRanked(fused, bm25.hits().hits());
        addRanked(fused, knn.hits().hits());
        return fused.values().stream()
                .sorted(Comparator.comparingDouble(RankedHit::score).reversed())
                .limit(5)
                .map(hit -> new SearchHit(toSearchDocument(hit.source()), hit.source().getTextContent(), hit.score()))
                .toList();
    }

    private void addRanked(Map<String, RankedHit> fused, List<Hit<EsDocument>> hits) {
        for (int i = 0; i < hits.size(); i++) {
            Hit<EsDocument> hit = hits.get(i);
            if (hit.id() == null || hit.source() == null) continue;
            RankedHit current = fused.get(hit.id());
            double score = 1d / (60d + i + 1d);
            fused.put(hit.id(), current == null
                    ? new RankedHit(hit.source(), score)
                    : new RankedHit(current.source(), current.score() + score));
        }
    }

    private LightweightDocument toSearchDocument(EsDocument source) {
        FileUpload file = fileUploadRepository.findFirstByFileMd5AndUserIdOrderByCreatedAtDesc(
                source.getFileMd5(), source.getUserId()).orElse(null);
        Instant createdAt = file == null || file.getCreatedAt() == null
                ? Instant.now() : file.getCreatedAt().toInstant(java.time.ZoneOffset.UTC);
        return new LightweightDocument(source.getFileMd5(), source.getFileName(), source.getFileMd5(),
                source.getUserId(), source.isPublic(), source.getTextContent() == null ? 0 : source.getTextContent().length(),
                "READY", createdAt, List.of(source.getTextContent()));
    }

    private LightweightDocument toDocument(FileUpload file) {
        String owner = Objects.requireNonNullElse(file.getUserId(), "anonymous");
        List<String> chunks = chunkRepository.findByFileMd5AndUserIdOrderByChunkIndexAsc(file.getFileMd5(), owner)
                .stream().map(LightweightChunk::getTextContent).toList();
        String status = FileUpload.VECTORIZATION_STATUS_COMPLETED.equals(file.getVectorizationStatus())
                ? "READY" : FileUpload.VECTORIZATION_STATUS_FAILED.equals(file.getVectorizationStatus()) ? "FAILED" : "PROCESSING";
        Instant createdAt = file.getCreatedAt() == null ? Instant.now()
                : file.getCreatedAt().toInstant(java.time.ZoneOffset.UTC);
        return new LightweightDocument(file.getFileMd5(), file.getFileName(), file.getFileMd5(), owner,
                file.isPublic(), file.getTotalSize(), status, createdAt, chunks);
    }

    private float[] vectorize(String text) {
        float[] vector = new float[2048];
        for (String term : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}]+")) {
            if (term.isBlank()) continue;
            int index = Math.floorMod(term.hashCode(), vector.length);
            vector[index] += 1f;
        }
        double norm = 0d;
        for (float value : vector) norm += value * value;
        if (norm > 0d) {
            float scale = (float) (1d / Math.sqrt(norm));
            for (int i = 0; i < vector.length; i++) vector[i] *= scale;
        }
        return vector;
    }

    private List<Float> toVectorList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) values.add(value);
        return values;
    }

    private List<String> split(String text) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").trim();
        if (normalized.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (int start = 0; start < normalized.length(); start += CHUNK_SIZE - CHUNK_OVERLAP) {
            int end = Math.min(normalized.length(), start + CHUNK_SIZE);
            result.add(normalized.substring(start, end));
            if (end == normalized.length()) break;
        }
        return result;
    }

    private double score(String chunk, String[] terms) {
        String haystack = chunk.toLowerCase(Locale.ROOT);
        long matched = java.util.Arrays.stream(terms).filter(term -> !term.isBlank() && haystack.contains(term)).count();
        return terms.length == 0 ? 0 : (double) matched / terms.length;
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "txt" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String md5(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(bytes);
            StringBuilder result = new StringBuilder(32);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("MD5 不可用", ex); }
    }

    public record SearchHit(LightweightDocument document, String chunk, double score) { }

    private record RankedHit(EsDocument source, double score) { }
}
*/

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParser;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class LightweightDocumentService {
    private static final int MAX_TEXT = 2_000_000;
    private final LightweightStorageProperties properties;
    private final StoredDocumentRepository documents;
    private final StoredChunkRepository chunks;
    private final LightweightRagIndex index;
    private final IndexWorker worker;

    LightweightDocumentService(LightweightStorageProperties properties, StoredDocumentRepository documents,
                               StoredChunkRepository chunks, LightweightRagIndex index, IndexWorker worker) {
        this.properties = properties;
        this.documents = documents;
        this.chunks = chunks;
        this.index = index;
        this.worker = worker;
    }

    LightweightDocument accept(MultipartFile file, String owner, boolean shared) throws IOException {
        if (file.isEmpty()) throw error(HttpStatus.BAD_REQUEST, "文件不能为空");
        if (file.getSize() > properties.getMaxFileSizeBytes()) throw error(HttpStatus.PAYLOAD_TOO_LARGE, "文件超过大小限制");
        String name = Objects.requireNonNullElse(file.getOriginalFilename(), "document.txt").replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "");
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (name.length() > 255 || !properties.getAllowedExtensions().contains(extension) ||
                !Set.of("pdf", "doc", "docx", "md", "txt").contains(extension)) {
            throw error(HttpStatus.BAD_REQUEST, "仅支持 PDF、Word、Markdown、TXT 文档");
        }
        byte[] bytes = file.getBytes();
        String md5;
        try {
            md5 = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        var previous = documents.findByOwnerIdAndMd5(owner, md5);
        if (previous.isPresent()) return view(previous.get());

        StoredDocument document = new StoredDocument();
        document.id = UUID.randomUUID().toString();
        document.ownerId = owner;
        document.md5 = md5;
        document.fileName = name;
        document.extension = extension;
        document.shared = shared;
        document.fileSize = file.getSize();
        document.createdAt = Instant.now();
        document.status = "PROCESSING";
        Path path = original(document);
        Files.createDirectories(path.getParent());
        try {
            Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
            documents.saveAndFlush(document);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(path);
            if (e instanceof DataIntegrityViolationException) {
                var duplicate = documents.findByOwnerIdAndMd5(owner, md5);
                if (duplicate.isPresent()) return view(duplicate.get());
            }
            throw e;
        }
        try {
            worker.process(document.id, path);
        } catch (org.springframework.core.task.TaskRejectedException e) {
            document.status = "FAILED";
            documents.save(document);
        }
        return view(document);
    }

    List<LightweightDocument> list(String requester) {
        return documents.visibleTo(requester).stream().map(LightweightDocumentService::view).toList();
    }

    StoredDocument visible(String id, String requester) {
        return documents.findById(id).filter(d -> d.shared || d.ownerId.equals(requester))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "文档不存在"));
    }

    Path original(StoredDocument document) {
        return properties.getRoot().toAbsolutePath().normalize().resolve(document.id + "." + document.extension);
    }

    String preview(StoredDocument document) {
        return String.join("\n\n", chunks.findByDocumentIdOrderByPosition(document.id)
                .stream().map(c -> c.content).toList());
    }

    void delete(String id, String requester) throws IOException {
        StoredDocument document = visible(id, requester);
        if (!document.ownerId.equals(requester)) throw error(HttpStatus.FORBIDDEN, "仅上传者可删除文档");
        if ("PROCESSING".equals(document.status)) throw error(HttpStatus.CONFLICT, "文档仍在处理中");
        index.delete(document.id);
        chunks.deleteByDocumentId(document.id);
        Files.deleteIfExists(original(document));
        documents.delete(document);
    }

    static LightweightDocument view(StoredDocument d) {
        return new LightweightDocument(d.id, d.fileName, d.md5, d.ownerId, d.shared,
                d.fileSize, d.status, d.createdAt, d.chunkCount);
    }

    static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    @jakarta.annotation.PostConstruct
    void markInterruptedUploads() {
        // 单机进程重启后旧线程已不存在，仅标记失败，不启动自动恢复任务。
        documents.failInterrupted();
    }

    @Service
    public static class IndexWorker {
        private static final Logger LOG = LoggerFactory.getLogger(IndexWorker.class);
        private final StoredDocumentRepository documents;
        private final StoredChunkRepository chunks;
        private final LightweightRagIndex index;

        IndexWorker(StoredDocumentRepository documents, StoredChunkRepository chunks, LightweightRagIndex index) {
            this.documents = documents;
            this.chunks = chunks;
            this.index = index;
        }

        @Async("documentParseExecutor")
        public void process(String id, Path path) {
            StoredDocument document = documents.findById(id).orElseThrow();
            try {
                List<StoredChunk> extracted = new ArrayList<>();
                if ("pdf".equals(document.extension)) {
                    try (PDDocument pdf = PDDocument.load(path.toFile())) {
                        if (pdf.getNumberOfPages() > 1000) throw new IOException("PDF 超过页数上限");
                        PDFTextStripper stripper = new PDFTextStripper();
                        int total = 0;
                        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                            stripper.setStartPage(page);
                            stripper.setEndPage(page);
                            String pageText = stripper.getText(pdf);
                            total += pageText.length();
                            if (total > MAX_TEXT) throw new IOException("文档文本过长");
                            split(id, page, pageText, extracted);
                        }
                    }
                } else if (Set.of("txt", "md").contains(document.extension)) {
                    String text = Files.readString(path, StandardCharsets.UTF_8);
                    if (text.length() > MAX_TEXT) throw new IOException("文档文本过长");
                    split(id, null, text, extracted);
                } else {
                    try (var input = Files.newInputStream(path)) {
                        var handler = new BodyContentHandler(MAX_TEXT);
                        var parser = "doc".equals(document.extension) ? new OfficeParser() : new OOXMLParser();
                        var context = new ParseContext();
                        context.set(org.apache.tika.extractor.EmbeddedDocumentExtractor.class,
                                new org.apache.tika.extractor.EmbeddedDocumentExtractor() {
                                    @Override public boolean shouldParseEmbedded(Metadata metadata) { return false; }
                                    @Override public void parseEmbedded(java.io.InputStream stream,
                                            org.xml.sax.ContentHandler content, Metadata metadata, boolean outputHtml) {}
                                });
                        parser.parse(input, handler, new Metadata(), context);
                        split(id, null, handler.toString(), extracted);
                    }
                }
                if (extracted.isEmpty()) throw new IOException("没有可提取的文本；不支持扫描件 OCR");
                chunks.saveAllAndFlush(extracted);
                index.add(extracted);
                document.chunkCount = extracted.size();
                document.status = "READY";
                documents.save(document);
            } catch (Exception e) {
                LOG.warn("文档索引失败: {}, {}", id, e.getClass().getSimpleName());
                document.status = "FAILED";
                documents.save(document);
            }
        }

        static void split(String id, Integer page, String raw, List<StoredChunk> output) {
            String text = raw.replace("\r\n", "\n").strip();
            for (int start = 0; start < text.length();) {
                int end = Math.min(text.length(), start + 800);
                if (end < text.length()) {
                    for (int i = end - 1; i >= start + 500; i--) {
                        if ("\n。！？!?；;".indexOf(text.charAt(i)) >= 0) {
                            end = i + 1;
                            break;
                        }
                    }
                    if (Character.isLowSurrogate(text.charAt(end))) end--;
                }
                String part = text.substring(start, end).strip();
                if (!part.isEmpty()) output.add(new StoredChunk(id, output.size(), page, part));
                if (end == text.length()) break;
                start = end - 120;
                if (Character.isLowSurrogate(text.charAt(start))) start--;
            }
        }
    }
}
