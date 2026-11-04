package com.zuoye.mark.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.zuoye.mark.service.ResearchPlatformService;
import com.zuoye.mark.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/research")
public class ResearchPlatformController {

    private final SessionService sessions;
    private final ResearchPlatformService service;

    public ResearchPlatformController(
            SessionService sessions,
            ResearchPlatformService service) {
        this.sessions = sessions;
        this.service = service;
    }

    @GetMapping("/knowledge-points")
    public List<Map<String, Object>> knowledgePoints(
            @RequestParam(defaultValue = "false") boolean includeDisabled,
            HttpServletRequest request) {
        research(request);
        return service.knowledgePoints(includeDisabled);
    }

    @PostMapping("/knowledge-points")
    public Map<String, Object> createKnowledgePoint(
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.createKnowledgePoint(research(request).id(), body);
    }

    @PutMapping("/knowledge-points/{knowledgePointId}")
    public Map<String, Object> updateKnowledgePoint(
            @PathVariable int knowledgePointId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.updateKnowledgePoint(
                research(request).id(),
                knowledgePointId,
                body);
    }

    @PostMapping("/knowledge-points/{knowledgePointId}/disable")
    public Map<String, Object> disableKnowledgePoint(
            @PathVariable int knowledgePointId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.disableKnowledgePoint(
                research(request).id(),
                knowledgePointId,
                body);
    }

    @GetMapping("/templates")
    public Map<String, Object> templates(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        research(request);
        return service.templates(status, page, pageSize);
    }

    @PostMapping("/templates/{templateId}/review")
    public Map<String, Object> reviewTemplate(
            @PathVariable int templateId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.reviewTemplate(
                research(request).id(),
                templateId,
                body);
    }

    @GetMapping("/operation-logs")
    public Map<String, Object> operationLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Integer actorId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        research(request);
        return service.operationLogs(
                action,
                targetType,
                actorId,
                page,
                pageSize);
    }

    private SessionService.PlatformUser research(HttpServletRequest request) {
        return sessions.required(request, "RESEARCH");
    }
}
