package com.zuoye.mark.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentHomeworkAssessmentService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public StudentHomeworkAssessmentService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listHomeworks(
            SessionService.PlatformUser student) {
        if (student.classId() == null) return List.of();
        return jdbcTemplate.query(
                """
                SELECT h.id, h.title, h.description, h.dueAt,
                       h.timeEstimateMinutes,
                       (SELECT COUNT(*) FROM course_question q
                        WHERE q.homeworkId = h.id AND q.isDeleted = 0)
                         AS questionCount,
                       s.id AS submissionId, s.attemptNo, s.score,
                       s.maxScore, s.scoreRate, s.submittedAt
                FROM course_homework h
                LEFT JOIN student_homework_submission s
                  ON s.id = (
                    SELECT s2.id FROM student_homework_submission s2
                    WHERE s2.homeworkId = h.id AND s2.studentId = ?
                    ORDER BY s2.attemptNo DESC LIMIT 1
                  )
                WHERE h.classId = ? AND h.status = 'PUBLISHED'
                ORDER BY h.publishedAt DESC, h.id DESC
                """,
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.getInt("id"));
                    item.put("title", row.getString("title"));
                    item.put("description", row.getString("description"));
                    item.put("dueAt", instant(row.getTimestamp("dueAt")));
                    item.put("timeEstimateMinutes",
                            row.getInt("timeEstimateMinutes"));
                    item.put("questionCount", row.getInt("questionCount"));
                    item.put("submitted", row.getObject("submissionId") != null);
                    item.put("submissionId", row.getObject("submissionId"));
                    item.put("attemptNo", row.getObject("attemptNo"));
                    item.put("score", row.getObject("score"));
                    item.put("maxScore", row.getObject("maxScore"));
                    item.put("scoreRate", row.getObject("scoreRate"));
                    item.put("submittedAt", instant(row.getTimestamp("submittedAt")));
                    return item;
                },
                student.id(),
                student.classId());
    }

    public Map<String, Object> homeworkDetail(
            SessionService.PlatformUser student,
            int homeworkId) {
        Map<String, Object> homework = jdbcTemplate.query(
                """
                SELECT id, title, description, instructions, dueAt,
                       timeEstimateMinutes
                FROM course_homework
                WHERE id = ? AND classId = ? AND status = 'PUBLISHED'
                """,
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.getInt("id"));
                    item.put("title", row.getString("title"));
                    item.put("description", row.getString("description"));
                    item.put("instructions", row.getString("instructions"));
                    item.put("dueAt", instant(row.getTimestamp("dueAt")));
                    item.put("timeEstimateMinutes",
                            row.getInt("timeEstimateMinutes"));
                    return item;
                },
                homeworkId,
                student.classId()).stream().findFirst().orElseThrow(
                        () -> new PlatformException(
                                HttpStatus.NOT_FOUND,
                                "HOMEWORK_NOT_FOUND",
                                "作业不存在或尚未发布。"));
        List<Map<String, Object>> questions = jdbcTemplate.query(
                """
                SELECT id, seqNo, questionType, stem, options, score
                FROM course_question
                WHERE homeworkId = ? AND isDeleted = 0
                ORDER BY seqNo, id
                """,
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    String questionType = row.getString("questionType");
                    Object options = parseJson(row.getString("options"));
                    if ("TRUE_FALSE".equals(questionType)
                            && (!(options instanceof List<?> list)
                                    || list.isEmpty())) {
                        options = List.of(
                                Map.of("key", "TRUE", "text", "正确"),
                                Map.of("key", "FALSE", "text", "错误"));
                    }
                    item.put("id", row.getInt("id"));
                    item.put("seqNo", row.getInt("seqNo"));
                    item.put("questionType", questionType);
                    item.put("stem", row.getString("stem"));
                    item.put("options", options);
                    item.put("score", row.getInt("score"));
                    return item;
                },
                homeworkId);
        homework.put("questions", questions);
        return homework;
    }

    @Transactional
    public Map<String, Object> submit(
            SessionService.PlatformUser student,
            int homeworkId,
            JsonNode body) {
        homeworkDetail(student, homeworkId);
        JsonNode answerNode = body.path("answers");
        if (!answerNode.isObject() || answerNode.isEmpty()) {
            throw validation("请至少提交一道题的答案。");
        }
        List<Map<String, Object>> questions = jdbcTemplate.queryForList(
                """
                SELECT id, seqNo, questionType, stem, options,
                       standardAnswer, teacherAnalysis, score
                FROM course_question
                WHERE homeworkId = ? AND isDeleted = 0
                ORDER BY seqNo, id
                """,
                homeworkId);
        List<GradedAnswer> graded = new ArrayList<>();
        int totalScore = 0;
        int maxScore = 0;
        for (Map<String, Object> question : questions) {
            int questionId = intValue(question.get("id"));
            int points = intValue(question.get("score"));
            maxScore += points;
            String rawAnswer = answerNode.path(
                    Integer.toString(questionId)).asText("");
            String correctAnswer = value(question.get("standardAnswer"));
            List<Map<String, Object>> options = parseOptions(
                    value(question.get("options")));
            String selectedLabel = normalizeAnswer(
                    value(question.get("questionType")),
                    options,
                    rawAnswer);
            String correctLabel = normalizeAnswer(
                    value(question.get("questionType")),
                    options,
                    correctAnswer);
            boolean correct = !selectedLabel.isBlank()
                    && answersEqual(
                            value(question.get("questionType")),
                            options,
                            rawAnswer,
                            correctAnswer);
            int awarded = correct ? points : 0;
            totalScore += awarded;
            graded.add(new GradedAnswer(
                    questionId,
                    intValue(question.get("seqNo")),
                    value(question.get("questionType")),
                    value(question.get("stem")),
                    selectedLabel,
                    correctLabel,
                    value(question.get("teacherAnalysis")),
                    correct,
                    awarded,
                    points));
        }
        int attemptNo = nextAttemptNo(student.id(), homeworkId);
        int rate = maxScore == 0
                ? 0
                : (int) Math.round(totalScore * 100.0 / maxScore);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int finalAttempt = attemptNo;
        int finalTotalScore = totalScore;
        int finalMaxScore = maxScore;
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO student_homework_submission
                      (homeworkId, studentId, attemptNo, score, maxScore,
                       scoreRate, status)
                    VALUES (?, ?, ?, ?, ?, ?, 'SUBMITTED')
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, homeworkId);
            statement.setInt(2, student.id());
            statement.setInt(3, finalAttempt);
            statement.setInt(4, finalTotalScore);
            statement.setInt(5, finalMaxScore);
            statement.setBigDecimal(6, java.math.BigDecimal.valueOf(rate));
            return statement;
        }, keyHolder);
        long submissionId = keyHolder.getKey() == null
                ? 0
                : keyHolder.getKey().longValue();
        for (GradedAnswer answer : graded) {
            jdbcTemplate.update(
                    """
                    INSERT INTO student_question_answer
                      (submissionId, questionId, questionStem, questionType,
                       selectedAnswer, correctAnswer, teacherAnalysis,
                       isCorrect, awardedScore, maxScore)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    submissionId,
                    answer.questionId(),
                    answer.stem(),
                    answer.questionType(),
                    answer.selectedAnswer(),
                    answer.correctAnswer(),
                    answer.teacherAnalysis(),
                    answer.correct(),
                    answer.awardedScore(),
                    answer.maxScore());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submissionId);
        result.put("homeworkId", homeworkId);
        result.put("studentId", student.id());
        result.put("attemptNo", attemptNo);
        result.put("score", totalScore);
        result.put("maxScore", maxScore);
        result.put("scoreRate", rate);
        result.put("passed", rate >= 60);
        result.put("submittedAt", OffsetDateTime.now().toString());
        result.put(
                "answers",
                graded.stream().map(GradedAnswer::toMap).toList());
        return result;
    }

    public Map<String, Object> latestResult(
            SessionService.PlatformUser student,
            int homeworkId) {
        Map<String, Object> submission = latestSubmission(
                student.id(),
                homeworkId);
        List<Map<String, Object>> answers = jdbcTemplate.query(
                """
                SELECT a.*, q.seqNo
                FROM student_question_answer a
                JOIN course_question q ON q.id = a.questionId
                WHERE a.submissionId = ?
                ORDER BY q.seqNo, a.id
                """,
                (row, index) -> answerMap(row),
                submission.get("id"));
        submission.put("answers", answers);
        return submission;
    }

    public Map<String, Object> scoreSummary(
            SessionService.PlatformUser student) {
        Map<String, Object> result = jdbcTemplate.queryForMap(
                """
                SELECT COALESCE(SUM(s.score), 0) AS totalScore,
                       COALESCE(SUM(s.maxScore), 0) AS maxScore,
                       COUNT(*) AS submissionCount
                FROM student_homework_submission s
                JOIN (
                  SELECT homeworkId, MAX(attemptNo) AS maxAttempt
                  FROM student_homework_submission
                  WHERE studentId = ?
                  GROUP BY homeworkId
                ) latest
                  ON latest.homeworkId = s.homeworkId
                 AND latest.maxAttempt = s.attemptNo
                WHERE s.studentId = ?
                """,
                student.id(),
                student.id());
        int total = intValue(result.get("totalScore"));
        int max = intValue(result.get("maxScore"));
        result.put(
                "scoreRate",
                max == 0 ? 0 : Math.round(total * 1000.0 / max) / 10.0);
        return result;
    }

    public List<Map<String, Object>> wrongBook(
            SessionService.PlatformUser student,
            Integer homeworkId) {
        String homeworkClause = homeworkId == null
                ? ""
                : " AND s.homeworkId = ?\n";
        List<Object> arguments = new ArrayList<>();
        arguments.add(student.id());
        if (homeworkId != null) arguments.add(homeworkId);
        return jdbcTemplate.query(
                """
                SELECT h.id AS homeworkId, h.title AS homeworkTitle,
                       a.questionId, q.seqNo, a.questionType, a.questionStem,
                       a.selectedAnswer, a.correctAnswer, a.teacherAnalysis,
                       a.maxScore, s.attemptNo, s.submittedAt
                FROM student_homework_submission s
                JOIN student_question_answer a ON a.submissionId = s.id
                JOIN course_homework h ON h.id = s.homeworkId
                JOIN course_question q ON q.id = a.questionId
                WHERE s.studentId = ? AND a.isCorrect = 0
                  AND s.id = (
                    SELECT latest.id
                    FROM student_homework_submission latest
                    WHERE latest.studentId = s.studentId
                      AND latest.homeworkId = s.homeworkId
                    ORDER BY latest.attemptNo DESC, latest.id DESC
                    LIMIT 1
                  )
                """ + homeworkClause + """
                ORDER BY s.submittedAt DESC, q.seqNo, a.id
                """,
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("homeworkId", row.getInt("homeworkId"));
                    item.put("homeworkTitle", row.getString("homeworkTitle"));
                    item.put("questionId", row.getInt("questionId"));
                    item.put("seqNo", row.getInt("seqNo"));
                    item.put("questionType", row.getString("questionType"));
                    item.put("questionStem", row.getString("questionStem"));
                    item.put("selectedAnswer", row.getString("selectedAnswer"));
                    item.put("correctAnswer", row.getString("correctAnswer"));
                    item.put("teacherAnalysis", row.getString("teacherAnalysis"));
                    item.put("maxScore", row.getBigDecimal("maxScore"));
                    item.put("attemptNo", row.getInt("attemptNo"));
                    item.put("submittedAt", instant(row.getTimestamp("submittedAt")));
                    return item;
                },
                arguments.toArray());
    }

    private Map<String, Object> latestSubmission(
            int studentId,
            int homeworkId) {
        return jdbcTemplate.query(
                """
                SELECT id, homeworkId, studentId, attemptNo, score,
                       maxScore, scoreRate, status, submittedAt
                FROM student_homework_submission
                WHERE studentId = ? AND homeworkId = ?
                ORDER BY attemptNo DESC LIMIT 1
                """,
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.getLong("id"));
                    item.put("homeworkId", row.getInt("homeworkId"));
                    item.put("studentId", row.getInt("studentId"));
                    item.put("attemptNo", row.getInt("attemptNo"));
                    item.put("score", row.getInt("score"));
                    item.put("maxScore", row.getInt("maxScore"));
                    item.put("scoreRate", row.getBigDecimal("scoreRate"));
                    item.put("status", row.getString("status"));
                    item.put("submittedAt",
                            instant(row.getTimestamp("submittedAt")));
                    return item;
                },
                studentId,
                homeworkId).stream().findFirst().orElseThrow(
                        () -> new PlatformException(
                                HttpStatus.NOT_FOUND,
                                "SUBMISSION_NOT_FOUND",
                                "还没有提交记录。"));
    }

    private int nextAttemptNo(int studentId, int homeworkId) {
        Integer max = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(MAX(attemptNo), 0)
                FROM student_homework_submission
                WHERE studentId = ? AND homeworkId = ?
                """,
                Integer.class,
                studentId,
                homeworkId);
        return (max == null ? 0 : max) + 1;
    }

    private boolean answersEqual(
            String type,
            List<Map<String, Object>> options,
            String selected,
            String correct) {
        String left = normalizedComparison(type, options, selected);
        String right = normalizedComparison(type, options, correct);
        return !left.isBlank() && left.equals(right);
    }

    private String normalizeAnswer(
            String type,
            List<Map<String, Object>> options,
            String answer) {
        if (answer == null || answer.isBlank()) return "未作答";
        if ("TRUE_FALSE".equals(type)) {
            String normalized = answer.trim().toUpperCase(Locale.ROOT);
            return normalized.equals("TRUE")
                            || normalized.equals("正确")
                            || normalized.equals("对")
                    ? "正确"
                    : "错误";
        }
        Map<String, Object> option = findOption(options, answer);
        if (option != null) {
            return value(option.get("key"))
                    + ". "
                    + value(option.get("text"));
        }
        return answer.trim();
    }

    private String normalizedComparison(
            String type,
            List<Map<String, Object>> options,
            String answer) {
        if (answer == null || answer.isBlank()) return "";
        if ("TRUE_FALSE".equals(type)) {
            String normalized = answer.trim().toUpperCase(Locale.ROOT);
            return normalized.equals("TRUE")
                            || normalized.equals("正确")
                            || normalized.equals("对")
                    ? "TRUE"
                    : "FALSE";
        }
        Map<String, Object> option = findOption(options, answer);
        return option == null
                ? answer.trim()
                : value(option.get("key"));
    }

    private Map<String, Object> findOption(
            List<Map<String, Object>> options,
            String answer) {
        String normalized = answer.trim();
        for (Map<String, Object> option : options) {
            String key = value(option.get("key"));
            String text = value(option.get("text"));
            if (normalized.equalsIgnoreCase(key)
                    || normalized.equals(text)
                    || normalized.equalsIgnoreCase(key + ". " + text)) {
                return option;
            }
        }
        return null;
    }

    private List<Map<String, Object>> parseOptions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception error) {
            return List.of();
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

    private Map<String, Object> answerMap(ResultSet row)
            throws SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getLong("id"));
        item.put("questionId", row.getInt("questionId"));
        item.put("seqNo", row.getInt("seqNo"));
        item.put("questionType", row.getString("questionType"));
        item.put("stem", row.getString("questionStem"));
        item.put("selectedAnswer", row.getString("selectedAnswer"));
        item.put("correctAnswer", row.getString("correctAnswer"));
        item.put("teacherAnalysis", row.getString("teacherAnalysis"));
        item.put("isCorrect", row.getBoolean("isCorrect"));
        item.put("awardedScore", row.getBigDecimal("awardedScore"));
        item.put("maxScore", row.getBigDecimal("maxScore"));
        return item;
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String instant(java.sql.Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }

    private PlatformException validation(String message) {
        return new PlatformException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_ERROR",
                message);
    }

    private record GradedAnswer(
            int questionId,
            int seqNo,
            String questionType,
            String stem,
            String selectedAnswer,
            String correctAnswer,
            String teacherAnalysis,
            boolean correct,
            int awardedScore,
            int maxScore) {
        Map<String, Object> toMap() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questionId", questionId);
            item.put("seqNo", seqNo);
            item.put("questionType", questionType);
            item.put("stem", stem);
            item.put("selectedAnswer", selectedAnswer);
            item.put("correctAnswer", correctAnswer);
            item.put("teacherAnalysis", teacherAnalysis);
            item.put("isCorrect", correct);
            item.put("awardedScore", awardedScore);
            item.put("maxScore", maxScore);
            return item;
        }
    }
}
