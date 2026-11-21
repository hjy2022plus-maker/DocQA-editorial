package com.yizhaoqi.smartpai.lightweight;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Local-disk upload, Tika extraction, and small sliding-window chunker. */
@Service
public class LightweightDocumentService {
    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 120;
    private final LightweightStorageProperties properties;
    private final TaskExecutor parseExecutor;
    private final Tika tika = new Tika();
    private final Map<String, LightweightDocument> documents = new ConcurrentHashMap<>();
    private final Map<String, Path> paths = new ConcurrentHashMap<>();

    public LightweightDocumentService(LightweightStorageProperties properties,
                                      @Qualifier("documentParseExecutor") TaskExecutor parseExecutor) {
        this.properties = properties;
        this.parseExecutor = parseExecutor;
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
        LightweightDocument existing = documents.get(md5);
        if (existing != null) return existing;
        LightweightDocument document = new LightweightDocument(md5, originalName, md5,
                Objects.requireNonNullElse(ownerId, "anonymous"), isPublic, bytes.length,
                "PROCESSING", Instant.now(), List.of());
        documents.put(md5, document);
        paths.put(md5, target);
        // Submit through the named executor so the @Async parser is not bypassed
        // by a same-bean call (the upload request returns immediately).
        parseExecutor.execute(() -> processAsync(md5, target));
        return document;
    }

    public List<LightweightDocument> list(String requester) {
        String user = Objects.requireNonNullElse(requester, "anonymous");
        return documents.values().stream().filter(doc -> doc.isPublic() || doc.ownerId().equals(user))
                .sorted(Comparator.comparing(LightweightDocument::createdAt).reversed()).toList();
    }

    public List<SearchHit> search(String question, String requester) {
        String[] terms = question.toLowerCase(Locale.ROOT).split("[\\s,，。！？、；：]+", -1);
        return list(requester).stream().filter(doc -> "READY".equals(doc.status()))
                .flatMap(doc -> doc.chunks().stream().map(chunk -> new SearchHit(doc, chunk, score(chunk, terms))))
                .filter(hit -> hit.score() > 0).sorted(Comparator.comparingDouble(SearchHit::score).reversed()).limit(5).toList();
    }

    @Async("documentParseExecutor")
    public void processAsync(String id, Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            documents.computeIfPresent(id, (key, doc) -> doc.withStatus("READY", split(tika.parseToString(input))));
        } catch (IOException | TikaException ex) {
            documents.computeIfPresent(id, (key, doc) -> doc.withStatus("FAILED", List.of()));
        }
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
}
