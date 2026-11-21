package com.yizhaoqi.smartpai.lightweight;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/documents")
class DocQaPreview {
    private final LightweightDocumentService documents;
    DocQaPreview(LightweightDocumentService documents) { this.documents = documents; }

    @GetMapping("/preview")
    Map<String, Object> preview(@RequestParam String fileMd5, Principal principal) {
        var doc = documents.visible(fileMd5, principal.getName());
        var payload = new LinkedHashMap<String, Object>(info(doc));
        if ("pdf".equals(doc.extension)) {
            payload.put("previewType", "pdf");
            payload.put("previewUrl", originalUrl(doc));
        } else {
            payload.put("previewType", "text");
            payload.put("content", documents.preview(doc));
        }
        return DocQaAccounts.ok(payload);
    }

    @GetMapping("/download-by-md5")
    Map<String, Object> download(@RequestParam String fileMd5, Principal principal) {
        var doc = documents.visible(fileMd5, principal.getName());
        var payload = new LinkedHashMap<String, Object>(info(doc));
        payload.put("downloadUrl", originalUrl(doc));
        return DocQaAccounts.ok(payload);
    }

    @GetMapping("/original/{id}")
    ResponseEntity<FileSystemResource> original(@PathVariable String id, Principal principal) {
        var doc = documents.visible(id, principal.getName());
        var resource = new FileSystemResource(documents.original(doc));
        if (!resource.isReadable()) throw LightweightDocumentService.error(HttpStatus.NOT_FOUND, "原文件不存在");
        var disposition = "pdf".equals(doc.extension) ? ContentDisposition.inline() : ContentDisposition.attachment();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.filename(doc.fileName, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType("pdf".equals(doc.extension) ? MediaType.APPLICATION_PDF : MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    private Map<String, Object> info(StoredDocument doc) {
        return Map.of("fileName", doc.fileName, "fileSize", doc.fileSize, "fileMd5", doc.id);
    }
    private String originalUrl(StoredDocument doc) { return "/api/v1/documents/original/" + doc.id; }
}
