package com.zuoye.mark.web;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LegacyProxyController.class)
public class ProxyExceptionHandler {

    @ExceptionHandler({IOException.class, InterruptedException.class})
    public ResponseEntity<Map<String, Object>> legacyUnavailable(Exception error) {
        if (error instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "legacy_api_unavailable");
        body.put("message", "旧版教学服务暂时不可用，请先运行 start-dev.bat。");
        body.put("checkedAt", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
