package com.zuoye.mark.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuoye.mark.service.DeepTutorClient;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * In-memory review draft store for the first Java/Vue vertical slice.
 *
 * The production schema will persist immutable AI drafts and teacher-published
 * versions separately. This controller deliberately models that boundary now:
 * AI output starts as DRAFT and a teacher must move it to REVIEWED before it
 * can be PUBLISHED.
 */
@RestController
@RequestMapping("/java-api/reviews")
public class TeachingReviewController {

    private final Map<String, ReviewDraft> drafts = new ConcurrentHashMap<>();
    private final DeepTutorClient deepTutor;
    private final ObjectMapper objectMapper;

    public TeachingReviewController(DeepTutorClient deepTutor, ObjectMapper objectMapper) {
        this.deepTutor = deepTutor;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{assignmentId}")
    public ReviewDraft get(@PathVariable String assignmentId) {
        return drafts.computeIfAbsent(assignmentId, ignored -> initialDraft());
    }

    @PutMapping("/{assignmentId}")
    public ReviewDraft save(
            @PathVariable String assignmentId,
            @Valid @RequestBody ReviewDraft submitted) {
        String requestedStatus = normalizeStatus(submitted.status());
        ReviewDraft previous = drafts.getOrDefault(assignmentId, initialDraft());
        if ("PUBLISHED".equals(requestedStatus)
                && !"REVIEWED".equals(previous.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "讲评必须先由教师确认，才能发布。");
        }
        int nextVersion = previous.version() + 1;
        ReviewDraft saved = new ReviewDraft(
                blankToDefault(submitted.title(), previous.title()),
                blankToDefault(submitted.overview(), previous.overview()),
                blankToDefault(submitted.commonErrors(), previous.commonErrors()),
                blankToDefault(submitted.possibleReasons(), previous.possibleReasons()),
                blankToDefault(submitted.correctApproach(), previous.correctApproach()),
                blankToDefault(submitted.knowledgeSummary(), previous.knowledgeSummary()),
                blankToDefault(submitted.practiceAdvice(), previous.practiceAdvice()),
                blankToDefault(submitted.evidence(), previous.evidence()),
                requestedStatus,
                nextVersion,
                Instant.now().toString());
        drafts.put(assignmentId, saved);
        return saved;
    }

    @PostMapping("/{assignmentId}/generate")
    public ReviewDraft generate(
            @PathVariable String assignmentId,
            @RequestBody(required = false) ReviewEvidence evidence) {
        ReviewEvidence safeEvidence = evidence == null
                ? new ReviewEvidence("当前数学作业", 0, 0, "", "")
                : evidence;
        String prompt = """
                你是初中数学教师的讲评助手。请只根据以下确定性统计数据生成讲评草稿。
                禁止判断学生身份，禁止改变题目对错和得分，禁止把可能原因写成确定事实。
                只返回 JSON，字段为：
                title, overview, commonErrors, possibleReasons, correctApproach,
                knowledgeSummary, practiceAdvice, evidence。

                作业名称：%s
                题目数量：%s
                整体正确率：%s%%
                高频错误：%s
                教师材料：%s
                """.formatted(
                safeEvidence.assignmentName(),
                safeEvidence.questionCount(),
                safeEvidence.correctRate(),
                safeEvidence.commonWrongQuestions(),
                safeEvidence.teacherMaterials());
        try {
            String answer = deepTutor.chat(prompt);
            ReviewDraft previous = drafts.getOrDefault(assignmentId, initialDraft());
            ReviewDraft generated = parseGeneratedDraft(
                    answer,
                    previous.version() + 1,
                    safeEvidence);
            drafts.put(assignmentId, generated);
            return generated;
        } catch (Exception error) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 讲评生成失败，请教师手工填写后继续审核。",
                    error);
        }
    }

    private ReviewDraft initialDraft() {
        return new ReviewDraft(
                "本次作业讲评",
                "AI 已根据当前作业和作答统计准备讲评草稿，请教师核对事实和措辞。",
                "集中讲解错误率较高的题目。",
                "可能原因：概念混淆、计算符号错误或审题不完整。",
                "先明确题目条件，再根据标准答案和教师解析逐步核对。",
                "有理数加法、绝对值与一元一次方程。",
                "完成同类基础练习，并说明每一题的判断依据。",
                "依据：题目、标准答案、教师解析和匿名作答统计。",
                "DRAFT",
                1,
                Instant.now().toString());
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "DRAFT" : status.trim().toUpperCase();
        if ("DRAFT".equals(normalized)
                || "REVIEWED".equals(normalized)
                || "PUBLISHED".equals(normalized)) {
            return normalized;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的讲评状态。");
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private ReviewDraft parseGeneratedDraft(
            String answer,
            int version,
            ReviewEvidence evidence) {
        try {
            String json = answer
                    .replaceFirst("^\\s*```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```\\s*$", "");
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start < 0 || end <= start) {
                throw new IllegalArgumentException("AI response did not contain JSON");
            }
            JsonNode node = objectMapper.readTree(json.substring(start, end + 1));
            return new ReviewDraft(
                    text(node, "title", evidence.assignmentName() + "讲评"),
                    text(node, "overview", "AI 已生成讲评草稿，请教师核对。"),
                    text(node, "commonErrors", evidence.commonWrongQuestions()),
                    text(node, "possibleReasons", "可能原因需要教师结合课堂情况进一步确认。"),
                    text(node, "correctApproach", "根据标准答案和教师解析核对解题步骤。"),
                    text(node, "knowledgeSummary", evidence.teacherMaterials()),
                    text(node, "practiceAdvice", "完成同类基础练习并说明判断依据。"),
                    text(
                            node,
                            "evidence",
                            "依据：题目、标准答案、教师材料和匿名作答统计。"),
                    "DRAFT",
                    version,
                    Instant.now().toString());
        } catch (Exception error) {
            return new ReviewDraft(
                    evidence.assignmentName() + "讲评",
                    answer,
                    evidence.commonWrongQuestions(),
                    "可能原因需要教师结合课堂情况进一步确认。",
                    "根据标准答案和教师解析核对解题步骤。",
                    evidence.teacherMaterials(),
                    "完成同类基础练习并说明判断依据。",
                    "依据：题目、标准答案、教师材料和匿名作答统计。",
                    "DRAFT",
                    version,
                    Instant.now().toString());
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("");
        return value.isBlank() ? fallback : value.trim();
    }

    public record ReviewDraft(
            String title,
            String overview,
            String commonErrors,
            String possibleReasons,
            String correctApproach,
            String knowledgeSummary,
            String practiceAdvice,
            String evidence,
            String status,
            int version,
            String updatedAt) {}

    public record ReviewEvidence(
            String assignmentName,
            int questionCount,
            int correctRate,
            String commonWrongQuestions,
            String teacherMaterials) {}
}
