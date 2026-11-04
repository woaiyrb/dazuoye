package com.zuoye.mark.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResearchPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ResearchPlatformService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> knowledgePoints(
            boolean includeDisabled) {
        return jdbcTemplate.query(
                """
                SELECT *
                FROM kp_node
                WHERE (? = TRUE OR status = 'ACTIVE')
                ORDER BY kpLevel, code
                """,
                (row, index) -> knowledgePointMap(row),
                includeDisabled);
    }

    @Transactional
    public Map<String, Object> createKnowledgePoint(
            int researchId,
            JsonNode body) {
        String code = requiredText(body, "code", "知识点编码不能为空。");
        String name = requiredText(body, "name", "知识点名称不能为空。");
        int level = integer(body, "kpLevel", 1);
        Integer parentId = body.has("parentId") && !body.get("parentId").isNull()
                ? body.get("parentId").asInt()
                : null;
        jdbcTemplate.update(
                """
                INSERT INTO kp_node
                  (parentId, code, name, kpLevel, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """,
                parentId,
                code,
                name,
                level);
        Integer id = jdbcTemplate.queryForObject(
                "SELECT id FROM kp_node WHERE code = ?",
                Integer.class,
                code);
        return knowledgePointById(id == null ? 0 : id);
    }

    @Transactional
    public Map<String, Object> updateKnowledgePoint(
            int researchId,
            int knowledgePointId,
            JsonNode body) {
        String name = requiredText(body, "name", "知识点名称不能为空。");
        jdbcTemplate.update(
                "UPDATE kp_node SET name = ? WHERE id = ?",
                name,
                knowledgePointId);
        return knowledgePointById(knowledgePointId);
    }

    @Transactional
    public Map<String, Object> disableKnowledgePoint(
            int researchId,
            int knowledgePointId,
            JsonNode body) {
        String impactNote = requiredText(
                body,
                "impactNote",
                "停用知识点必须填写影响说明。");
        jdbcTemplate.update(
                """
                UPDATE kp_node
                SET status = 'DISABLED', disabledImpactNote = ?
                WHERE id = ?
                """,
                impactNote,
                knowledgePointId);
        return knowledgePointById(knowledgePointId);
    }

    public Map<String, Object> templates(
            String status,
            int page,
            int pageSize) {
        String safeStatus = status == null ? "" : status.trim();
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                SELECT t.*, u.displayName AS createdByName,
                       r.displayName AS reviewedByName
                FROM comment_template t
                JOIN app_user u ON u.id = t.createdBy
                LEFT JOIN app_user r ON r.id = t.reviewedBy
                WHERE (? = '' OR t.status = ?)
                ORDER BY t.updatedAt DESC
                """,
                (row, index) -> templateMap(row),
                safeStatus,
                safeStatus);
        return page(rows, page, pageSize);
    }

    @Transactional
    public Map<String, Object> reviewTemplate(
            int researchId,
            int templateId,
            JsonNode body) {
        String decision = requiredText(body, "decision", "请选择审核结果。");
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_DECISION",
                    "审核结果必须为 APPROVED 或 REJECTED。");
        }
        jdbcTemplate.update(
                """
                UPDATE comment_template
                SET status = ?, reviewedBy = ?, reviewNote = ?,
                    reviewedAt = CURRENT_TIMESTAMP(3)
                WHERE id = ?
                """,
                decision,
                researchId,
                text(body, "reviewNote", null),
                templateId);
        return templateById(templateId);
    }

    public Map<String, Object> operationLogs(
            String action,
            String targetType,
            Integer actorId,
            int page,
            int pageSize) {
        String safeAction = action == null ? "" : action.trim();
        String safeTarget = targetType == null ? "" : targetType.trim();
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                SELECT id, traceId, actorId, actorRole, actorName, method,
                       path, action, targetType, targetId, summary,
                       statusCode, success, durationMs, ipAddress, createdAt
                FROM operation_log
                WHERE (? = '' OR action LIKE ?)
                  AND (? = '' OR targetType = ?)
                  AND (? IS NULL OR actorId = ?)
                ORDER BY createdAt DESC
                """,
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.getLong("id"));
                    item.put("actorId", row.getObject("actorId"));
                    item.put("actorRole", row.getString("actorRole"));
                    item.put("actorName", row.getString("actorName"));
                    item.put("action", row.getString("action"));
                    item.put("targetType", row.getString("targetType"));
                    item.put("targetId", row.getObject("targetId"));
                    item.put("summary", row.getString("summary"));
                    item.put("detail", Map.of(
                            "traceId", row.getString("traceId"),
                            "method", row.getString("method"),
                            "path", row.getString("path"),
                            "statusCode", row.getInt("statusCode"),
                            "success", row.getBoolean("success"),
                            "durationMs", row.getLong("durationMs")));
                    item.put("createdAt", instant(row.getTimestamp("createdAt")));
                    item.put("ip", row.getString("ipAddress"));
                    return item;
                },
                safeAction,
                "%" + safeAction + "%",
                safeTarget,
                safeTarget,
                actorId,
                actorId);
        return page(rows, page, pageSize);
    }

    private Map<String, Object> knowledgePointById(int id) {
        return jdbcTemplate.query(
                "SELECT * FROM kp_node WHERE id = ?",
                (row, index) -> knowledgePointMap(row),
                id).stream().findFirst().orElseThrow(
                        () -> new PlatformException(
                                HttpStatus.NOT_FOUND,
                                "KNOWLEDGE_POINT_NOT_FOUND",
                                "知识点不存在。"));
    }

    private Map<String, Object> knowledgePointMap(java.sql.ResultSet row)
            throws java.sql.SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getInt("id"));
        item.put("parentId", row.getObject("parentId"));
        item.put("code", row.getString("code"));
        item.put("name", row.getString("name"));
        item.put("kpLevel", row.getInt("kpLevel"));
        item.put("status", row.getString("status"));
        item.put("disabledImpactNote", row.getString("disabledImpactNote"));
        item.put("createdAt", instant(row.getTimestamp("createdAt")));
        item.put("updatedAt", instant(row.getTimestamp("updatedAt")));
        return item;
    }

    private Map<String, Object> templateById(int templateId) {
        return jdbcTemplate.query(
                """
                SELECT t.*, u.displayName AS createdByName,
                       r.displayName AS reviewedByName
                FROM comment_template t
                JOIN app_user u ON u.id = t.createdBy
                LEFT JOIN app_user r ON r.id = t.reviewedBy
                WHERE t.id = ?
                """,
                (row, index) -> templateMap(row),
                templateId).stream().findFirst().orElseThrow(
                        () -> new PlatformException(
                                HttpStatus.NOT_FOUND,
                                "TEMPLATE_NOT_FOUND",
                                "模板不存在。"));
    }

    private Map<String, Object> templateMap(java.sql.ResultSet row)
            throws java.sql.SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getInt("id"));
        item.put("title", row.getString("title"));
        item.put("category", row.getString("category"));
        item.put("structure", parseJson(row.getString("structure")));
        item.put("content", row.getString("content"));
        item.put("status", row.getString("status"));
        item.put("createdBy", row.getInt("createdBy"));
        item.put("createdByName", row.getString("createdByName"));
        item.put("reviewedBy", row.getObject("reviewedBy"));
        item.put("reviewNote", row.getString("reviewNote"));
        item.put("reviewedAt", instant(row.getTimestamp("reviewedAt")));
        item.put("createdAt", instant(row.getTimestamp("createdAt")));
        item.put("updatedAt", instant(row.getTimestamp("updatedAt")));
        return item;
    }

    private Map<String, Object> page(
            List<Map<String, Object>> rows,
            int page,
            int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        int from = Math.min(rows.size(), (safePage - 1) * safeSize);
        int to = Math.min(rows.size(), from + safeSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", rows.subList(from, to));
        result.put("page", safePage);
        result.put("pageSize", safeSize);
        result.put("total", rows.size());
        return result;
    }

    private String requiredText(
            JsonNode body,
            String field,
            String message) {
        String value = text(body, field, null);
        if (value == null || value.isBlank()) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR",
                    message);
        }
        return value.trim();
    }

    private String text(JsonNode body, String field, String fallback) {
        if (body == null || !body.has(field) || body.get(field).isNull()) {
            return fallback;
        }
        return body.get(field).asText(fallback);
    }

    private int integer(JsonNode body, String field, int fallback) {
        if (body == null || !body.has(field) || body.get(field).isNull()) {
            return fallback;
        }
        return body.get(field).asInt(fallback);
    }

    private Object parseJson(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception error) {
            return null;
        }
    }

    private String instant(java.sql.Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }
}
