package com.yizhaoqi.smartpai.lightweight;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice
class DocQaErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, Object>> problem(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("code", e.getStatusCode().value(),
                "message", java.util.Objects.requireNonNullElse(e.getReason(), "请求未能完成")));
    }
}
