package com.zuoye.mark.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.zuoye.mark.service.AiPlatformService;
import com.zuoye.mark.service.PlatformException;
import com.zuoye.mark.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiPlatformController {

    private final SessionService sessions;
    private final AiPlatformService service;

    public AiPlatformController(
            SessionService sessions,
            AiPlatformService service) {
        this.sessions = sessions;
        this.service = service;
    }

    @PostMapping("/draft")
    public Map<String, Object> draft(
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        SessionService.PlatformUser teacher =
                sessions.required(request, "TEACHER");
        return service.generateDraft(
                teacher,
                requiredInt(body, "homeworkId"),
                requiredInt(body, "questionId"));
    }

    @PostMapping("/explain")
    public Map<String, Object> explain() {
        throw new PlatformException(
                HttpStatus.NOT_IMPLEMENTED,
                "FEATURE_DISABLED",
                "学生自由 AI 追问未在第一版开放。");
    }

    private int requiredInt(JsonNode body, String field) {
        if (body == null || !body.has(field) || !body.get(field).canConvertToInt()) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR",
                    field + " 不能为空。");
        }
        return body.get(field).asInt();
    }
}
