package com.zuoye.mark.config;

import com.zuoye.mark.service.PlatformLogService;
import com.zuoye.mark.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class OperationLogFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_ATTRIBUTE = "mark.traceId";
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(?i)(\"(?:password|passwordHash|authorization|accessToken)\"\\s*:\\s*)\"[^\"]*\"");
    private static final Pattern FORM_SECRET_PATTERN = Pattern.compile(
            "(?i)(password=)[^&\\s]*");

    private final SessionService sessionService;
    private final PlatformLogService logService;

    public OperationLogFilter(
            SessionService sessionService,
            PlatformLogService logService) {
        this.sessionService = sessionService;
        this.logService = logService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/")
                || path.equals("/api/health");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long started = System.nanoTime();
        String traceId = Optional.ofNullable(request.getHeader("X-Trace-Id"))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        response.setHeader("X-Trace-Id", traceId);

        ContentCachingRequestWrapper requestWrapper =
                new ContentCachingRequestWrapper(request, 32_000);
        ContentCachingResponseWrapper responseWrapper =
                new ContentCachingResponseWrapper(response);
        Exception failure = null;
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } catch (Exception error) {
            failure = error;
            throw error;
        } finally {
            SessionService.PlatformUser actor = request.getAttribute(
                    SessionService.REQUEST_USER_ATTRIBUTE) instanceof SessionService.PlatformUser user
                            ? user
                            : sessionService.current(requestWrapper).orElse(null);
            String path = requestWrapper.getRequestURI();
            String targetType = targetType(path);
            Long targetId = targetId(path);
            String requestBody = requestBody(requestWrapper);
            String responseBody = responseBody(responseWrapper);
            String summary = (failure == null ? "" : failure.getMessage() + " · ")
                    + requestWrapper.getMethod()
                    + " "
                    + path;
            logService.operation(new PlatformLogService.OperationEntry(
                    traceId,
                    actor == null ? null : actor.id(),
                    actor == null ? "SYSTEM" : actor.role(),
                    actor == null ? null : actor.displayName(),
                    requestWrapper.getMethod(),
                    path,
                    action(requestWrapper.getMethod(), path),
                    targetType,
                    targetId,
                    summary,
                    requestBody,
                    responseBody,
                    responseWrapper.getStatus(),
                    failure == null && responseWrapper.getStatus() < 400,
                    (System.nanoTime() - started) / 1_000_000,
                    clientIp(requestWrapper),
                    requestWrapper.getHeader("User-Agent")));
            responseWrapper.copyBodyToResponse();
        }
    }

    private String requestBody(ContentCachingRequestWrapper request) {
        if (request.getContentType() != null
                && request.getContentType().toLowerCase().contains("multipart/")) {
            return "{\"multipart\":true}";
        }
        return sanitize(new String(
                request.getContentAsByteArray(),
                StandardCharsets.UTF_8));
    }

    private String responseBody(ContentCachingResponseWrapper response) {
        if (response.getContentSize() == 0) return null;
        String contentType = response.getContentType();
        if (contentType == null
                || (!contentType.contains("json")
                    && !contentType.contains("text"))) {
            return null;
        }
        return sanitize(new String(
                response.getContentAsByteArray(),
                StandardCharsets.UTF_8));
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) return null;
        String sanitized = PASSWORD_PATTERN.matcher(value).replaceAll("$1\"***\"");
        return FORM_SECRET_PATTERN.matcher(sanitized).replaceAll("$1***");
    }

    private String action(String method, String path) {
        return method.toUpperCase() + " " + path;
    }

    private String targetType(String path) {
        String[] segments = path.split("/");
        if (segments.length > 2) return segments[2];
        if (segments.length > 1) return segments[1];
        return null;
    }

    private Long targetId(String path) {
        for (String segment : path.split("/")) {
            if (segment.matches("\\d+")) {
                try {
                    return Long.parseLong(segment);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
