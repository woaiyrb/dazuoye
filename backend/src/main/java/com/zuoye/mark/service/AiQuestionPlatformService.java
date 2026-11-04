package com.zuoye.mark.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiQuestionPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DeepTutorClient deepTutor;

    public AiQuestionPlatformService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            DeepTutorClient deepTutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.deepTutor = deepTutor;
    }

    public Map<String, Object> generate(
            SessionService.PlatformUser teacher,
            int homeworkId,
            JsonNode body) {
        requireHomework(teacher.id(), homeworkId);
        String topic = text(body, "topic", "").trim();
        if (topic.isBlank()) {
            throw validation("请输入出题主题。");
        }
        int singleCount = clamp(integer(body, "singleChoiceCount", 0), 0, 10);
        int trueFalseCount = clamp(integer(body, "trueFalseCount", 0), 0, 10);
        if (singleCount + trueFalseCount == 0) {
            throw validation("至少选择一种题型并填写生成数量。");
        }
        String difficulty = normalizeDifficulty(
                text(body, "difficulty", "medium"));
        List<Integer> knowledgePointIds = integers(
                body.path("knowledgePointIds"));
        String knowledgePointNames = knowledgePointNames(knowledgePointIds);
        String generationTopic = knowledgePointNames.isBlank()
                ? topic
                : topic + "\n关联知识点：" + knowledgePointNames;

        QuestionGeneratorService.GeneratedBatch batch;
        try {
            List<QuestionGeneratorService.GeneratedQuestion> direct =
                    generateDirect(
                            generationTopic,
                            text(body, "learningObjectives", ""),
                            text(body, "materials", ""),
                            difficulty,
                            singleCount,
                            trueFalseCount);
            batch = new QuestionGeneratorService.GeneratedBatch(
                    singleCount + trueFalseCount,
                    direct.size(),
                    objectMapper.createObjectNode(),
                    direct);
        } catch (Exception error) {
            throw new PlatformException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DEEPTUTOR_GENERATION_FAILED",
                    "DeepTutor 出题失败，请稍后重试或手工添加题目。",
                    error);
        }

        List<Map<String, Object>> drafts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (QuestionGeneratorService.GeneratedQuestion question
                : batch.questions()) {
            Map<String, Object> normalized = normalizeQuestion(question);
            long draftId = saveDraft(
                    homeworkId,
                    teacher.id(),
                    topic,
                    difficulty,
                    normalized,
                    knowledgePointIds,
                    batch.rawMetadata() == null
                            ? null
                            : batch.rawMetadata().toString());
            drafts.add(draftById(draftId));
        }
        if (drafts.size() < batch.requestedCount()) {
            warnings.add("DeepTutor 返回 "
                    + drafts.size()
                    + " 道可用题目，少于请求的 "
                    + batch.requestedCount()
                    + " 道，可再次生成补充。");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("drafts", drafts);
        result.put("warnings", warnings);
        result.put("requestedCount", batch.requestedCount());
        result.put("generatedCount", drafts.size());
        return result;
    }

    private List<QuestionGeneratorService.GeneratedQuestion> generateDirect(
            String topic,
            String objectives,
            String materials,
            String difficulty,
            int singleCount,
            int trueFalseCount) throws Exception {
        String response = deepTutor.chat("""
                你是初中数学教师，请使用 DeepTutor 生成可直接判分的题目。
                只返回 JSON，不要 Markdown，不要解释。格式：
                {
                  "questions": [
                    {
                      "questionType": "SINGLE_CHOICE 或 TRUE_FALSE",
                      "stem": "题干",
                      "options": [{"key":"A","text":"选项"}],
                      "standardAnswer": "正确选项 key；判断题填 TRUE 或 FALSE",
                      "teacherAnalysis": "面向教师的解析",
                      "score": 1
                    }
                  ]
                }

                要求：
                - 只生成初中阶段的数学题，不使用高中或大学知识。
                - 单选题必须有 4 个互不重复的选项，且只有一个正确答案。
                - 判断题选项固定为“正确”“错误”。
                - 标准答案必须明确。
                - 本次生成单选题 %s 道、判断题 %s 道。

                主题：%s
                难度：%s
                学习目标：%s
                参考材料：%s
                """.formatted(
                singleCount,
                trueFalseCount,
                topic,
                difficulty,
                objectives == null ? "" : objectives,
                materials == null ? "" : materials));
        String json = response
                .replaceFirst("^\\s*```(?:json)?\\s*", "")
                .replaceFirst("\\s*```\\s*$", "");
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("DeepTutor 未返回 JSON 题目。");
        }
        JsonNode root = objectMapper.readTree(json.substring(start, end + 1));
        JsonNode entries = root.path("questions");
        if (!entries.isArray() || entries.isEmpty()) {
            throw new IllegalStateException("DeepTutor 没有返回可用题目。");
        }
        List<QuestionGeneratorService.GeneratedQuestion> questions =
                new ArrayList<>();
        for (JsonNode entry : entries) {
            String type = "TRUE_FALSE".equalsIgnoreCase(
                    entry.path("questionType").asText())
                            ? "TRUE_FALSE"
                            : "SINGLE_CORRECT";
            String stem = entry.path("stem").asText("").trim();
            if (stem.isBlank()) continue;
            String answer = entry.path("standardAnswer").asText("").trim();
            List<QuestionGeneratorService.GeneratedChoice> choices =
                    new ArrayList<>();
            boolean correctTrue = answer.equalsIgnoreCase("TRUE")
                    || answer.equals("正确")
                    || answer.equals("对");
            if ("TRUE_FALSE".equals(type)) {
                choices.add(new QuestionGeneratorService.GeneratedChoice(
                        1,
                        "正确",
                        correctTrue));
                choices.add(new QuestionGeneratorService.GeneratedChoice(
                        2,
                        "错误",
                        !correctTrue));
            } else {
                JsonNode options = entry.path("options");
                int index = 0;
                if (options.isArray()) {
                    for (JsonNode option : options) {
                        String key = option.isObject()
                                ? option.path("key").asText(
                                        String.valueOf((char) ('A' + index)))
                                : String.valueOf((char) ('A' + index));
                        String label = option.isObject()
                                ? option.path("text").asText("").trim()
                                : option.asText("").trim();
                        if (label.isBlank()) continue;
                        choices.add(new QuestionGeneratorService.GeneratedChoice(
                                index + 1,
                                label,
                                answerMatches(answer, key, label)));
                        index++;
                    }
                } else if (options.isObject()) {
                    var fields = options.fields();
                    while (fields.hasNext()) {
                        var field = fields.next();
                        String label = field.getValue().asText("").trim();
                        if (label.isBlank()) continue;
                        String key = String.valueOf((char) ('A' + index));
                        choices.add(new QuestionGeneratorService.GeneratedChoice(
                                index + 1,
                                label,
                                answerMatches(answer, field.getKey(), label)));
                        index++;
                    }
                }
                if (choices.size() < 2
                        || choices.stream().noneMatch(
                                QuestionGeneratorService.GeneratedChoice::correct)) {
                    continue;
                }
            }
            questions.add(new QuestionGeneratorService.GeneratedQuestion(
                    UUID.randomUUID().toString(),
                    stem,
                    type,
                    Math.max(1, entry.path("score").asInt(1)),
                    List.copyOf(choices),
                    answer,
                    entry.path("teacherAnalysis").asText(
                            "由 DeepTutor 生成，请教师核对。"),
                    difficulty));
            if (questions.size() >= singleCount + trueFalseCount) break;
        }
        if (questions.isEmpty()) {
            throw new IllegalStateException("DeepTutor 返回的题目格式不完整。");
        }
        return questions;
    }

    private boolean answerMatches(String answer, String key, String label) {
        String normalized = answer == null ? "" : answer.trim();
        return normalized.equalsIgnoreCase(key)
                || normalized.equalsIgnoreCase(key + ".")
                || normalized.equalsIgnoreCase(key + "、")
                || normalized.equalsIgnoreCase(key + ". " + label)
                || normalized.equalsIgnoreCase(key + "、" + label)
                || normalized.equals(label);
    }

    @Transactional
    public Map<String, Object> importDrafts(
            SessionService.PlatformUser teacher,
            int homeworkId,
            JsonNode body) {
        requireHomework(teacher.id(), homeworkId);
        List<Long> ids = longs(body.path("draftIds"));
        if (ids.isEmpty()) {
            throw validation("请选择要加入作业的 AI 题目。");
        }
        Integer maxSeq = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(MAX(seqNo), 0) FROM course_question
                WHERE homeworkId = ?
                """,
                Integer.class,
                homeworkId);
        int seqNo = (maxSeq == null ? 0 : maxSeq) + 1;
        List<Map<String, Object>> imported = new ArrayList<>();
        for (Long id : ids) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                    SELECT * FROM ai_question_draft
                    WHERE id = ? AND homeworkId = ? AND createdBy = ?
                      AND status = 'READY'
                    """,
                    id,
                    homeworkId,
                    teacher.id());
            if (rows.isEmpty()) continue;
            Map<String, Object> draft = rows.get(0);
            KeyHolder keyHolder = new GeneratedKeyHolder();
            int finalSeq = seqNo++;
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO course_question
                          (homeworkId, seqNo, questionType, stem, options,
                           standardAnswer, teacherAnalysis, score, revisionNo)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
                        """,
                        Statement.RETURN_GENERATED_KEYS);
                statement.setInt(1, homeworkId);
                statement.setInt(2, finalSeq);
                statement.setString(3, String.valueOf(draft.get("questionType")));
                statement.setString(4, String.valueOf(draft.get("stem")));
                statement.setString(5, value(draft.get("options")));
                statement.setString(6, value(draft.get("standardAnswer")));
                statement.setString(7, value(draft.get("teacherAnalysis")));
                statement.setInt(8, intValue(draft.get("score"), 1));
                return statement;
            }, keyHolder);
            int questionId = keyHolder.getKey() == null
                    ? 0
                    : keyHolder.getKey().intValue();
            attachKnowledgePoints(
                    questionId,
                    value(draft.get("knowledgePointIds")));
            jdbcTemplate.update(
                    """
                    UPDATE ai_question_draft
                    SET status = 'IMPORTED', importedQuestionId = ?
                    WHERE id = ?
                    """,
                    questionId,
                    id);
            imported.add(questionResponse(questionId));
        }
        jdbcTemplate.update(
                """
                UPDATE course_homework
                SET version = version + 1
                WHERE id = ?
                """,
                homeworkId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("importedCount", imported.size());
        result.put("questions", imported);
        return result;
    }

    private Map<String, Object> normalizeQuestion(
            QuestionGeneratorService.GeneratedQuestion question) {
        boolean trueFalse = "TRUE_FALSE".equals(question.type());
        List<Map<String, Object>> options = new ArrayList<>();
        String standardAnswer;
        if (trueFalse) {
            QuestionGeneratorService.GeneratedChoice correct = question.choices()
                    .stream()
                    .filter(QuestionGeneratorService.GeneratedChoice::correct)
                    .findFirst()
                    .orElse(null);
            boolean correctTrue = correct == null
                    || !correct.label().contains("错");
            standardAnswer = correctTrue ? "TRUE" : "FALSE";
            options.add(Map.of("key", "TRUE", "text", "正确"));
            options.add(Map.of("key", "FALSE", "text", "错误"));
        } else {
            int index = 0;
            String answerKey = "A";
            for (QuestionGeneratorService.GeneratedChoice choice
                    : question.choices()) {
                String key = String.valueOf((char) ('A' + index));
                options.add(Map.of("key", key, "text", readableMath(choice.label())));
                if (choice.correct()) answerKey = key;
                index++;
            }
            standardAnswer = answerKey;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(
                "questionType",
                trueFalse ? "TRUE_FALSE" : "SINGLE_CHOICE");
        result.put("stem", readableMath(question.question()));
        result.put("options", trueFalse ? null : options);
        result.put("standardAnswer", standardAnswer);
        result.put(
                "teacherAnalysis",
                question.explanation() == null
                        ? "由 DeepTutor 生成，请教师核对后再用于统计和讲评。"
                        : readableMath(question.explanation()));
        result.put("score", Math.max(1, question.totalPoints()));
        return result;
    }

    private long saveDraft(
            int homeworkId,
            int teacherId,
            String topic,
            String difficulty,
            Map<String, Object> question,
            List<Integer> knowledgePointIds,
            String rawResponse) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO ai_question_draft
                      (homeworkId, createdBy, topic, difficulty,
                       questionType, stem, options, standardAnswer,
                       teacherAnalysis, score, knowledgePointIds, status,
                       sourceModel, rawResponse)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'READY',
                            'DeepTutor', ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, homeworkId);
            statement.setInt(2, teacherId);
            statement.setString(3, topic);
            statement.setString(4, difficulty);
            statement.setString(5, value(question.get("questionType")));
            statement.setString(6, value(question.get("stem")));
            statement.setString(7, json(question.get("options")));
            statement.setString(8, value(question.get("standardAnswer")));
            statement.setString(9, value(question.get("teacherAnalysis")));
            statement.setInt(10, intValue(question.get("score"), 1));
            statement.setString(11, json(knowledgePointIds));
            statement.setString(12, rawResponse);
            return statement;
        }, keyHolder);
        return keyHolder.getKey() == null ? 0 : keyHolder.getKey().longValue();
    }

    private Map<String, Object> draftById(long id) {
        return jdbcTemplate.query(
                "SELECT * FROM ai_question_draft WHERE id = ?",
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.getLong("id"));
                    item.put("homeworkId", row.getInt("homeworkId"));
                    item.put("topic", row.getString("topic"));
                    item.put("difficulty", row.getString("difficulty"));
                    item.put("questionType", row.getString("questionType"));
                    item.put("stem", row.getString("stem"));
                    item.put("options", parseJson(row.getString("options")));
                    item.put("standardAnswer", row.getString("standardAnswer"));
                    item.put("teacherAnalysis", row.getString("teacherAnalysis"));
                    item.put("score", row.getInt("score"));
                    item.put(
                            "knowledgePointIds",
                            parseJson(row.getString("knowledgePointIds")));
                    item.put("status", row.getString("status"));
                    item.put("sourceModel", row.getString("sourceModel"));
                    item.put(
                            "createdAt",
                            row.getTimestamp("createdAt").toInstant().toString());
                    item.put("importedQuestionId", row.getObject("importedQuestionId"));
                    return item;
                },
                id).stream().findFirst().orElseThrow(
                        () -> new PlatformException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "AI_QUESTION_DRAFT_NOT_FOUND",
                                "AI 题目草稿保存失败。"));
    }

    private Map<String, Object> questionResponse(int questionId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT id, homeworkId, seqNo, questionType, stem, options,
                       standardAnswer, teacherAnalysis, score, revisionNo
                FROM course_question WHERE id = ?
                """,
                questionId);
        Map<String, Object> item = new LinkedHashMap<>(row);
        item.put("options", parseJson(value(row.get("options"))));
        item.put(
                "knowledgePointIds",
                jdbcTemplate.queryForList(
                        """
                        SELECT knowledgePointId FROM course_question_kp
                        WHERE questionId = ?
                        """,
                        Integer.class,
                        questionId));
        return item;
    }

    private void attachKnowledgePoints(int questionId, String json) {
        if (json == null || json.isBlank()) return;
        try {
            List<Integer> ids = objectMapper.readValue(
                    json,
                    new TypeReference<>() {});
            for (Integer id : ids) {
                try {
                    jdbcTemplate.update(
                            """
                            INSERT IGNORE INTO course_question_kp
                              (questionId, knowledgePointId)
                            VALUES (?, ?)
                            """,
                            questionId,
                            id);
                } catch (Exception ignored) {
                    // Ignore ids that were disabled or removed while previewing.
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void requireHomework(int teacherId, int homeworkId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM course_homework h
                JOIN classroom c ON c.id = h.classId
                WHERE h.id = ? AND c.teacherId = ?
                """,
                Integer.class,
                homeworkId,
                teacherId);
        if (count == null || count == 0) {
            throw new PlatformException(
                    HttpStatus.NOT_FOUND,
                    "HOMEWORK_NOT_FOUND",
                    "作业不存在。");
        }
    }

    private List<Integer> integers(JsonNode node) {
        List<Integer> values = new ArrayList<>();
        if (!node.isArray()) return values;
        for (JsonNode value : node) {
            if (value.canConvertToInt()) values.add(value.asInt());
        }
        return values;
    }

    private String knowledgePointNames(List<Integer> ids) {
        if (ids.isEmpty()) return "";
        String placeholders = String.join(
                ",",
                ids.stream().map(ignored -> "?").toList());
        List<String> names = jdbcTemplate.query(
                "SELECT name FROM kp_node WHERE id IN (" + placeholders + ")"
                        + " ORDER BY kpLevel, name",
                (row, index) -> row.getString("name"),
                ids.toArray());
        return String.join("、", names);
    }

    private List<Long> longs(JsonNode node) {
        List<Long> values = new ArrayList<>();
        if (!node.isArray()) return values;
        for (JsonNode value : node) {
            if (value.canConvertToLong()) values.add(value.asLong());
        }
        return values;
    }

    private String normalizeDifficulty(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "easy", "basic" -> "easy";
            case "hard", "challenging" -> "hard";
            default -> "medium";
        };
    }

    private String text(JsonNode body, String field, String fallback) {
        if (body == null || !body.has(field) || body.get(field).isNull()) {
            return fallback;
        }
        return body.get(field).asText(fallback);
    }

    private int integer(JsonNode body, String field, int fallback) {
        if (body == null || !body.has(field) || body.get(field).isNull()) {
            return fallback;
        }
        return body.get(field).asInt(fallback);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private String value(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String readableMath(String value) {
        if (value == null) return null;
        return value
                .replace("$", "")
                .replace("\\(", "")
                .replace("\\)", "")
                .replace("\\[", "")
                .replace("\\]", "")
                .replace("\\left", "")
                .replace("\\right", "")
                .replace("\\cdot", "×")
                .replace("\\times", "×")
                .replace("\\div", "÷")
                .replace("\\Delta", "Δ")
                .replace("\\neq", "≠")
                .replace("\\le", "≤")
                .replace("\\ge", "≥")
                .replace("\\pm", "±")
                .replaceAll(
                        "\\\\[dt]?frac\\{([^{}]+)\\}\\{([^{}]+)\\}",
                        "($1)/($2)")
                .replace("\\", "")
                .replaceAll("\\^\\{?2\\}?", "²")
                .replaceAll("\\^\\{?3\\}?", "³")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            return null;
        }
    }

    private Object parseJson(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception error) {
            return null;
        }
    }

    private PlatformException validation(String message) {
        return new PlatformException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_ERROR",
                message);
    }
}
