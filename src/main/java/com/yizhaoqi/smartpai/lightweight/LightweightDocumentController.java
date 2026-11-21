package com.yizhaoqi.smartpai.lightweight;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/lightweight/documents")
public class LightweightDocumentController {
    private final LightweightDocumentService documentService;
    public LightweightDocumentController(LightweightDocumentService documentService) { this.documentService = documentService; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LightweightDocument> upload(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "public", defaultValue = "false") boolean isPublic,
            @RequestAttribute(value = "userId", required = false) String userId) throws Exception {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(documentService.accept(file, userId, isPublic));
    }

    @GetMapping
    public List<LightweightDocument> list(@RequestAttribute(value = "userId", required = false) String userId) {
        return documentService.list(userId);
    }
}
