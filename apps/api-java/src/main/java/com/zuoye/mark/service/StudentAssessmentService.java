package com.zuoye.mark.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentAssessmentService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String legacyBaseUrl;
    private final Map<String, SubmissionResult> submissions = new ConcurrentHashMap<>();
    private final Map<String, CorrectionRecord> corrections = new ConcurrentHashMap<>();

    public StudentAssessmentService(
            ObjectMapper objectMapper,
            @Value("${mark.legacy-api-base-url:http://127.0.0.1:8000}")
            String legacyBaseUrl) {
        this.objectMapper = objectMapper;
        this.legacyBaseUrl = legacyBaseUrl.replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public StudentAssignment getAssignment(
            String assignmentId,
            String cookie,
            String userSession) {
        JsonNode assignment = fetchAssignment(assignmentId, cookie, userSession);
        List<StudentQuestion> questions = new ArrayList<>();
        for (JsonNode question : assignment.path("questions")) {
            List<StudentChoice> choices = new ArrayList<>();
            for (JsonNode choice : question.path("choices")) {
                choices.add(new StudentChoice(
                        choice.path("id").asLong(),
                        displayChoice(decodeText(choice.path("choice").asText("")))));
            }
            questions.add(new StudentQuestion(
                    question.path("id").asLong(),
                    question.path("type").asText(""),
                    decodeText(question.path("question").asText("")),
                    question.path("totalPoints").asInt(0),
                    choices));
        }
        return new StudentAssignment(
                assignment.path("id").asLong(),
                decodeText(assignment.path("name").asText("")),
                decodeText(assignment.path("introduction").asText("")),
                decodeText(assignment.path("instructions").asText("")),
                assignment.path("timeEstimateMinutes").asInt(0),
                assignment.path("passingGrade").asInt(60),
                questions);
    }

    public SubmissionResult submit(
            String assignmentId,
            Map<String, String> answers,
            String cookie,
            String userSession) {
        JsonNode assignment = fetchAssignment(assignmentId, cookie, userSession);
        List<GradedQuestion> graded = new ArrayList<>();
        int score = 0;
        int maxScore = 0;

        for (JsonNode question : assignment.path("questions")) {
            long questionId = question.path("id").asLong();
            int questionPoints = question.path("totalPoints").asInt(0);
            maxScore += questionPoints;
            String selectedValue = answers.get(Long.toString(questionId));
            JsonNode selected = findChoice(question.path("choices"), selectedValue);
            JsonNode correct = findCorrectChoice(question.path("choices"));
            boolean correctAnswer = selected != null && sameChoice(selected, correct);
            int awarded = correctAnswer ? questionPoints : 0;
            score += awarded;
            graded.add(new GradedQuestion(
                    questionId,
                    decodeText(question.path("question").asText("")),
                    question.path("type").asText(""),
                    questionPoints,
                    selected == null
                            ? "未作答"
                            : displayChoice(decodeText(selected.path("choice").asText(""))),
                    correct == null
                            ? "暂无标准答案"
                            : displayChoice(decodeText(correct.path("choice").asText(""))),
                    correctAnswer,
                    awarded));
        }

        int rate = maxScore == 0 ? 0 : Math.round(score * 100f / maxScore);
        SubmissionResult result = new SubmissionResult(
                UUID.randomUUID().toString(),
                assignment.path("id").asLong(),
                decodeText(assignment.path("name").asText("")),
                score,
                maxScore,
                rate,
                rate >= assignment.path("passingGrade").asInt(60),
                Instant.now().toString(),
                List.copyOf(graded));
        submissions.put(assignmentId, result);
        return result;
    }

    public SubmissionResult getResult(String assignmentId) {
        SubmissionResult result = submissions.get(assignmentId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "还没有提交记录。");
        }
        return result;
    }

    public CorrectionRecord submitCorrection(
            String assignmentId,
            CorrectionRequest request,
            String cookie,
            String userSession) {
        JsonNode assignment = fetchAssignment(assignmentId, cookie, userSession);
        JsonNode target = null;
        for (JsonNode question : assignment.path("questions")) {
            if (question.path("id").asLong() == request.questionId()) {
                target = question;
                break;
            }
        }
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "题目不存在。");
        }
        JsonNode selected = findChoice(
                target.path("choices"),
                Long.toString(request.choiceId()));
        JsonNode correct = findCorrectChoice(target.path("choices"));
        boolean isCorrect = selected != null && sameChoice(selected, correct);
        CorrectionRecord record = new CorrectionRecord(
                UUID.randomUUID().toString(),
                assignmentId,
                request.questionId(),
                decodeText(target.path("question").asText("")),
                selected == null
                        ? "未选择"
                        : displayChoice(decodeText(selected.path("choice").asText(""))),
                correct == null
                        ? "暂无标准答案"
                        : displayChoice(decodeText(correct.path("choice").asText(""))),
                isCorrect,
                request.explanation() == null ? "" : request.explanation().trim(),
                "SUBMITTED",
                Instant.now().toString());
        corrections.put(record.id(), record);
        return record;
    }

    public List<CorrectionRecord> getCorrections(String assignmentId) {
        return corrections.values().stream()
                .filter(record -> record.assignmentId().equals(assignmentId))
                .sorted((left, right) -> right.submittedAt().compareTo(left.submittedAt()))
                .toList();
    }

    public CorrectionRecord confirmCorrection(String correctionId) {
        CorrectionRecord current = corrections.get(correctionId);
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订正记录不存在。");
        }
        CorrectionRecord confirmed = new CorrectionRecord(
                current.id(),
                current.assignmentId(),
                current.questionId(),
                current.question(),
                current.selectedLabel(),
                current.correctLabel(),
                current.correct(),
                current.explanation(),
                "CONFIRMED",
                current.submittedAt());
        corrections.put(correctionId, confirmed);
        return confirmed;
    }

    private JsonNode fetchAssignment(String assignmentId, String cookie, String userSession) {
        if (!assignmentId.matches("\\d+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "作业编号无效。");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create(legacyBaseUrl + "/api/v2/assignments/" + assignmentId))
                .timeout(Duration.ofSeconds(20))
                .GET();
        if (cookie != null && !cookie.isBlank()) {
            builder.header("Cookie", cookie);
        }
        if (userSession != null && !userSession.isBlank()) {
            builder.header("user-session", userSession);
        }
        try {
            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "作业服务暂时不可用。");
            }
            return objectMapper.readTree(response.body());
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "无法读取作业数据。",
                    error);
        }
    }

    private JsonNode findChoice(JsonNode choices, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (JsonNode choice : choices) {
            if (Long.toString(choice.path("id").asLong()).equals(value)) {
                return choice;
            }
        }
        return null;
    }

    private JsonNode findCorrectChoice(JsonNode choices) {
        for (JsonNode choice : choices) {
            if (choice.path("isCorrect").asBoolean(false)) {
                return choice;
            }
        }
        return null;
    }

    private boolean sameChoice(JsonNode left, JsonNode right) {
        return left != null
                && right != null
                && left.path("id").asLong() == right.path("id").asLong();
    }

    private String decodeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value.trim());
            String text = new String(decoded, StandardCharsets.UTF_8);
            return text.contains("\u0000") ? value : text;
        } catch (IllegalArgumentException error) {
            return value;
        }
    }

    private String displayChoice(String value) {
        return switch (value) {
            case "true" -> "正确";
            case "false" -> "错误";
            default -> value;
        };
    }

    public record StudentAssignment(
            long id,
            String name,
            String introduction,
            String instructions,
            int timeEstimateMinutes,
            int passingGrade,
            List<StudentQuestion> questions) {}

    public record StudentQuestion(
            long id,
            String type,
            String question,
            int totalPoints,
            List<StudentChoice> choices) {}

    public record StudentChoice(long id, String label) {}

    public record SubmissionResult(
            String id,
            long assignmentId,
            String assignmentName,
            int score,
            int maxScore,
            int scoreRate,
            boolean passed,
            String submittedAt,
            List<GradedQuestion> questions) {}

    public record GradedQuestion(
            long questionId,
            String question,
            String type,
            int maxPoints,
            String selectedAnswer,
            String correctAnswer,
            boolean correct,
            int pointsAwarded) {}

    public record CorrectionRequest(
            long questionId,
            long choiceId,
            String explanation) {}

    public record CorrectionRecord(
            String id,
            String assignmentId,
            long questionId,
            String question,
            String selectedLabel,
            String correctLabel,
            boolean correct,
            String explanation,
            String status,
            String submittedAt) {}
}
