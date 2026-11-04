package com.zuoye.mark.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.zuoye.mark.service.SessionService;
import com.zuoye.mark.service.StudentHomeworkAssessmentService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
public class StudentHomeworkAssessmentController {

    private final SessionService sessions;
    private final StudentHomeworkAssessmentService service;

    public StudentHomeworkAssessmentController(
            SessionService sessions,
            StudentHomeworkAssessmentService service) {
        this.sessions = sessions;
        this.service = service;
    }

    @GetMapping("/homeworks")
    public List<Map<String, Object>> homeworks(HttpServletRequest request) {
        return service.listHomeworks(student(request));
    }

    @GetMapping("/homeworks/{homeworkId}")
    public Map<String, Object> homework(
            @PathVariable int homeworkId,
            HttpServletRequest request) {
        return service.homeworkDetail(student(request), homeworkId);
    }

    @PostMapping("/homeworks/{homeworkId}/submit")
    public Map<String, Object> submit(
            @PathVariable int homeworkId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.submit(student(request), homeworkId, body);
    }

    @GetMapping("/homeworks/{homeworkId}/result")
    public Map<String, Object> result(
            @PathVariable int homeworkId,
            HttpServletRequest request) {
        return service.latestResult(student(request), homeworkId);
    }

    @GetMapping("/score-summary")
    public Map<String, Object> scoreSummary(HttpServletRequest request) {
        return service.scoreSummary(student(request));
    }

    @GetMapping("/wrong-book")
    public List<Map<String, Object>> wrongBook(
            @RequestParam(required = false) Integer homeworkId,
            HttpServletRequest request) {
        return service.wrongBook(student(request), homeworkId);
    }

    private SessionService.PlatformUser student(HttpServletRequest request) {
        return sessions.required(request, "STUDENT");
    }
}
