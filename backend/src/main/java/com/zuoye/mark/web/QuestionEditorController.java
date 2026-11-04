package com.zuoye.mark.web;

import com.zuoye.mark.service.QuestionEditorJdbcService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/java-api/editor/assignments/{assignmentId}")
public class QuestionEditorController {

    private final QuestionEditorJdbcService service;

    public QuestionEditorController(QuestionEditorJdbcService service) {
        this.service = service;
    }

    @GetMapping
    public QuestionEditorJdbcService.EditorAssignment assignment(
            @PathVariable long assignmentId) {
        return service.getAssignment(assignmentId);
    }

    @PostMapping("/questions")
    public QuestionEditorJdbcService.EditableQuestion create(
            @PathVariable long assignmentId,
            @Valid @RequestBody QuestionEditorJdbcService.QuestionSaveRequest body) {
        return service.create(assignmentId, body);
    }

    @PutMapping("/questions/{questionId}")
    public QuestionEditorJdbcService.EditableQuestion update(
            @PathVariable long assignmentId,
            @PathVariable long questionId,
            @Valid @RequestBody QuestionEditorJdbcService.QuestionSaveRequest body) {
        return service.update(assignmentId, questionId, body);
    }

    @DeleteMapping("/questions/{questionId}")
    public void delete(
            @PathVariable long assignmentId,
            @PathVariable long questionId) {
        service.delete(assignmentId, questionId);
    }
}
