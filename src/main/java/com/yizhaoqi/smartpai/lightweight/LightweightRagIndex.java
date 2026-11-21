package com.yizhaoqi.smartpai.lightweight;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.yizhaoqi.smartpai.service.search.RrfFusion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

@Service
class LightweightRagIndex {
    private final ElasticsearchClient es;
    private final LightweightModels models;
    private final StoredDocumentRepository documents;
    private final StoredChunkRepository chunks;
    @Value("${rag.index}") private String name;
    @Value("${rag.rerank.enabled}") private boolean rerank;
    @Value("${rag.rerank.threshold}") private double threshold;
    @Value("${rag.rrf-threshold}") private double rrfThreshold;
    @Value("${rag.parent-context-enabled}") private boolean expand;

    LightweightRagIndex(ElasticsearchClient es, LightweightModels models, StoredDocumentRepository documents,
                        StoredChunkRepository chunks) {
        this.es = es;
        this.models = models;
        this.documents = documents;
        this.chunks = chunks;
    }

    private synchronized void ensureIndex() throws IOException {
        if (es.indices().exists(r -> r.index(name)).value()) return;
        int dims = models.dimension();
        if (dims < 1 || dims > 2048) throw new IllegalStateException("ES 8.10 的向量维度须在 1–2048");
        String mapping = """
                {"mappings":{"dynamic":"strict","properties":{
                  "documentId":{"type":"keyword"},
                  "text":{"type":"text"},
                  "vector":{"type":"dense_vector","index":true,"similarity":"cosine","dims":%d}
                }}}
                """.formatted(dims);
        es.indices().create(r -> r.index(name).withJson(new StringReader(mapping)));
    }

    void add(List<StoredChunk> records) throws IOException {
        ensureIndex();
        for (int start = 0; start < records.size(); start += 10) {
            var batch = records.subList(start, Math.min(records.size(), start + 10));
            var vectors = models.embed(batch.stream().map(c -> c.content).toList());
            var request = new BulkRequest.Builder().refresh(Refresh.WaitFor);
            for (int i = 0; i < batch.size(); i++) {
                StoredChunk chunk = batch.get(i);
                var payload = Map.of("documentId", chunk.documentId, "text", chunk.content, "vector", vectors.get(i));
                request.operations(o -> o.index(x -> x.index(name).id(chunk.id).document(payload)));
            }
            if (es.bulk(request.build()).errors()) throw new IOException("ES 分块写入失败");
        }
    }

    void delete(String documentId) throws IOException {
        if (!es.indices().exists(r -> r.index(name)).value()) return;
        var result = es.deleteByQuery(r -> r.index(name).refresh(true)
                .query(q -> q.term(t -> t.field("documentId").value(documentId))));
        if (!result.failures().isEmpty()) throw new IOException("ES 分块删除失败");
    }

    List<Evidence> search(String question, String owner) throws IOException {
        var allowed = documents.readyIds(owner);
        if (allowed.isEmpty()) return List.of();
        var values = allowed.stream().map(FieldValue::of).toList();
        Query permission = Query.of(q -> q.terms(t -> t.field("documentId").terms(v -> v.value(values))));
        var queryVector = models.embed(List.of(question)).get(0);
        var bm25 = es.search(r -> r.index(name).size(30).source(s -> s.fetch(false))
                .query(q -> q.bool(b -> b.filter(permission)
                        .must(m -> m.match(x -> x.field("text").query(question))))), Void.class);
        var knn = es.search(r -> r.index(name).size(30).source(s -> s.fetch(false))
                .knn(k -> k.field("vector").queryVector(queryVector).k(30).numCandidates(100).filter(permission)),
                Void.class);
        var fused = RrfFusion.fuse(60, Hit<Void>::id, bm25.hits().hits(), knn.hits().hits())
                .stream().limit(30).toList();
        if (fused.isEmpty()) return List.of();

        // 回源 MySQL：只接受就绪且可访问的当前记录，不把索引里的文本当作权限依据。
        var permitted = new HashSet<>(allowed);
        var byId = chunks.findAllById(fused.stream().map(RrfFusion.Fused::key).toList()).stream()
                .filter(c -> permitted.contains(c.documentId)).collect(Collectors.toMap(c -> c.id, c -> c));
        var candidates = fused.stream().filter(x -> byId.containsKey(x.key())).toList();
        if (candidates.isEmpty()) return List.of();
        var selected = new ArrayList<Scored>();
        if (rerank) {
            var texts = candidates.stream().map(c -> byId.get(c.key()).content).toList();
            models.rerank(question, texts).stream().filter(x -> x.score() >= threshold).limit(5)
                    .forEach(x -> selected.add(new Scored(byId.get(candidates.get(x.position()).key()), x.score())));
        } else {
            candidates.stream().filter(x -> x.score() >= rrfThreshold).limit(5)
                    .forEach(x -> selected.add(new Scored(byId.get(x.key()), x.score())));
        }
        var names = documents.findAllById(selected.stream().map(x -> x.chunk.documentId).distinct().toList())
                .stream().filter(d -> d.shared || owner.equals(d.ownerId))
                .collect(Collectors.toMap(d -> d.id, d -> d));
        List<Evidence> evidence = new ArrayList<>();
        for (Scored hit : selected) {
            StoredChunk chunk = hit.chunk;
            StoredDocument document = names.get(chunk.documentId);
            if (document == null || !"READY".equals(document.status)) continue;
            String context = chunk.content;
            if (expand) {
                var nearby = chunks.findByDocumentIdAndPositionBetweenOrderByPosition(
                        chunk.documentId, chunk.position - 1, chunk.position + 1);
                context = nearby.stream().map(c -> c.content).collect(Collectors.joining("\n"));
            }
            evidence.add(new Evidence(document.id, document.fileName, chunk.pageNumber, chunk.position, chunk.content,
                    context, hit.score));
        }
        return evidence;
    }

    private record Scored(StoredChunk chunk, double score) {}
    record Evidence(String documentId, String fileName, Integer pageNumber, int position,
                    String excerpt, String context, double score) {}
}
