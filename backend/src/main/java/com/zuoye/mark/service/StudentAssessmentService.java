package com.zuoye.mark.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentAssessmentService {

    private final AssignmentReadService assignments;
    private final MvpPersistenceService persistence;
    private final ObjectMapper objectMapper;

    public StudentAssessmentService(
            AssignmentReadService assignments,
            MvpPersistenceService persistence,
            ObjectMapper objectMapper) {
        this.assignments = assignments;
        this.persistence = persistence;
        this.objectMapper = objectMapper;
    }

    public StudentAssignment getAssignment(long assignmentId) {
        AssignmentReadService.AssignmentData assignment =
                assignments.getAssignment(assignmentId);
        List<StudentQuestion> questions = assignment.questions().stream()
                .map(question -> new StudentQuestion(
                        question.id(),
                        question.type(),
                        question.question(),
                        question.totalPoints(),
                        question.choices().stream()
                                .map(choice -> new StudentChoice(
                                        choice.id(),
                                        choice.choice()))
                                .toList()))
                .toList();
        return new StudentAssignment(
                assignment.id(),
                assignment.name(),
                assignment.introduction(),
                assignment.instructions(),
                assignment.timeEstimateMinutes(),
                assignment.passingGrade(),
                questions);
    }

    public SubmissionResult submit(
            long assignmentId,
            String studentKey,
            Map<String, String> answers) {
        AssignmentReadService.AssignmentData assignment =
                assignments.getAssignment(assignmentId);
        List<GradedQuestion> graded = new ArrayList<>();
        int score = 0;
        int maxScore = 0;
        for (AssignmentReadService.QuestionData question
                : assignment.questions()) {
            maxScore += question.totalPoints();
            String selectedValue = answers.get(Long.toString(question.id()));
            AssignmentReadService.ChoiceData selected = findChoice(
                    question.choices(),
                    selectedValue);
            AssignmentReadService.ChoiceData correct = findCorrectChoice(
                    question.choices());
            boolean isCorrect = selected != null
                    && correct != null
                    && selected.id() == correct.id();
            int awarded = isCorrect ? question.totalPoints() : 0;
            score += awarded;
            graded.add(new GradedQuestion(
                    question.id(),
                    question.question(),
                    question.type(),
                    question.totalPoints(),
                    selected == null ? "未作答" : selected.choice(),
                    correct == null ? "暂无标准答案" : correct.choice(),
                    isCorrect,
                    awarded));
        }
        int rate = maxScore == 0 ? 0 : Math.round(score * 100f / maxScore);
        SubmissionResult result = new SubmissionResult(
                UUID.randomUUID().toString(),
                assignment.id(),
                assignment.name(),
                score,
                maxScore,
                rate,
                rate >= assignment.passingGrade(),
                Instant.now().toString(),
                List.copyOf(graded));
        try {
            persistence.saveSubmission(
                    normalizeStudentKey(studentKey),
                    Long.toString(assignmentId),
                    objectMapper.writeValueAsString(result));
        } catch (Exception error) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "作答结果保存失败。",
                    error);
        }
        return result;
    }

    public SubmissionResult getResult(long assignmentId, String studentKey) {
        String json = persistence.findSubmission(
                normalizeStudentKey(studentKey),
                Long.toString(assignmentId)).orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "还没有提交记录。"));
        try {
            return objectMapper.readValue(json, SubmissionResult.class);
        } catch (Exception error) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "作答记录格式错误。",
                    error);
        }
    }

    public CorrectionRecord submitCorrection(
            long assignmentId,
            String studentKey,
            CorrectionRequest request) {
        AssignmentReadService.AssignmentData assignment =
                assignments.getAssignment(assignmentId);
        AssignmentReadService.QuestionData target = assignment.questions()
                .stream()
                .filter(question -> question.id() == request.questionId())
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "题目不存在。"));
        AssignmentReadService.ChoiceData selected = target.choices().stream()
                .filter(choice -> choice.id() == request.choiceId())
                .findFirst()
                .orElse(null);
        AssignmentReadService.ChoiceData correct = findCorrectChoice(
                target.choices());
        boolean isCorrect = selected != null
                && correct != null
                && selected.id() == correct.id();
        CorrectionRecord record = new CorrectionRecord(
                UUID.randomUUID().toString(),
                Long.toString(assignmentId),
                request.questionId(),
                target.question(),
                selected == null ? "未选择" : selected.choice(),
                correct == null ? "暂无标准答案" : correct.choice(),
                isCorrect,
                request.explanation() == null ? "" : request.explanation().trim(),
                "SUBMITTED",
                Instant.now().toString());
        saveCorrection(record, normalizeStudentKey(studentKey));
        return record;
    }

    public List<CorrectionRecord> getCorrections(long assignmentId) {
        return persistence.findCorrections(Long.toString(assignmentId)).stream()
                .map(stored -> {
                    try {
                        return objectMapper.readValue(
                                stored.json(),
                                CorrectionRecord.class);
                    } catch (Exception error) {
                        throw new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "订正记录格式错误。",
                                error);
                    }
                })
                .toList();
    }

    public CorrectionRecord confirmCorrection(String correctionId) {
        MvpPersistenceService.StoredCorrection stored = persistence
                .findCorrection(correctionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "订正记录不存在。"));
        try {
            CorrectionRecord current = objectMapper.readValue(
                    stored.json(),
                    CorrectionRecord.class);
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
            saveCorrection(confirmed, stored.studentKey());
            return confirmed;
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "订正确认失败。",
                    error);
        }
    }

    private void saveCorrection(CorrectionRecord record, String studentKey) {
        try {
            persistence.saveCorrection(
                    record.id(),
                    studentKey,
                    record.assignmentId(),
                    objectMapper.writeValueAsString(record),
                    record.status());
        } catch (Exception error) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "订正记录保存失败。",
                    error);
        }
    }

    private AssignmentReadService.ChoiceData findChoice(
            List<AssignmentReadService.ChoiceData> choices,
            String value) {
        if (value == null || value.isBlank()) return null;
        try {
            long id = Long.parseLong(value);
            return choices.stream()
                    .filter(choice -> choice.id() == id)
                    .findFirst()
                    .orElse(null);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private AssignmentReadService.ChoiceData findCorrectChoice(
            List<AssignmentReadService.ChoiceData> choices) {
        return choices.stream()
                .filter(AssignmentReadService.ChoiceData::isCorrect)
                .findFirst()
                .orElse(null);
    }

    private String normalizeStudentKey(String value) {
        if (value == null || value.isBlank()) return "demo-student";
        String normalized = value.trim()
                .replaceAll("[^A-Za-z0-9._@-]", "-");
        return normalized.length() > 96
                ? normalized.substring(0, 96)
                : normalized;
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
