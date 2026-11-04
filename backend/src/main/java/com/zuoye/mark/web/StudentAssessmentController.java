package com.zuoye.mark.web;

import com.zuoye.mark.service.StudentAssessmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/java-api/student")
public class StudentAssessmentController {

    private final StudentAssessmentService service;

    public StudentAssessmentController(StudentAssessmentService service) {
        this.service = service;
    }

    @GetMapping("/assignments/{assignmentId}/questions")
    public StudentAssessmentService.StudentAssignment questions(
            @PathVariable String assignmentId,
            HttpServletRequest request) {
        return service.getAssignment(
                Long.parseLong(assignmentId));
    }

    @PostMapping("/assignments/{assignmentId}/submit")
    public StudentAssessmentService.SubmissionResult submit(
            @PathVariable String assignmentId,
            @Valid @RequestBody SubmitRequest body,
            HttpServletRequest request) {
        return service.submit(
                Long.parseLong(assignmentId),
                studentKey(request),
                body.answers());
    }

    @GetMapping("/assignments/{assignmentId}/result")
    public StudentAssessmentService.SubmissionResult result(
            @PathVariable String assignmentId,
            HttpServletRequest request) {
        return service.getResult(
                Long.parseLong(assignmentId),
                studentKey(request));
    }

    @PostMapping("/assignments/{assignmentId}/corrections")
    public StudentAssessmentService.CorrectionRecord correct(
            @PathVariable String assignmentId,
            @Valid @RequestBody StudentAssessmentService.CorrectionRequest body,
            HttpServletRequest request) {
        return service.submitCorrection(
                Long.parseLong(assignmentId),
                studentKey(request),
                body);
    }

    @GetMapping("/assignments/{assignmentId}/corrections")
    public List<StudentAssessmentService.CorrectionRecord> corrections(
            @PathVariable String assignmentId) {
        return service.getCorrections(Long.parseLong(assignmentId));
    }

    @PatchMapping("/corrections/{correctionId}/confirm")
    public StudentAssessmentService.CorrectionRecord confirm(
            @PathVariable String correctionId) {
        return service.confirmCorrection(correctionId);
    }

    private String studentKey(HttpServletRequest request) {
        String value = request.getHeader("X-Student-Key");
        if (value == null || value.isBlank()) {
            value = request.getHeader("user-session");
        }
        return value;
    }

    public record SubmitRequest(@NotEmpty Map<String, String> answers) {}
}
