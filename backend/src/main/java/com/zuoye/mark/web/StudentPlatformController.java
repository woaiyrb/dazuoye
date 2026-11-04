package com.zuoye.mark.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.zuoye.mark.service.SessionService;
import com.zuoye.mark.service.StudentPlatformService;
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
public class StudentPlatformController {

    private final SessionService sessions;
    private final StudentPlatformService service;

    public StudentPlatformController(
            SessionService sessions,
            StudentPlatformService service) {
        this.sessions = sessions;
        this.service = service;
    }

    @GetMapping("/handouts/mine")
    public List<Map<String, Object>> handouts(HttpServletRequest request) {
        return service.listHandouts(student(request));
    }

    @GetMapping("/handouts/{handoutId}")
    public Map<String, Object> handout(
            @PathVariable int handoutId,
            HttpServletRequest request) {
        return service.getHandout(student(request), handoutId);
    }

    @GetMapping("/corrections")
    public Map<String, Object> corrections(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return service.listCorrections(student(request), page, pageSize);
    }

    @PostMapping("/corrections")
    public Map<String, Object> createCorrection(
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.createCorrection(student(request), body);
    }

    @GetMapping("/doubts")
    public Map<String, Object> doubts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return service.listDoubts(student(request), page, pageSize);
    }

    @PostMapping("/doubts")
    public Map<String, Object> createDoubt(
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.createDoubt(student(request), body);
    }

    private SessionService.PlatformUser student(HttpServletRequest request) {
        return sessions.required(request, "STUDENT");
    }
}
