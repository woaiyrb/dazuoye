package com.zuoye.mark.web;

import com.zuoye.mark.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PlatformAuthController {

    private final SessionService sessionService;
    private final JdbcTemplate jdbcTemplate;

    public PlatformAuthController(
            SessionService sessionService,
            JdbcTemplate jdbcTemplate) {
        this.sessionService = sessionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/auth/session")
    public SessionService.LoginSession login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        return sessionService.login(
                body.username(),
                body.password(),
                body.portal(),
                request,
                response);
    }

    @DeleteMapping("/auth/session")
    public Map<String, Object> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        sessionService.logout(request, response);
        return Map.of("loggedOut", true);
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        SessionService.PlatformUser user = sessionService.required(request);
        return currentUser(user);
    }

    private Map<String, Object> currentUser(SessionService.PlatformUser user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", user.id());
        body.put("username", user.username());
        body.put("displayName", user.displayName());
        body.put("status", "ACTIVE".equals(user.status()) ? "ACTIVE" : "DISABLED");
        body.put("roles", List.of(user.role()));
        body.put("studentNo", user.studentNo());
        String className = null;
        if (user.classId() != null) {
            List<String> names = jdbcTemplate.query(
                    "SELECT name FROM classroom WHERE id = ?",
                    (rows, index) -> rows.getString("name"),
                    user.classId());
            if (!names.isEmpty()) className = names.get(0);
        }
        body.put("className", className);
        body.put("classNames", className == null ? List.of() : List.of(className));
        return body;
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password,
            String portal) {}
}
