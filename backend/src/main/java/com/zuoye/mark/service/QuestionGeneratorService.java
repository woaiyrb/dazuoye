package com.zuoye.mark.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionGeneratorService {

    private final DeepTutorClient deepTutor;
    private final ObjectMapper objectMapper;
    private final QuestionEditorJdbcService editorService;

    public QuestionGeneratorService(
            DeepTutorClient deepTutor,
            ObjectMapper objectMapper,
            QuestionEditorJdbcService editorService) {
        this.deepTutor = deepTutor;
        this.objectMapper = objectMapper;
        this.editorService = editorService;
    }

    public GeneratedBatch generate(GenerateRequest request) {
        int singleChoice = Math.max(0, Math.min(50, request.singleChoiceCount()));
        int trueFalse = Math.max(0, Math.min(50, request.trueFalseCount()));
        int total = singleChoice + trueFalse;
        if (total == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "至少选择一种题型并填写生成数量。");
        }
        if (request.topic() == null || request.topic().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入出题主题。");
        }

        String topic = composeTopic(request);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("mode", "custom");
        config.put("topic", topic);
        config.put("difficulty", normalizeDifficulty(request.difficulty()));

        try {
            List<GeneratedQuestion> questions = new ArrayList<>();
            List<JsonNode> batches = new ArrayList<>();
            if (singleChoice > 0) {
                questions.addAll(generateType(
                        topic,
                        config,
                        "choice",
                        singleChoice,
                        batches));
            }
            if (trueFalse > 0) {
                questions.addAll(generateType(
                        topic,
                        config,
                        "concept",
                        trueFalse,
                        batches));
            }
            if (questions.isEmpty()) {
                throw new IllegalStateException("DeepTutor 未返回可用题目。");
            }
            var metadata = objectMapper.createObjectNode();
            var metadataBatches = metadata.putArray("batches");
            batches.forEach(metadataBatches::add);
            return new GeneratedBatch(
                    total,
                    questions.size(),
                    metadata,
                    List.copyOf(questions));
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DeepTutor 批量出题失败，请检查 AI 服务后重试。",
                    error);
        }
    }

    private List<GeneratedQuestion> generateType(
            String topic,
            Map<String, Object> baseConfig,
            String deepTutorType,
            int count,
            List<JsonNode> batches) throws Exception {
        Map<String, Object> config = new LinkedHashMap<>(baseConfig);
        config.put("num_questions", count);
        config.put("question_types", List.of(deepTutorType));
        DeepTutorClient.TurnResult result = deepTutor.complete(
                "deep_question",
                config,
                topic);
        batches.add(result.metadata().deepCopy());
        JsonNode entries = result.metadata().path("summary").path("results");
        List<GeneratedQuestion> questions = new ArrayList<>();
        for (JsonNode entry : entries) {
            JsonNode qa = entry.path("qa_pair");
            if (!qa.isObject()) continue;
            GeneratedQuestion question = toGeneratedQuestion(qa);
            boolean requestedChoice = "choice".equals(deepTutorType);
            if (requestedChoice && !"SINGLE_CORRECT".equals(question.type())) {
                continue;
            }
            if (!requestedChoice && !"TRUE_FALSE".equals(question.type())) {
                continue;
            }
            if ("SINGLE_CORRECT".equals(question.type())
                    && question.choices().stream()
                            .noneMatch(GeneratedChoice::correct)) {
                continue;
            }
            questions.add(question);
            if (questions.size() >= count) break;
        }
        return questions;
    }

    public ImportResult importQuestions(
            String assignmentId,
            List<GeneratedQuestion> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有可导入的题目。");
        }
        long targetAssignmentId;
        try {
            targetAssignmentId = Long.parseLong(assignmentId);
        } catch (NumberFormatException error) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "作业编号无效。",
                    error);
        }
        List<Long> importedIds = new ArrayList<>();
        for (GeneratedQuestion question : questions) {
            try {
                QuestionEditorJdbcService.EditableQuestion created =
                        editorService.create(
                                targetAssignmentId,
                                toEditorQuestion(question));
                importedIds.add(created.id());
            } catch (Exception error) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "题目已生成，但写入作业失败：" + error.getMessage(),
                        error);
            }
        }
        return new ImportResult(importedIds.size(), List.copyOf(importedIds));
    }

    private GeneratedQuestion toGeneratedQuestion(JsonNode qa) {
        String qaType = qa.path("question_type").asText("").toLowerCase(Locale.ROOT);
        JsonNode options = qa.path("options");
        boolean isChoice = "choice".equals(qaType) || (options.isObject() && options.size() > 0);
        String correctAnswer = qa.path("correct_answer").asText("");
        List<GeneratedChoice> choices = new ArrayList<>();

        if (isChoice) {
            int index = 1;
            for (Map.Entry<String, JsonNode> option : iterable(options.fields())) {
                String key = option.getKey();
                choices.add(new GeneratedChoice(
                        index++,
                        option.getValue().asText(""),
                        isCorrectChoice(key, correctAnswer)));
            }
            if (choices.stream().noneMatch(GeneratedChoice::correct)) {
                for (int indexChoice = 0; indexChoice < choices.size(); indexChoice++) {
                    GeneratedChoice choice = choices.get(indexChoice);
                    if (correctAnswer.contains(choice.label())
                            || correctAnswer.equalsIgnoreCase(String.valueOf((char) ('A' + indexChoice)))) {
                        choices.set(
                                indexChoice,
                                new GeneratedChoice(choice.id(), choice.label(), true));
                        break;
                    }
                }
            }
        } else {
            boolean correctTrue = correctAnswer.toLowerCase(Locale.ROOT).contains("true")
                    || correctAnswer.contains("正确")
                    || correctAnswer.equals("对");
            choices.add(new GeneratedChoice(1, "正确", correctTrue));
            choices.add(new GeneratedChoice(2, "错误", !correctTrue));
        }

        return new GeneratedQuestion(
                UUID.randomUUID().toString(),
                qa.path("question").asText(""),
                isChoice ? "SINGLE_CORRECT" : "TRUE_FALSE",
                1,
                List.copyOf(choices),
                correctAnswer,
                qa.path("explanation").asText(""),
                qa.path("difficulty").asText(""));
    }

    private Iterable<Map.Entry<String, JsonNode>> iterable(
            java.util.Iterator<Map.Entry<String, JsonNode>> iterator) {
        return () -> iterator;
    }

    private boolean isCorrectChoice(String key, String answer) {
        return key.equalsIgnoreCase(answer)
                || answer.trim().toUpperCase(Locale.ROOT).startsWith(
                        key.trim().toUpperCase(Locale.ROOT) + ".")
                || answer.trim().toUpperCase(Locale.ROOT).startsWith(
                        key.trim().toUpperCase(Locale.ROOT) + "、");
    }

    private QuestionEditorJdbcService.QuestionSaveRequest toEditorQuestion(
            GeneratedQuestion question) {
        List<QuestionEditorJdbcService.EditableChoice> choices = new ArrayList<>();
        for (GeneratedChoice choice : question.choices()) {
            choices.add(new QuestionEditorJdbcService.EditableChoice(
                    choice.id(),
                    normalizeChoiceLabel(choice.label()),
                    choice.correct(),
                    choice.correct() ? question.totalPoints() : 0,
                    question.explanation()));
        }
        return new QuestionEditorJdbcService.QuestionSaveRequest(
                question.type(),
                question.question(),
                question.totalPoints(),
                question.explanation(),
                List.copyOf(choices),
                false);
    }

    private String normalizeChoiceLabel(String value) {
        return switch (value) {
            case "true" -> "正确";
            case "false" -> "错误";
            default -> value;
        };
    }

    private String composeTopic(GenerateRequest request) {
        List<String> parts = new ArrayList<>();
        parts.add("主题：" + request.topic().trim());
        if (request.learningObjectives() != null
                && !request.learningObjectives().isBlank()) {
            parts.add("学习目标：" + request.learningObjectives().trim());
        }
        if (request.materials() != null && !request.materials().isBlank()) {
            parts.add("参考材料：" + request.materials().trim());
        }
        parts.add("只生成初中数学单选题和判断题，答案必须明确。");
        return String.join("\n", parts);
    }

    private String normalizeDifficulty(String difficulty) {
        String value = difficulty == null ? "medium" : difficulty.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "easy", "basic" -> "easy";
            case "hard", "challenging" -> "hard";
            default -> "medium";
        };
    }

    public record GenerateRequest(
            String topic,
            String learningObjectives,
            String materials,
            String difficulty,
            int singleChoiceCount,
            int trueFalseCount) {}

    public record GeneratedBatch(
            int requestedCount,
            int generatedCount,
            JsonNode rawMetadata,
            List<GeneratedQuestion> questions) {}

    public record GeneratedQuestion(
            String id,
            String question,
            String type,
            int totalPoints,
            List<GeneratedChoice> choices,
            String correctAnswer,
            String explanation,
            String difficulty) {}

    public record GeneratedChoice(int id, String label, boolean correct) {}

    public record ImportResult(int importedCount, List<Long> questionIds) {}
}
