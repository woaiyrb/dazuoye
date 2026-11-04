package com.zuoye.mark.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.zuoye.mark.service.AiQuestionPlatformService;
import com.zuoye.mark.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/homeworks/{homeworkId}/ai-questions")
public class AiQuestionController {

    private final SessionService sessions;
    private final AiQuestionPlatformService service;

    public AiQuestionController(
            SessionService sessions,
            AiQuestionPlatformService service) {
        this.sessions = sessions;
        this.service = service;
    }

    @PostMapping("/generate")
    public Map<String, Object> generate(
            @PathVariable int homeworkId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.generate(
                sessions.required(request, "TEACHER"),
                homeworkId,
                body);
    }

    @PostMapping("/import")
    public Map<String, Object> importDrafts(
            @PathVariable int homeworkId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.importDrafts(
                sessions.required(request, "TEACHER"),
                homeworkId,
                body);
    }
}
