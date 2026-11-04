package com.zuoye.mark.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health/database")
public class DatabaseHealthController {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public Map<String, Object> databaseHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Integer ping = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            Integer questionCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM question WHERE isDeleted = 0",
                    Integer.class);
            result.put("status", ping != null && ping == 1 ? "up" : "down");
            result.put("database", "mark");
            result.put("questionCount", questionCount);
            result.put("checkedAt", Instant.now().toString());
        } catch (Exception error) {
            result.put("status", "down");
            result.put("error", error.getMessage());
            result.put("checkedAt", Instant.now().toString());
        }
        return result;
    }
}
