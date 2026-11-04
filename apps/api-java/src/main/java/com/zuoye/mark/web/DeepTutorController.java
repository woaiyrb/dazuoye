package com.zuoye.mark.web;

import com.zuoye.mark.service.DeepTutorClient;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/java-api/ai")
public class DeepTutorController {

    private final DeepTutorClient deepTutor;
    private final String legacyApiBaseUrl;

    public DeepTutorController(
            DeepTutorClient deepTutor,
            @Value("${mark.legacy-api-base-url:http://127.0.0.1:8000}") String legacyApiBaseUrl) {
        this.deepTutor = deepTutor;
        this.legacyApiBaseUrl = legacyApiBaseUrl;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> readiness = deepTutor.readiness();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("javaBackend", "ok");
        result.put("javaVersion", System.getProperty("java.version"));
        result.put("legacyApi", legacyApiBaseUrl);
        result.put("deepTutorState", readiness.get("state"));
        result.put("deepTutorReady", Boolean.TRUE.equals(readiness.get("ready")));
        result.put("deepTutor", readiness);
        result.put("checkedAt", Instant.now().toString());
        return result;
    }

}
