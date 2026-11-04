package com.zuoye.mark.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single JDBC source for the simplified Vue + Java application.
 *
 * The browser never reads Prisma or the retired Node API. All teacher and
 * student pages receive plain UTF-8 data from this service.
 */
@Service
public class AssignmentReadService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AssignmentReadService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public UserSession session() {
        long assignmentId = jdbcTemplate.query(
                "SELECT id FROM assignment ORDER BY id LIMIT 1",
                rows -> rows.next() ? rows.getLong(1) : 1L);
        return new UserSession(
                "teacher@local.demo",
                "author",
                assignmentId,
                "本地演示账号",
                "zh-CN");
    }

    public List<AssignmentData> listAssignments() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM assignment ORDER BY id",
                Long.class);
        List<AssignmentData> assignments = new ArrayList<>();
        for (Long id : ids) {
            assignments.add(getAssignment(id));
        }
        return List.copyOf(assignments);
    }

    public AssignmentData getAssignment(long assignmentId) {
        List<AssignmentData> rows = jdbcTemplate.query(
                """
                SELECT id, name, introduction, instructions,
                       gradingCriteriaOverview, timeEstimateMinutes,
                       passingGrade, published, updatedAt
                FROM assignment
                WHERE id = ?
                """,
                (row, index) -> new AssignmentData(
                        row.getLong("id"),
                        decodeText(row.getString("name")),
                        decodeText(row.getString("introduction")),
                        decodeText(row.getString("instructions")),
                        decodeText(row.getString("gradingCriteriaOverview")),
                        row.getInt("timeEstimateMinutes"),
                        row.getInt("passingGrade"),
                        row.getBoolean("published"),
                        row.getTimestamp("updatedAt").toInstant().toString(),
                        List.of()),
                assignmentId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "作业不存在。");
        }
        AssignmentData assignment = rows.get(0);
        List<QuestionData> questions = jdbcTemplate.query(
                """
                SELECT id, totalPoints, type, authorComment, question,
                       choices, answer, randomizedChoices
                FROM question
                WHERE assignmentId = ? AND isDeleted = 0
                ORDER BY id
                """,
                (row, index) -> new QuestionData(
                        row.getLong("id"),
                        row.getString("type"),
                        decodeText(row.getString("question")),
                        row.getInt("totalPoints"),
                        decodeText(row.getString("authorComment")),
                        parseChoices(
                                row.getString("choices"),
                                row.getObject("answer") == null
                                        ? null
                                        : row.getBoolean("answer"),
                                row.getInt("totalPoints")),
                        row.getBoolean("randomizedChoices")),
                assignmentId);
        return new AssignmentData(
                assignment.id(),
                assignment.name(),
                assignment.introduction(),
                assignment.instructions(),
                assignment.gradingCriteriaOverview(),
                assignment.timeEstimateMinutes(),
                assignment.passingGrade(),
                assignment.published(),
                assignment.updatedAt(),
                List.copyOf(questions));
    }

    public List<ClassData> listClasses() {
        return jdbcTemplate.query(
                """
                SELECT c.id, c.name, c.classCode, c.subject, c.term, c.gradeLevel,
                       (SELECT COUNT(*) FROM classenrollment ce
                        WHERE ce.classId = c.id AND ce.status = 'ACTIVE') AS studentCount,
                       (SELECT COUNT(*) FROM assignmentclass ac
                        WHERE ac.classId = c.id) AS assignmentCount
                FROM class c
                WHERE c.isArchived = 0
                ORDER BY c.id
                """,
                (row, index) -> new ClassData(
                        row.getLong("id"),
                        row.getString("name"),
                        row.getString("classCode"),
                        row.getString("subject"),
                        row.getString("term"),
                        row.getString("gradeLevel"),
                        row.getInt("studentCount"),
                        row.getInt("assignmentCount")));
    }

    public List<KnowledgePointData> listKnowledgePoints() {
        return jdbcTemplate.query(
                """
                SELECT kp.id, kp.name, kp.subject, kp.level,
                       COUNT(qkp.questionId) AS questionCount
                FROM knowledgepoint kp
                LEFT JOIN questionknowledgepoint qkp
                  ON qkp.knowledgePointId = kp.id
                WHERE kp.isActive = 1
                GROUP BY kp.id, kp.name, kp.subject, kp.level
                ORDER BY kp.level, kp.name
                """,
                (row, index) -> new KnowledgePointData(
                        row.getLong("id"),
                        row.getString("name"),
                        row.getString("subject"),
                        row.getInt("level"),
                        row.getInt("questionCount")));
    }

    public long totalQuestions() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question WHERE isDeleted = 0",
                Long.class);
        return count == null ? 0 : count;
    }

    private List<ChoiceData> parseChoices(
            String json,
            Boolean trueFalseAnswer,
            int totalPoints) {
        if (json == null || json.isBlank()) {
            if (trueFalseAnswer == null) return List.of();
            return List.of(
                    new ChoiceData(
                            1,
                            "正确",
                            trueFalseAnswer,
                            trueFalseAnswer ? totalPoints : 0,
                            ""),
                    new ChoiceData(
                            2,
                            "错误",
                            !trueFalseAnswer,
                            trueFalseAnswer ? 0 : totalPoints,
                            ""));
        }
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(
                    json,
                    new TypeReference<>() {});
            List<ChoiceData> choices = new ArrayList<>();
            for (int index = 0; index < rows.size(); index++) {
                Map<String, Object> row = rows.get(index);
                long id = row.get("id") instanceof Number number
                        ? number.longValue()
                        : index + 1L;
                String label = decodeText(String.valueOf(
                        row.getOrDefault("choice", "")));
                choices.add(new ChoiceData(
                        id,
                        normalizeTrueFalseLabel(label),
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

    private String normalizeTrueFalseLabel(String value) {
        return switch (value) {
            case "true" -> "正确";
            case "false" -> "错误";
            default -> value;
        };
    }

    public String decodeText(String value) {
        if (value == null || value.isBlank()) return "";
        String compact = value.replaceAll("\\s", "");
        if (compact.length() < 12
                || !compact.matches("^[A-Za-z0-9+/]+={0,2}$")) {
            return value;
        }
        try {
            String decoded = new String(
                    Base64.getDecoder().decode(compact),
                    StandardCharsets.UTF_8);
            return decoded.contains("\u0000") ? value : decoded;
        } catch (IllegalArgumentException error) {
            return value;
        }
    }

    public record UserSession(
            String userId,
            String role,
            long assignmentId,
            String displayName,
            String locale) {}

    public record AssignmentData(
            long id,
            String name,
            String introduction,
            String instructions,
            String gradingCriteriaOverview,
            int timeEstimateMinutes,
            int passingGrade,
            boolean published,
            String updatedAt,
            List<QuestionData> questions) {}

    public record QuestionData(
            long id,
            String type,
            String question,
            int totalPoints,
            String authorComment,
            List<ChoiceData> choices,
            boolean randomizedChoices) {}

    public record ChoiceData(
            long id,
            String choice,
            boolean isCorrect,
            int points,
            String feedback) {}

    public record ClassData(
            long id,
            String name,
            String classCode,
            String subject,
            String term,
            String gradeLevel,
            int studentCount,
            int assignmentCount) {}

    public record KnowledgePointData(
            long id,
            String name,
            String subject,
            int level,
            int questionCount) {}
}
