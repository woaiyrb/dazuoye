package com.zuoye.mark.web;

import com.zuoye.mark.service.QuestionGeneratorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/java-api/generator")
public class QuestionGeneratorController {

    private final QuestionGeneratorService service;

    public QuestionGeneratorController(QuestionGeneratorService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    public QuestionGeneratorService.GeneratedBatch generate(
            @Valid @RequestBody GenerateRequest body) {
        return service.generate(new QuestionGeneratorService.GenerateRequest(
                body.topic(),
                body.learningObjectives(),
                body.materials(),
                body.difficulty(),
                body.singleChoiceCount(),
                body.trueFalseCount()));
    }

    @PostMapping("/assignments/{assignmentId}/import")
    public QuestionGeneratorService.ImportResult importQuestions(
            @PathVariable String assignmentId,
            @RequestBody List<QuestionGeneratorService.GeneratedQuestion> questions) {
        return service.importQuestions(
                assignmentId,
                questions);
    }

    public record GenerateRequest(
            @NotBlank String topic,
            String learningObjectives,
            String materials,
            String difficulty,
            int singleChoiceCount,
            int trueFalseCount) {}
}
