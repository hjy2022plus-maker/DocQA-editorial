package com.yizhaoqi.smartpai.lightweight;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/lightweight/qa")
public class LightweightQaController {
    private final LightweightDocumentService documentService;
    public LightweightQaController(LightweightDocumentService documentService) { this.documentService = documentService; }

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream(@RequestParam("question") String question,
            @RequestAttribute(value = "userId", required = false) String userId) {
        SseEmitter emitter = new SseEmitter(60_000L);
        try {
            List<LightweightDocumentService.SearchHit> hits = documentService.search(question, userId);
            if (hits.isEmpty()) {
                emitter.send(SseEmitter.event().name("refusal").data("当前知识库没有足够证据，暂不回答。"));
            } else {
                emitter.send(SseEmitter.event().name("answer").data(hits.get(0).chunk()));
                for (int i = 0; i < hits.size(); i++) {
                    LightweightDocumentService.SearchHit hit = hits.get(i);
                    emitter.send(SseEmitter.event().name("citation").data(new Citation(i + 1, hit.document().id(), hit.document().fileName(), hit.chunk())));
                }
            }
            emitter.send(SseEmitter.event().name("done").data("true"));
            emitter.complete();
        } catch (IOException ex) { emitter.completeWithError(ex); }
        return emitter;
    }

    private record Citation(int number, String documentId, String fileName, String excerpt) { }
}
