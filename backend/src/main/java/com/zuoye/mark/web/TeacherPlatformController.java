package com.zuoye.mark.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.zuoye.mark.service.SessionService;
import com.zuoye.mark.service.TabularImportParser;
import com.zuoye.mark.service.TeacherPlatformService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/teacher")
public class TeacherPlatformController {

    private final SessionService sessions;
    private final TeacherPlatformService service;
    private final TabularImportParser importParser;

    public TeacherPlatformController(
            SessionService sessions,
            TeacherPlatformService service,
            TabularImportParser importParser) {
        this.sessions = sessions;
        this.service = service;
        this.importParser = importParser;
    }

    @GetMapping("/classes")
    public List<Map<String, Object>> classes(HttpServletRequest request) {
        return service.listClasses(teacher(request).id());
    }

    @PostMapping("/classes")
    public Map<String, Object> createClass(
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.createClass(teacher(request).id(), body);
    }

    @PutMapping("/classes/{classId}")
    public Map<String, Object> updateClass(
            @PathVariable int classId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.updateClass(teacher(request).id(), classId, body);
    }

    @GetMapping("/classes/{classId}/students")
    public Map<String, Object> students(
            @PathVariable int classId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeDisabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return service.listStudents(
                teacher(request).id(),
                classId,
                keyword,
                includeDisabled,
                page,
                pageSize);
    }

    @PostMapping("/classes/{classId}/students")
    public Map<String, Object> createStudent(
            @PathVariable int classId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.createStudent(teacher(request).id(), classId, body);
    }

    @PutMapping("/classes/{classId}/students/{studentId}")
    public Map<String, Object> updateStudent(
            @PathVariable int classId,
            @PathVariable int studentId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.updateStudent(
                teacher(request).id(),
                classId,
                studentId,
                body);
    }

    @PostMapping("/classes/{classId}/students/import")
    public Map<String, Object> importStudents(
            @PathVariable int classId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.importStudents(teacher(request).id(), classId, body);
    }

    @GetMapping("/knowledge-points")
    public List<Map<String, Object>> knowledgePoints(HttpServletRequest request) {
        teacher(request);
        return service.knowledgePoints(false);
    }

    @GetMapping("/templates/approved")
    public List<Map<String, Object>> approvedTemplates(HttpServletRequest request) {
        return service.templates(teacher(request).id(), "approved");
    }

    @GetMapping("/templates/mine")
    public List<Map<String, Object>> myTemplates(HttpServletRequest request) {
        return service.templates(teacher(request).id(), "mine");
    }

    @PostMapping("/templates")
    public Map<String, Object> createTemplate(
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.createTemplate(teacher(request).id(), body);
    }

    @GetMapping("/homeworks")
    public Map<String, Object> homeworks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return service.listHomeworks(
                teacher(request).id(),
                status,
                keyword,
                page,
                pageSize);
    }

    @PostMapping("/homeworks")
    public Map<String, Object> createHomework(
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.createHomework(teacher(request).id(), body);
    }

    @GetMapping("/homeworks/{homeworkId}")
    public Map<String, Object> homework(
            @PathVariable int homeworkId,
            HttpServletRequest request) {
        return service.getHomework(teacher(request).id(), homeworkId);
    }

    @PutMapping("/homeworks/{homeworkId}")
    public Map<String, Object> updateHomework(
            @PathVariable int homeworkId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.updateHomework(teacher(request).id(), homeworkId, body);
    }

    @PostMapping("/homeworks/{homeworkId}/publish")
    public Map<String, Object> publishHomework(
            @PathVariable int homeworkId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        return service.publishHomework(
                teacher(request).id(),
                homeworkId,
                body == null ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode() : body);
    }

    @PostMapping("/homeworks/{homeworkId}/archive")
    public Map<String, Object> archiveHomework(
            @PathVariable int homeworkId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        return service.archiveHomework(
                teacher(request).id(),
                homeworkId,
                body == null ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode() : body);
    }

    @PostMapping("/homeworks/{homeworkId}/questions")
    public Map<String, Object> createQuestion(
            @PathVariable int homeworkId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.createQuestion(teacher(request).id(), homeworkId, body);
    }

    @PutMapping("/questions/{questionId}")
    public Map<String, Object> updateQuestion(
            @PathVariable int questionId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.updateQuestion(teacher(request).id(), questionId, body);
    }

    @DeleteMapping("/questions/{questionId}")
    public Map<String, Object> deleteQuestion(
            @PathVariable int questionId,
            HttpServletRequest request) {
        return service.deleteQuestion(teacher(request).id(), questionId);
    }

    @GetMapping("/homeworks/{homeworkId}/grading-batches")
    public List<Map<String, Object>> gradingBatches(
            @PathVariable int homeworkId,
            HttpServletRequest request) {
        return service.gradingBatches(teacher(request).id(), homeworkId);
    }

    @GetMapping("/homeworks/{homeworkId}/grading-records")
    public Map<String, Object> gradingRecords(
            @PathVariable int homeworkId,
            @RequestParam(required = false) Integer batchId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isCorrect,
            @RequestParam(required = false) Integer seqNo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return service.gradingRecords(
                teacher(request).id(),
                homeworkId,
                batchId,
                keyword,
                isCorrect,
                seqNo,
                page,
                pageSize);
    }

    @PostMapping("/homeworks/{homeworkId}/grading/manual")
    public Map<String, Object> importManualGrading(
            @PathVariable int homeworkId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.createManualBatch(
                teacher(request).id(),
                homeworkId,
                body);
    }

    @PostMapping(
            value = "/homeworks/{homeworkId}/grading/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importGradingFile(
            @PathVariable int homeworkId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String remark,
            @RequestParam(defaultValue = "false") boolean confirmNewBatch,
            HttpServletRequest request) {
        List<Map<String, Object>> rows = importParser.parse(file);
        return service.createImportedBatch(
                teacher(request).id(),
                homeworkId,
                rows,
                "EXCEL",
                remark);
    }

    @PutMapping("/grading-records/{recordId}")
    public Map<String, Object> reviseGradingRecord(
            @PathVariable int recordId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.reviseGradingRecord(
                teacher(request).id(),
                recordId,
                body);
    }

    @GetMapping("/homeworks/{homeworkId}/statistics")
    public Map<String, Object> statistics(
            @PathVariable int homeworkId,
            HttpServletRequest request) {
        return service.statistics(teacher(request).id(), homeworkId);
    }

    @GetMapping("/homeworks/{homeworkId}/handout")
    public Map<String, Object> handout(
            @PathVariable int homeworkId,
            HttpServletRequest request) {
        return service.getHandout(teacher(request).id(), homeworkId);
    }

    @PostMapping("/homeworks/{homeworkId}/handouts/generate")
    public Map<String, Object> generateHandout(
            @PathVariable int homeworkId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        return service.generateHandout(teacher(request).id(), homeworkId, body);
    }

    @PostMapping("/handout-items/{itemId}/confirm")
    public Map<String, Object> confirmHandoutItem(
            @PathVariable int itemId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        return service.confirmHandoutItem(
                teacher(request).id(),
                itemId,
                body == null ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode() : body);
    }

    @PostMapping("/handout-items/{itemId}/save")
    public Map<String, Object> saveHandoutItem(
            @PathVariable int itemId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.saveHandoutItem(teacher(request).id(), itemId, body);
    }

    @GetMapping("/handout-items/{itemId}/versions")
    public List<Map<String, Object>> handoutItemVersions(
            @PathVariable int itemId,
            HttpServletRequest request) {
        return service.handoutItemVersions(teacher(request).id(), itemId);
    }

    @PostMapping("/handout-items/{itemId}/exclude")
    public Map<String, Object> excludeHandoutItem(
            @PathVariable int itemId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.excludeHandoutItem(teacher(request).id(), itemId, body);
    }

    @PostMapping("/handout-items/{itemId}/regenerate")
    public Map<String, Object> regenerateHandoutItem(
            @PathVariable int itemId,
            HttpServletRequest request) {
        return service.regenerateHandoutItem(teacher(request).id(), itemId);
    }

    @PostMapping("/handouts/{handoutId}/publish")
    public Map<String, Object> publishHandout(
            @PathVariable int handoutId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        return service.publishHandout(teacher(request).id(), handoutId, body);
    }

    @PostMapping("/handouts/{handoutId}/withdraw")
    public Map<String, Object> withdrawHandout(
            @PathVariable int handoutId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        return service.withdrawHandout(teacher(request).id(), handoutId, body);
    }

    @GetMapping("/handouts/{handoutId}/logs")
    public Map<String, Object> handoutLogs(
            @PathVariable int handoutId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return service.handoutLogs(
                teacher(request).id(),
                handoutId,
                page,
                pageSize);
    }

    @GetMapping("/homeworks/{homeworkId}/corrections")
    public Map<String, Object> corrections(
            @PathVariable int homeworkId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return service.corrections(
                teacher(request).id(),
                homeworkId,
                keyword,
                page,
                pageSize);
    }

    @GetMapping("/homeworks/{homeworkId}/correction-progress")
    public Map<String, Object> correctionProgress(
            @PathVariable int homeworkId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return service.correctionProgress(
                teacher(request).id(),
                homeworkId,
                page,
                pageSize);
    }

    @GetMapping("/homeworks/{homeworkId}/correction-progress/by-question")
    public Map<String, Object> questionCorrectionProgress(
            @PathVariable int homeworkId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return service.questionCorrectionProgress(
                teacher(request).id(),
                homeworkId,
                page,
                pageSize);
    }

    @GetMapping("/doubts")
    public Map<String, Object> doubts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return service.doubts(
                teacher(request).id(),
                status,
                keyword,
                page,
                pageSize);
    }

    @PostMapping("/doubts/{doubtId}/replies")
    public Map<String, Object> replyDoubt(
            @PathVariable int doubtId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return service.replyDoubt(teacher(request).id(), doubtId, body);
    }

    private SessionService.PlatformUser teacher(HttpServletRequest request) {
        return sessions.required(request, "TEACHER");
    }
}
