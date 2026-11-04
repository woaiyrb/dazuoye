package com.zuoye.mark.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CommentaryTextService commentaryText;

    public StudentPlatformService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            CommentaryTextService commentaryText) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.commentaryText = commentaryText;
    }

    public List<Map<String, Object>> listHandouts(
            SessionService.PlatformUser student) {
        return jdbcTemplate.query(
                """
                SELECT hd.id, hd.homeworkId, h.title AS homeworkTitle,
                       hd.publishedAt,
                       (SELECT COUNT(DISTINCT phi.id)
                        FROM handout_item phi
                        JOIN grading_record pgr
                          ON pgr.questionId = phi.questionId
                         AND pgr.studentId = ?
                         AND pgr.batchId = hd.gradingBatchId
                         AND pgr.isCorrect = 0
                        WHERE phi.handoutId = hd.id AND phi.status = 'READY')
                         AS itemCount,
                       (SELECT COUNT(DISTINCT pc.questionId)
                        FROM correction_v2 pc
                        JOIN handout_item phi2 ON phi2.id = pc.handoutItemId
                        WHERE phi2.handoutId = hd.id
                          AND pc.studentId = ?) AS correctedCount,
                       (SELECT COUNT(DISTINCT pd.id)
                        FROM doubt pd
                        JOIN handout_item phi3 ON phi3.id = pd.handoutItemId
                        WHERE phi3.handoutId = hd.id
                          AND pd.studentId = ?) AS doubtCount,
                       cl.name AS className
                FROM handout hd
                JOIN course_homework h ON h.id = hd.homeworkId
                JOIN classroom cl ON cl.id = h.classId
                WHERE hd.status = 'PUBLISHED'
                  AND cl.id = ?
                ORDER BY hd.publishedAt DESC
                """,
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.getInt("id"));
                    item.put("homeworkId", row.getInt("homeworkId"));
                    item.put("homeworkTitle", row.getString("homeworkTitle"));
                    item.put("publishedAt", instant(row.getTimestamp("publishedAt")));
                    item.put("itemCount", row.getInt("itemCount"));
                    item.put("correctedCount", row.getInt("correctedCount"));
                    item.put("doubtCount", row.getInt("doubtCount"));
                    item.put("className", row.getString("className"));
                    return item;
                },
                student.id(),
                student.id(),
                student.id(),
                student.classId());
    }

    public Map<String, Object> getHandout(
            SessionService.PlatformUser student,
            int handoutId) {
        List<Map<String, Object>> handouts = jdbcTemplate.queryForList(
                """
                SELECT hd.id, hd.homeworkId, h.title AS homeworkTitle,
                       hd.publishedAt, h.classId
                FROM handout hd
                JOIN course_homework h ON h.id = hd.homeworkId
                WHERE hd.id = ? AND hd.status = 'PUBLISHED' AND h.classId = ?
                """,
                handoutId,
                student.classId());
        if (handouts.isEmpty()) {
            throw new PlatformException(
                    HttpStatus.NOT_FOUND,
                    "HANDOUT_NOT_FOUND",
                    "讲评不存在或尚未发布。");
        }
        Map<String, Object> handout = handouts.get(0);
        List<Map<String, Object>> items = new ArrayList<>();
        jdbcTemplate.query(
                """
                SELECT hi.id AS itemId, hi.questionId, q.seqNo,
                       q.questionType, q.stem, q.options, q.standardAnswer,
                       q.score, v.content AS commentary,
                       hi.currentVersionId, gr.studentAnswer,
                       gr.isCorrect, gr.score AS myScore, gr.teacherNote
                FROM handout_item hi
                JOIN handout hd ON hd.id = hi.handoutId
                JOIN course_homework h ON h.id = hd.homeworkId
                JOIN course_question q ON q.id = hi.questionId
                JOIN handout_item_version v ON v.id = hi.currentVersionId
                JOIN grading_record gr
                  ON gr.questionId = hi.questionId
                 AND gr.studentId = ?
                 AND gr.batchId = hd.gradingBatchId
                 AND gr.isCorrect = 0
                WHERE hi.handoutId = ? AND hi.status = 'READY'
                  AND h.classId = ?
                ORDER BY hi.sortNo, hi.id
                """,
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    int itemId = row.getInt("itemId");
                    int questionId = row.getInt("questionId");
                    item.put("itemId", itemId);
                    item.put("questionId", questionId);
                    item.put("seqNo", row.getInt("seqNo"));
                    item.put("questionType", row.getString("questionType"));
                    item.put("stem", row.getString("stem"));
                    item.put("options", parseJson(row.getString("options")));
                    item.put("standardAnswer", row.getString("standardAnswer"));
                    item.put("score", row.getInt("score"));
                    item.put(
                            "commentary",
                            commentaryText.toReadable(row.getString("commentary")));
                    item.put("commentaryVersionId", row.getInt("currentVersionId"));
                    Map<String, Object> record = new LinkedHashMap<>();
                    record.put("studentAnswer", row.getString("studentAnswer"));
                    record.put("isCorrect", row.getBoolean("isCorrect"));
                    record.put("score", row.getBigDecimal("myScore"));
                    record.put("teacherNote", row.getString("teacherNote"));
                    item.put("myRecord", record);
                    item.put("corrections", corrections(student, itemId));
                    item.put("doubts", doubts(student.id(), itemId));
                    items.add(item);
                    return item;
                },
                student.id(),
                handoutId,
                student.classId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", handout.get("id"));
        result.put("homeworkId", handout.get("homeworkId"));
        result.put("homeworkTitle", handout.get("homeworkTitle"));
        result.put("publishedAt", handout.get("publishedAt"));
        result.put("items", items);
        return result;
    }

    public Map<String, Object> listCorrections(
            SessionService.PlatformUser student,
            int page,
            int pageSize) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                SELECT c.*, q.seqNo
                FROM correction_v2 c
                JOIN course_question q ON q.id = c.questionId
                WHERE c.studentId = ?
                ORDER BY c.submittedAt DESC
                """,
                (row, index) -> correctionMap(row, student),
                student.id());
        return page(rows, page, pageSize);
    }

    @Transactional
    public Map<String, Object> createCorrection(
            SessionService.PlatformUser student,
            JsonNode body) {
        int handoutItemId = requiredInt(body, "handoutItemId");
        Map<String, Object> item = requireStudentItem(student, handoutItemId);
        int homeworkId = ((Number) item.get("homeworkId")).intValue();
        int questionId = ((Number) item.get("questionId")).intValue();
        int versionId = ((Number) item.get("commentaryVersionId")).intValue();
        Integer previous = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(MAX(versionNo), 0)
                FROM correction_v2
                WHERE studentId = ? AND handoutItemId = ?
                """,
                Integer.class,
                student.id(),
                handoutItemId);
        int nextVersion = (previous == null ? 0 : previous) + 1;
        jdbcTemplate.update(
                """
                INSERT INTO correction_v2
                  (homeworkId, questionId, studentId, handoutItemId,
                   itemVersionId, selectedAnswer, content, versionNo)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                homeworkId,
                questionId,
                student.id(),
                handoutItemId,
                versionId,
                text(body, "selectedAnswer", null),
                text(body, "content", null),
                nextVersion);
        Integer id = jdbcTemplate.queryForObject(
                """
                SELECT id FROM correction_v2
                WHERE studentId = ? AND handoutItemId = ?
                ORDER BY id DESC LIMIT 1
                """,
                Integer.class,
                student.id(),
                handoutItemId);
        return correctionById(id == null ? 0 : id, student);
    }

    public Map<String, Object> listDoubts(
            SessionService.PlatformUser student,
            int page,
            int pageSize) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                SELECT d.*, q.seqNo, h.title AS homeworkTitle,
                       h.id AS homeworkId
                FROM doubt d
                JOIN handout_item hi ON hi.id = d.handoutItemId
                JOIN handout hd ON hd.id = hi.handoutId
                JOIN course_homework h ON h.id = hd.homeworkId
                JOIN course_question q ON q.id = d.questionId
                WHERE d.studentId = ?
                ORDER BY d.createdAt DESC
                """,
                (row, index) -> doubtMap(row, student),
                student.id());
        return page(rows, page, pageSize);
    }

    @Transactional
    public Map<String, Object> createDoubt(
            SessionService.PlatformUser student,
            JsonNode body) {
        int handoutItemId = requiredInt(body, "handoutItemId");
        String content = requiredText(body, "content", "请输入疑问内容。");
        Map<String, Object> item = requireStudentItem(student, handoutItemId);
        jdbcTemplate.update(
                """
                INSERT INTO doubt
                  (studentId, handoutItemId, questionId, content, status)
                VALUES (?, ?, ?, ?, 'PENDING')
                """,
                student.id(),
                handoutItemId,
                ((Number) item.get("questionId")).intValue(),
                content);
        Integer id = jdbcTemplate.queryForObject(
                """
                SELECT id FROM doubt
                WHERE studentId = ? AND handoutItemId = ?
                ORDER BY id DESC LIMIT 1
                """,
                Integer.class,
                student.id(),
                handoutItemId);
        return doubtById(id == null ? 0 : id, student);
    }

    private Map<String, Object> requireStudentItem(
            SessionService.PlatformUser student,
            int itemId) {
        return jdbcTemplate.query(
                """
                SELECT hi.id, hi.questionId, hi.currentVersionId,
                       hd.homeworkId
                FROM handout_item hi
                JOIN handout hd ON hd.id = hi.handoutId
                JOIN course_homework h ON h.id = hd.homeworkId
                WHERE hi.id = ? AND hd.status = 'PUBLISHED'
                  AND hi.status = 'READY' AND h.classId = ?
                """,
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("itemId", row.getInt("id"));
                    item.put("questionId", row.getInt("questionId"));
                    item.put("commentaryVersionId",
                            row.getInt("currentVersionId"));
                    item.put("homeworkId", row.getInt("homeworkId"));
                    return item;
                },
                itemId,
                student.classId()).stream().findFirst().orElseThrow(
                        () -> new PlatformException(
                                HttpStatus.NOT_FOUND,
                                "HANDOUT_ITEM_NOT_FOUND",
                                "讲评条目不存在或尚未发布。"));
    }

    private List<Map<String, Object>> corrections(
            SessionService.PlatformUser student,
            int handoutItemId) {
        return jdbcTemplate.query(
                """
                SELECT c.*, q.seqNo
                FROM correction_v2 c
                JOIN course_question q ON q.id = c.questionId
                WHERE c.studentId = ? AND c.handoutItemId = ?
                ORDER BY c.versionNo DESC
                """,
                (row, index) -> correctionMap(row, student),
                student.id(),
                handoutItemId);
    }

    private List<Map<String, Object>> doubts(
            int studentId,
            int handoutItemId) {
        return jdbcTemplate.query(
                """
                SELECT d.*, q.seqNo, h.title AS homeworkTitle,
                       h.id AS homeworkId
                FROM doubt d
                JOIN handout_item hi ON hi.id = d.handoutItemId
                JOIN handout hd ON hd.id = hi.handoutId
                JOIN course_homework h ON h.id = hd.homeworkId
                JOIN course_question q ON q.id = d.questionId
                WHERE d.studentId = ? AND d.handoutItemId = ?
                ORDER BY d.createdAt DESC
                """,
                (row, index) -> doubtMap(row, null),
                studentId,
                handoutItemId);
    }

    private Map<String, Object> correctionById(
            int correctionId,
            SessionService.PlatformUser student) {
        return jdbcTemplate.query(
                """
                SELECT c.*, q.seqNo, u.studentNo,
                       u.displayName AS studentName
                FROM correction_v2 c
                JOIN course_question q ON q.id = c.questionId
                JOIN app_user u ON u.id = c.studentId
                WHERE c.id = ?
                """,
                (row, index) -> correctionMap(row, student),
                correctionId).stream().findFirst().orElseThrow(
                        () -> new PlatformException(
                                HttpStatus.NOT_FOUND,
                                "CORRECTION_NOT_FOUND",
                                "订正记录不存在。"));
    }

    private Map<String, Object> correctionMap(
            ResultSet row,
            SessionService.PlatformUser student) throws SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getInt("id"));
        item.put("homeworkId", row.getInt("homeworkId"));
        item.put("questionId", row.getInt("questionId"));
        item.put("seqNo", row.getInt("seqNo"));
        item.put("studentId", row.getInt("studentId"));
        item.put("studentNo", student == null
                ? row.getString("studentNo")
                : student.studentNo());
        item.put("studentName", student == null
                ? row.getString("studentName")
                : student.displayName());
        item.put("handoutItemId", row.getInt("handoutItemId"));
        item.put("itemVersionId", row.getInt("itemVersionId"));
        item.put("selectedAnswer", row.getString("selectedAnswer"));
        item.put("content", row.getString("content"));
        item.put("versionNo", row.getInt("versionNo"));
        item.put("submittedAt", instant(row.getTimestamp("submittedAt")));
        return item;
    }

    private Map<String, Object> doubtById(
            int doubtId,
            SessionService.PlatformUser student) {
        return jdbcTemplate.query(
                """
                SELECT d.*, q.seqNo, h.title AS homeworkTitle,
                       h.id AS homeworkId
                FROM doubt d
                JOIN handout_item hi ON hi.id = d.handoutItemId
                JOIN handout hd ON hd.id = hi.handoutId
                JOIN course_homework h ON h.id = hd.homeworkId
                JOIN course_question q ON q.id = d.questionId
                WHERE d.id = ?
                """,
                (row, index) -> doubtMap(row, student),
                doubtId).stream().findFirst().orElseThrow(
                        () -> new PlatformException(
                                HttpStatus.NOT_FOUND,
                                "DOUBT_NOT_FOUND",
                                "疑问不存在。"));
    }

    private Map<String, Object> doubtMap(
            ResultSet row,
            SessionService.PlatformUser student) throws SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        int id = row.getInt("id");
        item.put("id", id);
        item.put("studentId", row.getInt("studentId"));
        item.put("studentNo", student == null ? null : student.studentNo());
        item.put("studentName", student == null ? null : student.displayName());
        item.put("handoutItemId", row.getInt("handoutItemId"));
        item.put("questionId", row.getInt("questionId"));
        item.put("seqNo", row.getInt("seqNo"));
        item.put("content", row.getString("content"));
        item.put("status", row.getString("status"));
        item.put("createdAt", instant(row.getTimestamp("createdAt")));
        item.put("updatedAt", instant(row.getTimestamp("updatedAt")));
        item.put("replies", replies(id));
        item.put("homeworkId", row.getInt("homeworkId"));
        item.put("homeworkTitle", row.getString("homeworkTitle"));
        return item;
    }

    private List<Map<String, Object>> replies(int doubtId) {
        return jdbcTemplate.query(
                """
                SELECT r.*, u.displayName AS teacherName
                FROM doubt_reply r
                JOIN app_user u ON u.id = r.teacherId
                WHERE r.doubtId = ?
                ORDER BY r.createdAt
                """,
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.getInt("id"));
                    item.put("teacherId", row.getInt("teacherId"));
                    item.put("teacherName", row.getString("teacherName"));
                    item.put("content", row.getString("content"));
                    item.put("createdAt", instant(row.getTimestamp("createdAt")));
                    return item;
                },
                doubtId);
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

    private Object parseJson(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception error) {
            return null;
        }
    }

    private int requiredInt(JsonNode body, String field) {
        if (body == null || !body.has(field) || !body.get(field).canConvertToInt()) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR",
                    field + " 不能为空。");
        }
        return body.get(field).asInt();
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

    private String instant(java.sql.Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }
}
