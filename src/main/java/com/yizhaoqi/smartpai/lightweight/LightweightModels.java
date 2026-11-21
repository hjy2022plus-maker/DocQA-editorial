package com.yizhaoqi.smartpai.lightweight;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;

@Component
class LightweightModels {
    private final WebClient http;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    @Value("${rag.embedding.url}") private String embeddingUrl;
    @Value("${rag.embedding.key}") private String embeddingKey;
    @Value("${rag.embedding.model}") private String embeddingModel;
    @Value("${rag.embedding.dimension}") private int dimension;
    @Value("${rag.embedding.send-dimension}") private boolean sendDimension;
    @Value("${rag.llm.url}") private String chatUrl;
    @Value("${rag.llm.key}") private String chatKey;
    @Value("${rag.llm.model}") private String chatModel;
    @Value("${rag.rerank.url}") private String rerankUrl;
    @Value("${rag.rerank.key}") private String rerankKey;
    @Value("${rag.rerank.model}") private String rerankModel;

    LightweightModels(WebClient.Builder builder, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.http = builder.build();
        this.json = json;
    }

    int dimension() { return dimension; }

    List<List<Float>> embed(List<String> texts) {
        Map<String, Object> body = new HashMap<>(Map.of("model", embeddingModel, "input", texts));
        if (sendDimension) body.put("dimensions", dimension);
        var response = request(endpoint(embeddingUrl, "/embeddings"), embeddingKey, body);
        var vectors = new HashMap<Integer, List<Float>>();
        for (JsonNode row : response.path("data")) {
            int index = row.path("index").asInt(-1);
            List<Float> vector = new ArrayList<>();
            double squaredNorm = 0;
            for (JsonNode item : row.path("embedding")) {
                float value = (float) item.asDouble(Double.NaN);
                if (!Float.isFinite(value)) throw new IllegalStateException("Embedding 向量包含非数字");
                vector.add(value);
                squaredNorm += (double) value * value;
            }
            if (vector.size() != dimension || squaredNorm == 0
                    || index < 0 || index >= texts.size() || vectors.put(index, vector) != null) {
                throw new IllegalStateException("Embedding 返回的批次或向量维度无效");
            }
        }
        if (vectors.size() != texts.size()) throw new IllegalStateException("Embedding 返回的条数不符");
        return java.util.stream.IntStream.range(0, texts.size()).mapToObj(vectors::get).toList();
    }

    List<Rank> rerank(String question, List<String> texts) {
        var data = request(rerankUrl, rerankKey, Map.of(
                "model", rerankModel, "input", Map.of("query", question, "documents", texts),
                "parameters", Map.of("top_n", texts.size(), "return_documents", false)));
        JsonNode results = data.path("output").path("results");
        if (!results.isArray()) throw new IllegalStateException("重排序接口未返回结果");
        List<Rank> ranks = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (JsonNode result : results) {
            int position = result.path("index").asInt(-1);
            double score = result.path("relevance_score").asDouble(Double.NaN);
            if (position < 0 || position >= texts.size() || !Double.isFinite(score) || !seen.add(position)) {
                throw new IllegalStateException("重排序结果无效");
            }
            ranks.add(new Rank(position, score));
        }
        ranks.sort(Comparator.comparingDouble(Rank::score).reversed());
        return ranks;
    }

    Flux<String> answer(List<Map<String, String>> messages) {
        return post(endpoint(chatUrl, "/chat/completions"), chatKey,
                Map.of("model", chatModel, "messages", messages, "stream", true, "max_tokens", 1800))
                .accept(MediaType.TEXT_EVENT_STREAM).retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .<String>handle((event, sink) -> {
                    String data = event.data();
                    if (data == null || data.isBlank()) return;
                    if ("[DONE]".equals(data.strip())) {
                        sink.complete();
                        return;
                    }
                    try {
                        JsonNode frame = json.readTree(data);
                        if (frame.hasNonNull("error")) throw new IllegalStateException("模型返回错误");
                        String token = frame.path("choices").path(0).path("delta").path("content").asText("");
                        if (!token.isEmpty()) sink.next(token);
                    } catch (Exception e) {
                        sink.error(e);
                    }
                }).timeout(Duration.ofSeconds(60));
    }

    private JsonNode request(String url, String key, Object body) {
        Mono<JsonNode> call = post(url, key, body).retrieve().bodyToMono(JsonNode.class);
        return call.timeout(Duration.ofSeconds(15))
                .retryWhen(Retry.fixedDelay(1, Duration.ofMillis(350)).filter(error ->
                        error instanceof TimeoutException || error instanceof WebClientRequestException
                                || error instanceof WebClientResponseException response
                                && response.getStatusCode().is5xxServerError()))
                .blockOptional().orElseThrow(() -> new IllegalStateException("模型接口返回空响应"));
    }

    private WebClient.RequestHeadersSpec<?> post(String url, String key, Object body) {
        if (url.isBlank()) throw new IllegalStateException("未配置模型接口 URL");
        return http.post().uri(url).headers(headers -> {
            if (!key.isBlank()) headers.setBearerAuth(key);
        }).contentType(MediaType.APPLICATION_JSON).bodyValue(body);
    }

    private String endpoint(String base, String path) {
        if (base.isBlank()) throw new IllegalStateException("未配置模型接口 URL");
        return base.replaceAll("/+$", "") + path;
    }

    record Rank(int position, double score) {}
}
