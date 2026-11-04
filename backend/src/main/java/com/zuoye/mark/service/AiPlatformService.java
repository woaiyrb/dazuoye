package com.zuoye.mark.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

@Service
public class AiPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DeepTutorClient deepTutor;
    private final CommentaryTextService commentaryText;

    public AiPlatformService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            DeepTutorClient deepTutor,
            CommentaryTextService commentaryText) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.deepTutor = deepTutor;
        this.commentaryText = commentaryText;
    }

    public Map<String, Object> generateDraft(
            SessionService.PlatformUser teacher,
            int homeworkId,
            int questionId) {
        Map<String, Object> question = jdbcTemplate.queryForMap(
                """
                SELECT q.*, h.title
                FROM course_question q
                JOIN course_homework h ON h.id = q.homeworkId
                JOIN classroom c ON c.id = h.classId
                WHERE q.id = ? AND q.homeworkId = ? AND q.isDeleted = 0
                  AND c.teacherId = ?
                """,
                questionId,
                homeworkId,
                teacher.id());
        long started = System.nanoTime();
        String content;
        String status = "GENERATED";
        String errorCode = null;
        try {
            content = commentaryText.toReadable(deepTutor.chat("""
                    请为下面的初中数学题目写一段学生讲评。
                    只使用题目、标准答案和教师解析，不改变得分事实。
                    题目：%s
                    标准答案：%s
                    教师解析：%s
                    """.formatted(
                    question.get("stem"),
                    question.get("standardAnswer"),
                    question.get("teacherAnalysis"))));
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("AI 返回空内容");
            }
        } catch (Exception error) {
            status = "FAILED";
            errorCode = "AI_FAILED";
            content = "AI 暂不可用，请教师手工填写讲评。";
        }
        int elapsedMs = (int) ((System.nanoTime() - started) / 1_000_000);
        Integer generationNo = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(MAX(generationNo), 0) + 1
                FROM ai_comment_draft
                WHERE homeworkId = ? AND questionId = ?
                """,
                Integer.class,
                homeworkId,
                questionId);
        String evidence = evidence(question);
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        String finalContent = content;
        String finalStatus = status;
        String finalError = errorCode;
        int finalGeneration = generationNo == null ? 1 : generationNo;
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO ai_comment_draft
                      (homeworkId, questionId, draftType, content, status,
                       model, promptVersion, generationNo, requestId,
                       elapsedMs, errorCode, evidence)
                    VALUES (?, ?, 'QUESTION_COMMENTARY', ?, ?, 'deeptutor',
                            'v1', ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, homeworkId);
            statement.setInt(2, questionId);
            statement.setString(3, finalContent);
            statement.setString(4, finalStatus);
            statement.setInt(5, finalGeneration);
            statement.setString(6, java.util.UUID.randomUUID().toString());
            statement.setInt(7, elapsedMs);
            statement.setString(8, finalError);
            statement.setString(9, evidence);
            return statement;
        }, keyHolder);
        int id = keyHolder.getKey() == null ? 0 : keyHolder.getKey().intValue();
        return draftById(id);
    }

    private Map<String, Object> draftById(int id) {
        return jdbcTemplate.query(
                "SELECT * FROM ai_comment_draft WHERE id = ?",
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.getInt("id"));
                    item.put("homeworkId", row.getInt("homeworkId"));
                    item.put("questionId", row.getObject("questionId"));
                    item.put("draftType", row.getString("draftType"));
                    item.put("content", row.getString("content"));
                    item.put("status", row.getString("status"));
                    item.put("model", row.getString("model"));
                    item.put("promptVersion", row.getString("promptVersion"));
                    item.put("generationNo", row.getInt("generationNo"));
                    item.put("requestId", row.getString("requestId"));
                    item.put("elapsedMs", row.getObject("elapsedMs"));
                    item.put("errorCode", row.getString("errorCode"));
                    item.put("createdAt",
                            row.getTimestamp("createdAt").toInstant().toString());
                    item.put("evidence", parseJson(row.getString("evidence")));
                    return item;
                },
                id).stream().findFirst().orElseThrow(
                        () -> new PlatformException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "AI_DRAFT_NOT_SAVED",
                                "AI 草稿保存失败。"));
    }

    private String evidence(Map<String, Object> question) {
        try {
            return objectMapper.writeValueAsString(List.of(
                    Map.of(
                            "evidenceType", "QUESTION_STEM",
                            "snapshotText", String.valueOf(question.get("stem"))),
                    Map.of(
                            "evidenceType", "STANDARD_ANSWER",
                            "snapshotText", String.valueOf(question.get("standardAnswer"))),
                    Map.of(
                            "evidenceType", "TEACHER_ANALYSIS",
                            "snapshotText", String.valueOf(question.get("teacherAnalysis")))));
        } catch (Exception error) {
            return "[]";
        }
    }

    private Object parseJson(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception error) {
            return null;
        }
    }
}
