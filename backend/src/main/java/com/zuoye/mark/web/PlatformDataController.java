package com.zuoye.mark.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zuoye.mark.service.AssignmentReadService;
import com.zuoye.mark.service.MvpPersistenceService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/java-api")
public class PlatformDataController {

    private final AssignmentReadService assignments;
    private final MvpPersistenceService persistence;
    private final ObjectMapper objectMapper;

    public PlatformDataController(
            AssignmentReadService assignments,
            MvpPersistenceService persistence,
            ObjectMapper objectMapper) {
        this.assignments = assignments;
        this.persistence = persistence;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/user-session")
    public AssignmentReadService.UserSession session() {
        return assignments.session();
    }

    @GetMapping("/assignments")
    public List<AssignmentReadService.AssignmentData> listAssignments() {
        return assignments.listAssignments();
    }

    @GetMapping("/assignments/{assignmentId}")
    public AssignmentReadService.AssignmentData assignment(
            @PathVariable long assignmentId) {
        return assignments.getAssignment(assignmentId);
    }

    @GetMapping("/assignments/{assignmentId}/attempts")
    public List<AttemptRow> attempts(@PathVariable long assignmentId) {
        List<AttemptRow> rows = new ArrayList<>();
        for (MvpPersistenceService.StoredSubmission submission
                : persistence.findSubmissions(Long.toString(assignmentId))) {
            try {
                JsonNode result = objectMapper.readTree(submission.json());
                ObjectNode responses = objectMapper.createObjectNode();
                for (JsonNode question : result.path("questions")) {
                    ObjectNode answer = responses.putObject(
                            question.path("questionId").asText());
                    answer.put(
                            "isCorrect",
                            question.path("correct").asBoolean(false));
                    answer.put(
                            "selectedAnswer",
                            question.path("selectedAnswer").asText(""));
                    answer.put(
                            "correctAnswer",
                            question.path("correctAnswer").asText(""));
                }
                rows.add(new AttemptRow(
                        result.path("id").asText(submission.studentKey()),
                        assignmentId,
                        submission.studentKey(),
                        true,
                        result.path("scoreRate").asInt(0),
                        result.path("score").asInt(0),
                        result.path("maxScore").asInt(0),
                        result.path("submittedAt").asText(submission.updatedAt()),
                        responses));
            } catch (Exception error) {
                // Keep one malformed historical row from hiding all other data.
            }
        }
        return List.copyOf(rows);
    }

    @GetMapping("/assignments/{assignmentId}/insights")
    public InsightData insights(@PathVariable long assignmentId) {
        AssignmentReadService.AssignmentData assignment =
                assignments.getAssignment(assignmentId);
        Map<Long, int[]> counters = new LinkedHashMap<>();
        for (AttemptRow attempt : attempts(assignmentId)) {
            for (AssignmentReadService.QuestionData question
                    : assignment.questions()) {
                int[] counts = counters.computeIfAbsent(
                        question.id(),
                        ignored -> new int[2]);
                JsonNode response = attempt.responses().path(
                        Long.toString(question.id()));
                if (!response.isMissingNode()) {
                    counts[0] += 1;
                    if (response.path("isCorrect").asBoolean(false)) {
                        counts[1] += 1;
                    }
                }
            }
        }
        List<QuestionInsight> questionInsights = assignment.questions().stream()
                .map(question -> {
                    int[] counts = counters.getOrDefault(
                            question.id(),
                            new int[2]);
                    int percentage = counts[0] == 0
                            ? 0
                            : Math.round(counts[1] * 100f / counts[0]);
                    return new QuestionInsight(
                            question.id(),
                            question.question(),
                            counts[0],
                            counts[1],
                            percentage);
                })
                .toList();
        return new InsightData(
                assignment.id(),
                attempts(assignmentId).size(),
                questionInsights);
    }

    @GetMapping("/classes")
    public List<AssignmentReadService.ClassData> classes() {
        return assignments.listClasses();
    }

    @GetMapping("/knowledge-points")
    public List<AssignmentReadService.KnowledgePointData> knowledgePoints() {
        return assignments.listKnowledgePoints();
    }

    public record AttemptRow(
            String id,
            long assignmentId,
            String userId,
            boolean submitted,
            int grade,
            int score,
            int maxScore,
            String createdAt,
            ObjectNode responses) {}

    public record InsightData(
            long assignmentId,
            int submissionCount,
            List<QuestionInsight> questions) {}

    public record QuestionInsight(
            long id,
            String question,
            int responseCount,
            int correctCount,
            int correctPercentage) {}
}
