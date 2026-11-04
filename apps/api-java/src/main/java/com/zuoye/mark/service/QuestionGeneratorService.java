package com.zuoye.mark.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionGeneratorService {

    private final DeepTutorClient deepTutor;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String legacyBaseUrl;

    public QuestionGeneratorService(
            DeepTutorClient deepTutor,
            ObjectMapper objectMapper,
            @Value("${mark.legacy-api-base-url:http://127.0.0.1:8000}")
            String legacyBaseUrl) {
        this.deepTutor = deepTutor;
        this.objectMapper = objectMapper;
        this.legacyBaseUrl = legacyBaseUrl.replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
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
            List<GeneratedQuestion> questions,
            String cookie,
            String userSession) {
        if (questions == null || questions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有可导入的题目。");
        }
        List<Long> importedIds = new ArrayList<>();
        for (GeneratedQuestion question : questions) {
            try {
                Map<String, Object> payload = toLegacyQuestion(question);
                HttpRequest.Builder builder = HttpRequest
                        .newBuilder(URI.create(
                                legacyBaseUrl
                                        + "/api/v2/assignments/"
                                        + assignmentId
                                        + "/questions"))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                objectMapper.writeValueAsString(payload),
                                StandardCharsets.UTF_8));
                if (cookie != null && !cookie.isBlank()) {
                    builder.header("Cookie", cookie);
                }
                if (userSession != null && !userSession.isBlank()) {
                    builder.header("user-session", userSession);
                }
                HttpResponse<String> response = httpClient.send(
                        builder.build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                JsonNode body = objectMapper.readTree(response.body());
                if (response.statusCode() / 100 != 2 || !body.path("success").asBoolean(false)) {
                    throw new IllegalStateException(
                            body.path("error").asText("题目写入失败"));
                }
                if (body.has("id")) importedIds.add(body.path("id").asLong());
            } catch (Exception error) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
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

    private Map<String, Object> toLegacyQuestion(GeneratedQuestion question) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", question.question());
        payload.put("type", question.type());
        payload.put("totalPoints", question.totalPoints());
        if ("TRUE_FALSE".equals(question.type())) {
            GeneratedChoice correct = question.choices().stream()
                    .filter(GeneratedChoice::correct)
                    .findFirst()
                    .orElse(null);
            payload.put("answer", correct != null && "正确".equals(correct.label()));
        } else {
            List<Map<String, Object>> choices = new ArrayList<>();
            for (GeneratedChoice choice : question.choices()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", choice.id());
                item.put("choice", choice.label());
                item.put("isCorrect", choice.correct());
                item.put("points", choice.correct() ? question.totalPoints() : 0);
                choices.add(item);
            }
            payload.put("choices", choices);
        }
        return payload;
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
