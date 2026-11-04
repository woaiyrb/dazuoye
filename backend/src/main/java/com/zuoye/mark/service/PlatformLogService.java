package com.zuoye.mark.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PlatformLogService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PlatformLogService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void operation(OperationEntry entry) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO operation_log
                      (traceId, actorId, actorRole, actorName, method, path,
                       action, targetType, targetId, summary, requestJson,
                       responseJson, statusCode, success, durationMs,
                       ipAddress, userAgent)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    entry.traceId(),
                    entry.actorId(),
                    entry.actorRole(),
                    entry.actorName(),
                    entry.method(),
                    truncate(entry.path(), 500),
                    truncate(entry.action(), 128),
                    truncate(entry.targetType(), 80),
                    entry.targetId(),
                    truncate(entry.summary(), 500),
                    truncate(entry.requestJson(), 32_000),
                    truncate(entry.responseJson(), 32_000),
                    entry.statusCode(),
                    entry.success(),
                    entry.durationMs(),
                    truncate(entry.ipAddress(), 64),
                    truncate(entry.userAgent(), 500));
        } catch (Exception error) {
            // Audit failures must not change the user-facing business response.
        }
    }

    public void backendEvent(
            String eventType,
            String detailJson) {
        try {
            JsonNode detail = detailJson == null
                    ? null
                    : objectMapper.readTree(detailJson);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventType", eventType);
            payload.put("serviceName", "mark-api-java");
            payload.put("instanceId", instanceId());
            payload.put("version", "0.2.0");
            payload.put("detailJson", detail);
            jdbcTemplate.update(
                    """
                    INSERT INTO backend_event_log
                      (eventType, serviceName, instanceId, version, detailJson)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    eventType,
                    "mark-api-java",
                    instanceId(),
                    "0.2.0",
                    detail == null ? null : objectMapper.writeValueAsString(detail));
        } catch (Exception error) {
            // Logging failures remain isolated from application startup/shutdown.
        }
    }

    public long logCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operation_log",
                Long.class);
        return count == null ? 0 : count;
    }

    private String instanceId() {
        return System.getProperty("mark.instance.id", "local-java-1");
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record OperationEntry(
            String traceId,
            Integer actorId,
            String actorRole,
            String actorName,
            String method,
            String path,
            String action,
            String targetType,
            Long targetId,
            String summary,
            String requestJson,
            String responseJson,
            int statusCode,
            boolean success,
            long durationMs,
            String ipAddress,
            String userAgent) {}
}
