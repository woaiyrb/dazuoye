package com.zuoye.mark.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionEditorJdbcService {

    private static final Set<String> SUPPORTED_TYPES =
            Set.of("SINGLE_CORRECT", "TRUE_FALSE");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public QuestionEditorJdbcService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public EditorAssignment getAssignment(long assignmentId) {
        List<Map<String, Object>> assignments = jdbcTemplate.queryForList(
                "SELECT id, name, introduction FROM assignment WHERE id = ?",
                assignmentId);
        if (assignments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "作业不存在。");
        }
        Map<String, Object> assignment = assignments.get(0);
        List<EditableQuestion> questions = jdbcTemplate.query(
                """
                SELECT id, totalPoints, type, authorComment, question,
                       choices, randomizedChoices
                FROM question
                WHERE assignmentId = ? AND isDeleted = 0
                ORDER BY id
                """,
                (row, index) -> new EditableQuestion(
                        row.getLong("id"),
                        row.getString("type"),
                        row.getString("question"),
                        row.getInt("totalPoints"),
                        row.getString("authorComment") == null
                                ? ""
                                : row.getString("authorComment"),
                        parseChoices(row.getString("choices")),
                        row.getBoolean("randomizedChoices")),
                assignmentId);
        return new EditorAssignment(
                assignmentId,
                String.valueOf(assignment.get("name")),
                assignment.get("introduction") == null
                        ? ""
                        : String.valueOf(assignment.get("introduction")),
                questions);
    }

    @Transactional
    public EditableQuestion create(long assignmentId, QuestionSaveRequest request) {
        validate(request);
        String choicesJson = serializeChoices(request);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO question
                      (totalPoints, type, authorComment, question, choices,
                       randomizedChoices, assignmentId, isDeleted)
                    VALUES (?, ?, ?, ?, ?, ?, ?, false)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, request.totalPoints());
            statement.setString(2, request.type());
            statement.setString(3, request.authorComment());
            statement.setString(4, request.question().trim());
            statement.setString(5, choicesJson);
            statement.setBoolean(6, Boolean.TRUE.equals(request.randomizedChoices()));
            statement.setLong(7, assignmentId);
            return statement;
        }, keyHolder);
        long questionId = keyHolder.getKey() == null ? 0 : keyHolder.getKey().longValue();
        touchAssignment(assignmentId);
        return getQuestion(assignmentId, questionId);
    }

    @Transactional
    public EditableQuestion update(
            long assignmentId,
            long questionId,
            QuestionSaveRequest request) {
        validate(request);
        int changed = jdbcTemplate.update(
                """
                UPDATE question
                SET totalPoints = ?, type = ?, authorComment = ?, question = ?,
                    choices = ?, randomizedChoices = ?
                WHERE id = ? AND assignmentId = ? AND isDeleted = 0
                """,
                request.totalPoints(),
                request.type(),
                request.authorComment(),
                request.question().trim(),
                serializeChoices(request),
                Boolean.TRUE.equals(request.randomizedChoices()),
                questionId,
                assignmentId);
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "题目不存在。");
        }
        touchAssignment(assignmentId);
        return getQuestion(assignmentId, questionId);
    }

    @Transactional
    public void delete(long assignmentId, long questionId) {
        int changed = jdbcTemplate.update(
                """
                UPDATE question SET isDeleted = true
                WHERE id = ? AND assignmentId = ?
                """,
                questionId,
                assignmentId);
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "题目不存在。");
        }
        touchAssignment(assignmentId);
    }

    private EditableQuestion getQuestion(long assignmentId, long questionId) {
        List<EditableQuestion> rows = jdbcTemplate.query(
                """
                SELECT id, totalPoints, type, authorComment, question,
                       choices, randomizedChoices
                FROM question
                WHERE id = ? AND assignmentId = ? AND isDeleted = 0
                """,
                (row, index) -> new EditableQuestion(
                        row.getLong("id"),
                        row.getString("type"),
                        row.getString("question"),
                        row.getInt("totalPoints"),
                        row.getString("authorComment") == null
                                ? ""
                                : row.getString("authorComment"),
                        parseChoices(row.getString("choices")),
                        row.getBoolean("randomizedChoices")),
                questionId,
                assignmentId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "题目不存在。");
        }
        return rows.get(0);
    }

    private List<EditableChoice> parseChoices(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(
                    json,
                    new TypeReference<>() {});
            List<EditableChoice> choices = new ArrayList<>();
            for (int index = 0; index < raw.size(); index++) {
                Map<String, Object> row = raw.get(index);
                choices.add(new EditableChoice(
                        row.get("id") instanceof Number number
                                ? number.longValue()
                                : index + 1L,
                        String.valueOf(row.getOrDefault("choice", "")),
                        Boolean.TRUE.equals(row.get("isCorrect")),
                        row.get("points") instanceof Number number
                                ? number.intValue()
                                : 0,
                        row.get("feedback") == null
                                ? ""
                                : String.valueOf(row.get("feedback"))));
            }
            return List.copyOf(choices);
        } catch (Exception error) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "题目选项数据格式错误。",
                    error);
        }
    }

    private String serializeChoices(QuestionSaveRequest request) {
        List<Map<String, Object>> choices = new ArrayList<>();
        int id = 1;
        for (EditableChoice choice : request.choices()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id++);
            row.put("choice", choice.label().trim());
            row.put("isCorrect", choice.correct());
            row.put("points", choice.correct() ? request.totalPoints() : 0);
            row.put("feedback", choice.feedback() == null ? "" : choice.feedback());
            choices.add(row);
        }
        try {
            return objectMapper.writeValueAsString(choices);
        } catch (Exception error) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "题目选项无法保存。",
                    error);
        }
    }

    private void validate(QuestionSaveRequest request) {
        if (request.type() == null || !SUPPORTED_TYPES.contains(request.type())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "第一版只支持单选题和判断题。");
        }
        if (request.question() == null || request.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入题干。");
        }
        if (request.totalPoints() < 1 || request.totalPoints() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "题目分值应在 1 到 100 之间。");
        }
        if (request.choices() == null || request.choices().size() < 2) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "单选题和判断题至少需要两个选项。");
        }
        long correctCount = request.choices().stream()
                .filter(EditableChoice::correct)
                .count();
        if (correctCount != 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "必须且只能设置一个正确答案。");
        }
    }

    private void touchAssignment(long assignmentId) {
        jdbcTemplate.update(
                "UPDATE assignment SET updatedAt = CURRENT_TIMESTAMP(3) WHERE id = ?",
                assignmentId);
    }

    public record EditorAssignment(
            long id,
            String name,
            String introduction,
            List<EditableQuestion> questions) {}

    public record EditableQuestion(
            long id,
            String type,
            String question,
            int totalPoints,
            String authorComment,
            List<EditableChoice> choices,
            boolean randomizedChoices) {}

    public record EditableChoice(
            long id,
            String label,
            boolean correct,
            int points,
            String feedback) {}

    public record QuestionSaveRequest(
            String type,
            String question,
            int totalPoints,
            String authorComment,
            List<EditableChoice> choices,
            Boolean randomizedChoices) {}
}
