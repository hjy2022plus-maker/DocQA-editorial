package com.yizhaoqi.smartpai.lightweight;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/lightweight/documents")
public class LightweightDocumentController {
    private final LightweightDocumentService documentService;
    public LightweightDocumentController(LightweightDocumentService documentService) { this.documentService = documentService; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "public", defaultValue = "false") boolean isPublic,
            Principal principal) throws java.io.IOException {
        LightweightDocument document = documentService.accept(file, principal.getName(), isPublic);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "code", 200,
                "message", "文档已接收，正在异步解析",
                "data", document
        ));
    }

    @GetMapping
    public Map<String, Object> list(Principal principal) {
        List<LightweightDocument> documents = documentService.list(principal.getName());
        return Map.of("code", 200, "message", "ok", "data", documents);
    }

    @DeleteMapping("/{id}")
    Map<String, Object> delete(@PathVariable String id, Principal principal) throws java.io.IOException {
        documentService.delete(id, principal.getName());
        return DocQaAccounts.ok(true);
    }
}
