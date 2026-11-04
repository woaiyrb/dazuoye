package com.zuoye.mark.web;

import com.zuoye.mark.config.OperationLogFilter;
import com.zuoye.mark.service.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<Map<String, Object>> handlePlatform(
            PlatformException error,
            HttpServletRequest request) {
        return response(
                error.status(),
                error.code(),
                error.getMessage(),
                error.data(),
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException error,
            HttpServletRequest request) {
        String message = error.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(field -> field.getField() + " " + field.getDefaultMessage())
                .orElse("请求参数不合法。");
        return response(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_ERROR",
                message,
                null,
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(
            Exception error,
            HttpServletRequest request) {
        LOGGER.error(
                "Unhandled API error traceId={} path={}",
                traceId(request),
                request.getRequestURI(),
                error);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                error.getMessage() == null ? "服务器内部错误。" : error.getMessage(),
                null,
                request);
    }

    private ResponseEntity<Map<String, Object>> response(
            HttpStatus status,
            String code,
            String message,
            Object data,
            HttpServletRequest request) {
        String traceId = traceId(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", data);
        body.put("traceId", traceId);
        body.put("timestamp", OffsetDateTime.now().toString());
        return ResponseEntity.status(status).body(body);
    }

    private String traceId(HttpServletRequest request) {
        Object current = request.getAttribute(OperationLogFilter.TRACE_ID_ATTRIBUTE);
        if (current != null) return current.toString();
        String generated = UUID.randomUUID().toString().replace("-", "");
        request.setAttribute(OperationLogFilter.TRACE_ID_ATTRIBUTE, generated);
        return generated;
    }
}
