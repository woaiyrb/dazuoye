package com.zuoye.mark.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuoye.mark.mapper.TeacherWorkflowMapper;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PasswordService passwordService;
    private final DeepTutorClient deepTutor;
    private final CommentaryTextService commentaryText;
    private final TeacherWorkflowMapper workflowMapper;

    public TeacherPlatformService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PasswordService passwordService,
            DeepTutorClient deepTutor,
            CommentaryTextService commentaryText,
            TeacherWorkflowMapper workflowMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.passwordService = passwordService;
        this.deepTutor = deepTutor;
        this.commentaryText = commentaryText;
        this.workflowMapper = workflowMapper;
    }

    public List<Map<String, Object>> listClasses(int teacherId) {
        return jdbcTemplate.query(
                """
                SELECT c.id, c.name, c.grade, c.subject, c.status,
                       (SELECT COUNT(*) FROM app_user u
                        WHERE u.classId = c.id AND u.role = 'STUDENT'
                          AND u.status = 'ACTIVE') AS studentCount,
                       (SELECT COUNT(*) FROM course_homework h
                        WHERE h.classId = c.id AND h.status = 'PUBLISHED')
                         AS activeHomeworkCount
                FROM classroom c
                WHERE c.teacherId = ?
                ORDER BY c.id
                """,
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.getInt("id"));
                    item.put("name", row.getString("name"));
                    item.put("grade", row.getString("grade"));
                    item.put("subject", row.getString("subject"));
                    item.put("status", row.getString("status"));
                    item.put("studentCount", row.getInt("studentCount"));
                    item.put("activeHomeworkCount", row.getInt("activeHomeworkCount"));
                    return item;
                },
                teacherId);
    }

    @Transactional
    public Map<String, Object> createClass(
            int teacherId,
            JsonNode body) {
        String name = requiredText(body, "name", "班级名称不能为空。");
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO classroom
                      (name, grade, subject, status, teacherId)
                    VALUES (?, ?, ?, 'ACTIVE', ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            statement.setString(2, text(body, "grade", ""));
            statement.setString(3, text(body, "subject", "数学"));
            statement.setInt(4, teacherId);
            return statement;
        }, keyHolder);
        int id = keyHolder.getKey() == null ? 0 : keyHolder.getKey().intValue();
        return classById(teacherId, id);
    }

    @Transactional
    public Map<String, Object> updateClass(
            int teacherId,
            int classId,
            JsonNode body) {
        requireClass(teacherId, classId);
        List<String> updates = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        appendUpdate(updates, arguments, "name", body, "name");
        appendUpdate(updates, arguments, "grade", body, "grade");
        appendUpdate(updates, arguments, "subject", body, "subject");
        appendUpdate(updates, arguments, "status", body, "status");
        if (!updates.isEmpty()) {
            arguments.add(classId);
            jdbcTemplate.update(
                    "UPDATE classroom SET " + String.join(", ", updates)
                            + " WHERE id = ?",
                    arguments.toArray());
        }
        return classById(teacherId, classId);
    }

    public Map<String, Object> listStudents(
            int teacherId,
            int classId,
            String keyword,
            boolean includeDisabled,
            int page,
            int pageSize) {
        requireClass(teacherId, classId);
        String query = keyword == null ? "" : keyword.trim();
        String pattern = "%" + query + "%";
        String statusClause = includeDisabled ? "" : " AND status = 'ACTIVE'";
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                SELECT id, studentNo, displayName, status
                FROM app_user
                WHERE classId = ? AND role = 'STUDENT'
                """ + statusClause + """
                  AND (? = '' OR studentNo LIKE ? OR displayName LIKE ?)
                ORDER BY studentNo, id
                """,
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.getInt("id"));
                    item.put("studentNo", row.getString("studentNo"));
                    item.put("name", row.getString("displayName"));
                    item.put("status", row.getString("status"));
                    return item;
                },
                classId,
                query,
                pattern,
                pattern);
        return page(rows, page, pageSize);
    }

    @Transactional
    public Map<String, Object> createStudent(
            int teacherId,
            int classId,
            JsonNode body) {
        requireClass(teacherId, classId);
        String studentNo = requiredText(body, "studentNo", "学号不能为空。");
        String name = requiredText(body, "name", "学生姓名不能为空。");
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE username = ? OR studentNo = ?",
                Integer.class,
                studentNo,
                studentNo);
        if (existing != null && existing > 0) {
            throw conflict("该学号已经存在。");
        }
        jdbcTemplate.update(
                """
                INSERT INTO app_user
                  (username, passwordHash, displayName, role, status,
                   studentNo, classId)
                VALUES (?, ?, ?, 'STUDENT', 'ACTIVE', ?, ?)
                """,
                studentNo,
                passwordService.encode("123456"),
                name,
                studentNo,
                classId);
        return studentByNo(classId, studentNo);
    }

    @Transactional
    public Map<String, Object> updateStudent(
            int teacherId,
            int classId,
            int studentId,
            JsonNode body) {
        requireClass(teacherId, classId);
        jdbcTemplate.update(
                """
                UPDATE app_user
                SET displayName = COALESCE(?, displayName),
                    status = COALESCE(?, status)
                WHERE id = ? AND classId = ? AND role = 'STUDENT'
                """,
                nullableText(body, "name"),
                nullableText(body, "status"),
                studentId,
                classId);
        return studentById(classId, studentId);
    }

    @Transactional
    public Map<String, Object> transferStudent(
            int teacherId,
            int classId,
            int studentId,
            JsonNode body) {
        requireClass(teacherId, classId);
        int targetClassId = integer(body, "targetClassId", 0);
        if (targetClassId <= 0) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "TARGET_CLASS_REQUIRED",
                    "请选择目标班级。");
        }
        if (targetClassId == classId) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "SAME_CLASS",
                    "学生已经在当前班级。");
        }
        requireClass(teacherId, targetClassId);
        Map<String, Object> sourceStudent = studentById(classId, studentId);
        int changed = workflowMapper.transferStudent(
                studentId,
                classId,
                targetClassId);
        if (changed == 0) {
            throw notFound("学生不存在或已不在原班级。");
        }
        Map<String, Object> result = new LinkedHashMap<>(
                studentById(targetClassId, studentId));
        result.put("fromClassId", classId);
        result.put("fromClassName", classById(teacherId, classId).get("name"));
        result.put("targetClassId", targetClassId);
        result.put(
                "targetClassName",
                classById(teacherId, targetClassId).get("name"));
        result.put("previousStatus", sourceStudent.get("status"));
        return result;
    }

    @Transactional
    public Map<String, Object> importStudents(
            int teacherId,
            int classId,
            JsonNode body) {
        requireClass(teacherId, classId);
        int created = 0;
        for (JsonNode row : body.path("rows")) {
            String studentNo = requiredText(row, "studentNo", "学号不能为空。");
            String name = requiredText(row, "name", "学生姓名不能为空。");
            Integer existing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM app_user WHERE username = ? OR studentNo = ?",
                    Integer.class,
                    studentNo,
                    studentNo);
            if (existing != null && existing > 0) continue;
            jdbcTemplate.update(
                    """
                    INSERT INTO app_user
                      (username, passwordHash, displayName, role, status,
                       studentNo, classId)
                    VALUES (?, ?, ?, 'STUDENT', 'ACTIVE', ?, ?)
                    """,
                    studentNo,
                    passwordService.encode("123456"),
                    name,
                    studentNo,
                    classId);
            created++;
        }
        return Map.of("created", created);
    }

    public List<Map<String, Object>> knowledgePoints(boolean includeDisabled) {
        return jdbcTemplate.query(
                """
                SELECT id, parentId, code, name, kpLevel, status,
                       disabledImpactNote, createdAt, updatedAt
                FROM kp_node
                WHERE (? = TRUE OR status = 'ACTIVE')
                ORDER BY kpLevel, code
                """,
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.getInt("id"));
                    item.put("parentId", row.getObject("parentId"));
                    item.put("code", row.getString("code"));
                    item.put("name", row.getString("name"));
                    item.put("kpLevel", row.getInt("kpLevel"));
                    item.put("status", row.getString("status"));
                    item.put("disabledImpactNote", row.getString("disabledImpactNote"));
                    item.put("createdAt", instant(row.getTimestamp("createdAt")));
                    item.put("updatedAt", instant(row.getTimestamp("updatedAt")));
                    return item;
                },
                includeDisabled);
    }

    public List<Map<String, Object>> templates(
            int teacherId,
            String scope) {
        String where = "approved".equals(scope)
                ? "t.status = 'APPROVED'"
                : "t.createdBy = ?";
        List<Object> arguments = "approved".equals(scope)
                ? List.of()
                : List.of(teacherId);
        return jdbcTemplate.query(
                """
                SELECT t.*, u.displayName AS createdByName,
                       r.displayName AS reviewedByName
                FROM comment_template t
                JOIN app_user u ON u.id = t.createdBy
                LEFT JOIN app_user r ON r.id = t.reviewedBy
                WHERE
                """ + " " + where + " ORDER BY t.updatedAt DESC",
                (row, index) -> templateMap(row),
                arguments.toArray());
    }

    @Transactional
    public Map<String, Object> createTemplate(
            int teacherId,
            JsonNode body) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO comment_template
                      (title, category, structure, content, status, createdBy)
                    VALUES (?, ?, ?, ?, 'PENDING', ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, requiredText(body, "title", "模板标题不能为空。"));
            statement.setString(2, text(body, "category", null));
            statement.setString(3, jsonString(body.path("sections")));
            statement.setString(4, text(body, "content", ""));
            statement.setInt(5, teacherId);
            return statement;
        }, keyHolder);
        int id = keyHolder.getKey() == null ? 0 : keyHolder.getKey().intValue();
        return templateById(id);
    }

    public Map<String, Object> listHomeworks(
            int teacherId,
            String status,
            String keyword,
            int page,
            int pageSize) {
        String safeStatus = status == null ? "" : status.trim();
        String safeKeyword = keyword == null ? "" : keyword.trim();
        String pattern = "%" + safeKeyword + "%";
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                SELECT h.id, h.classId, c.name AS className, h.title,
                       h.status, h.dueAt, h.publishedAt, h.version, h.createdAt,
                       h.updatedAt, h.description,
                       (SELECT COUNT(*) FROM course_question q
                        WHERE q.homeworkId = h.id AND q.isDeleted = 0)
                         AS questionCount,
                       (SELECT COUNT(*) FROM app_user s
                        WHERE s.classId = h.classId AND s.role = 'STUDENT'
                          AND s.status = 'ACTIVE') AS studentCount,
                       (SELECT gb.id FROM grading_batch gb
                        WHERE gb.homeworkId = h.id
                        ORDER BY gb.versionNo DESC LIMIT 1) AS currentGradingBatchId,
                       (SELECT gb.versionNo FROM grading_batch gb
                        WHERE gb.homeworkId = h.id
                        ORDER BY gb.versionNo DESC LIMIT 1)
                         AS currentGradingBatchVersion,
                       (SELECT hd.id FROM handout hd
                        WHERE hd.homeworkId = h.id LIMIT 1) AS handoutId,
                       (SELECT hd.status FROM handout hd
                        WHERE hd.homeworkId = h.id LIMIT 1) AS handoutStatus
                FROM course_homework h
                JOIN classroom c ON c.id = h.classId
                WHERE c.teacherId = ?
                  AND (? = '' OR h.status = ?)
                  AND (? = '' OR h.title LIKE ?)
                ORDER BY h.updatedAt DESC
                """,
                (row, index) -> homeworkSummary(row),
                teacherId,
                safeStatus,
                safeStatus,
                safeKeyword,
                pattern);
        return page(rows, page, pageSize);
    }

    public Map<String, Object> getHomework(
            int teacherId,
            int homeworkId) {
        Map<String, Object> summary = homeworkSummaryById(teacherId, homeworkId);
        summary.put("questions", questions(homeworkId));
        return summary;
    }

    @Transactional
    public Map<String, Object> createHomework(
            int teacherId,
            JsonNode body) {
        int classId = integer(body, "classId", 0);
        requireClass(teacherId, classId);
        String title = requiredText(body, "title", "作业标题不能为空。");
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO course_homework
                      (classId, teacherId, title, description, status, dueAt, version)
                    VALUES (?, ?, ?, ?, 'DRAFT', ?, 1)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, classId);
            statement.setInt(2, teacherId);
            statement.setString(3, title);
            statement.setString(4, text(body, "description", null));
            statement.setObject(5, timestampOrNull(body, "dueAt"));
            return statement;
        }, keyHolder);
        int homeworkId = keyHolder.getKey() == null
                ? 0
                : keyHolder.getKey().intValue();
        return getHomework(teacherId, homeworkId);
    }

    @Transactional
    public Map<String, Object> updateHomework(
            int teacherId,
            int homeworkId,
            JsonNode body) {
        int currentVersion = requireHomeworkVersion(
                teacherId,
                homeworkId,
                integer(body, "expectedVersion", -1));
        requireHomeworkEditable(teacherId, homeworkId);
        jdbcTemplate.update(
                """
                UPDATE course_homework
                SET title = COALESCE(?, title),
                    description = COALESCE(?, description),
                    dueAt = COALESCE(?, dueAt),
                    version = version + 1
                WHERE id = ?
                """,
                nullableText(body, "title"),
                nullableText(body, "description"),
                timestampOrNull(body, "dueAt"),
                homeworkId);
        return getHomework(teacherId, homeworkId);
    }

    @Transactional
    public Map<String, Object> publishHomework(
            int teacherId,
            int homeworkId,
            JsonNode body) {
        requireHomeworkVersion(
                teacherId,
                homeworkId,
                integer(body, "expectedVersion", -1));
        Long questionCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM course_question
                WHERE homeworkId = ? AND isDeleted = 0
                """,
                Long.class,
                homeworkId);
        if (questionCount == null || questionCount == 0) {
            throw conflict("作业至少需要一道题目才能发布。");
        }
        jdbcTemplate.update(
                """
                UPDATE course_homework
                SET status = 'PUBLISHED',
                    publishedAt = CURRENT_TIMESTAMP(3),
                    archivedAt = NULL,
                    version = version + 1
                WHERE id = ?
                """,
                homeworkId);
        return getHomework(teacherId, homeworkId);
    }

    @Transactional
    public Map<String, Object> archiveHomework(
            int teacherId,
            int homeworkId,
            JsonNode body) {
        requireHomeworkVersion(
                teacherId,
                homeworkId,
                integer(body, "expectedVersion", -1));
        jdbcTemplate.update(
                """
                UPDATE course_homework
                SET status = 'ARCHIVED',
                    archivedAt = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE id = ?
                """,
                homeworkId);
        return getHomework(teacherId, homeworkId);
    }

    @Transactional
    public Map<String, Object> createQuestion(
            int teacherId,
            int homeworkId,
            JsonNode body) {
        requireHomeworkEditable(teacherId, homeworkId);
        int seqNo = integer(body, "seqNo", nextQuestionSeq(homeworkId));
        String type = normalizeQuestionType(text(body, "questionType", ""));
        String stem = requiredText(body, "stem", "题干不能为空。");
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
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
            statement.setInt(2, seqNo);
            statement.setString(3, type);
            statement.setString(4, stem);
            statement.setString(5, jsonString(body.path("options")));
            statement.setString(6, text(body, "standardAnswer", null));
            statement.setString(7, text(body, "teacherAnalysis", null));
            statement.setInt(8, integer(body, "score", 1));
            return statement;
        }, keyHolder);
        int questionId = keyHolder.getKey() == null
                ? 0
                : keyHolder.getKey().intValue();
        attachKnowledgePoints(questionId, body.path("knowledgePointIds"));
        if (body.has("aiDraftId") && body.get("aiDraftId").canConvertToLong()) {
            jdbcTemplate.update(
                    """
                    UPDATE ai_question_draft
                    SET status = 'IMPORTED', importedQuestionId = ?
                    WHERE id = ? AND homeworkId = ? AND createdBy = ?
                      AND status = 'READY'
                    """,
                    questionId,
                    body.get("aiDraftId").asLong(),
                    homeworkId,
                    teacherId);
        }
        touchHomework(homeworkId);
        return questionById(questionId);
    }

    @Transactional
    public Map<String, Object> updateQuestion(
            int teacherId,
            int questionId,
            JsonNode body) {
        Map<String, Object> question = questionById(questionId);
        int homeworkId = ((Number) question.get("homeworkId")).intValue();
        requireHomeworkEditable(teacherId, homeworkId);
        int expectedRevision = integer(body, "expectedRevisionNo", -1);
        int currentRevision = ((Number) question.get("revisionNo")).intValue();
        if (expectedRevision >= 0 && expectedRevision != currentRevision) {
            throw conflict("题目已被修改，请刷新后重试。");
        }
        jdbcTemplate.update(
                """
                UPDATE course_question
                SET seqNo = ?, questionType = ?, stem = ?, options = ?,
                    standardAnswer = ?, teacherAnalysis = ?, score = ?,
                    revisionNo = revisionNo + 1
                WHERE id = ?
                """,
                integer(body, "seqNo", ((Number) question.get("seqNo")).intValue()),
                normalizeQuestionType(text(
                        body,
                        "questionType",
                        String.valueOf(question.get("questionType")))),
                text(body, "stem", String.valueOf(question.get("stem"))),
                jsonString(body.path("options")),
                text(body, "standardAnswer", null),
                text(body, "teacherAnalysis", null),
                integer(body, "score", ((Number) question.get("score")).intValue()),
                questionId);
        jdbcTemplate.update(
                "DELETE FROM course_question_kp WHERE questionId = ?",
                questionId);
        attachKnowledgePoints(questionId, body.path("knowledgePointIds"));
        touchHomework(homeworkId);
        return questionById(questionId);
    }

    @Transactional
    public Map<String, Object> deleteQuestion(
            int teacherId,
            int questionId) {
        Map<String, Object> question = questionById(questionId);
        int homeworkId = ((Number) question.get("homeworkId")).intValue();
        requireHomeworkEditable(teacherId, homeworkId);
        jdbcTemplate.update(
                """
                UPDATE course_question
                SET isDeleted = 1, revisionNo = revisionNo + 1
                WHERE id = ?
                """,
                questionId);
        touchHomework(homeworkId);
        return Map.of("deleted", true);
    }

    public List<Map<String, Object>> gradingBatches(
            int teacherId,
            int homeworkId) {
        requireHomework(teacherId, homeworkId);
        return jdbcTemplate.query(
                """
                SELECT gb.*, u.displayName AS importedByName
                FROM grading_batch gb
                LEFT JOIN app_user u ON u.id = gb.importedBy
                WHERE gb.homeworkId = ?
                ORDER BY gb.versionNo DESC
                """,
                (row, index) -> batchMap(row),
                homeworkId);
    }

    public Map<String, Object> gradingRecords(
            int teacherId,
            int homeworkId,
            Integer batchId,
            String keyword,
            Boolean isCorrect,
            Integer seqNo,
            int page,
            int pageSize) {
        requireHomework(teacherId, homeworkId);
        Integer currentBatchId = batchId == null
                ? currentBatchId(homeworkId)
                : batchId;
        if (currentBatchId == null) {
            return page(List.of(), page, pageSize);
        }
        String query = keyword == null ? "" : keyword.trim();
        String pattern = "%" + query + "%";
        int correctFilter = isCorrect == null ? -1 : (isCorrect ? 1 : 0);
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                SELECT gr.id, gr.batchId, gr.homeworkId, gr.studentId,
                       u.studentNo, u.displayName AS studentName,
                       gr.questionId, gr.seqNo, gr.studentAnswer,
                       gr.isCorrect, gr.score, gr.teacherNote, gr.errorTag,
                       gr.createdAt
                FROM grading_record gr
                JOIN app_user u ON u.id = gr.studentId
                WHERE gr.homeworkId = ? AND gr.batchId = ?
                  AND (gr.seqNo = ? OR ? IS NULL)
                  AND (gr.isCorrect = ? OR ? < 0)
                  AND (? = '' OR u.studentNo LIKE ? OR u.displayName LIKE ?)
                ORDER BY u.studentNo, gr.seqNo
                """,
                (row, index) -> gradingRecordMap(row),
                homeworkId,
                currentBatchId,
                seqNo == null ? -1 : seqNo,
                seqNo,
                correctFilter,
                correctFilter,
                query,
                pattern,
                pattern);
        return page(rows, page, pageSize);
    }

    @Transactional
    public Map<String, Object> createManualBatch(
            int teacherId,
            int homeworkId,
            JsonNode body) {
        requireHomeworkEditable(teacherId, homeworkId);
        List<Map<String, Object>> rows = new ArrayList<>();
        JsonNode inputRows = body.path("rows");
        if (!inputRows.isArray() || inputRows.isEmpty()) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "EMPTY_IMPORT",
                    "没有可导入的批改记录。");
        }
        List<Map<String, Object>> validationErrors = new ArrayList<>();
        for (int index = 0; index < inputRows.size(); index++) {
            JsonNode row = inputRows.get(index);
            String studentNo = text(row, "studentNo", "").trim();
            int seq = integer(row, "seqNo", 0);
            int rowNumber = index + 2;
            if (studentNo.isBlank()) {
                validationErrors.add(importRowError(
                        rowNumber,
                        "studentNo",
                        "缺少学生标识",
                        ""));
            }
            if (seq < 1) {
                validationErrors.add(importRowError(
                        rowNumber,
                        "questionSeq",
                        "题号必须大于 0",
                        row.path("seqNo").asText("")));
            }
            JsonNode correctNode = row.get("isCorrect");
            if (correctNode == null
                    || correctNode.isNull()
                    || !correctNode.isBoolean()) {
                validationErrors.add(importRowError(
                        rowNumber,
                        "isCorrect",
                        "缺少对错结果",
                        correctNode == null ? "" : correctNode.asText()));
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("studentNo", studentNo);
            normalized.put("seqNo", seq);
            normalized.put("studentAnswer", text(row, "studentAnswer", null));
            normalized.put("isCorrect", booleanValue(row, "isCorrect", false));
            normalized.put("score", decimal(row, "score", BigDecimal.ZERO));
            normalized.put("teacherNote", text(row, "teacherNote", null));
            normalized.put("errorTag", text(row, "errorTag", null));
            rows.add(normalized);
        }
        if (!validationErrors.isEmpty()) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "IMPORT_VALIDATION_FAILED",
                    "批改数据存在无效行。",
                    Map.of("rowErrors", validationErrors));
        }
        return persistBatch(
                teacherId,
                homeworkId,
                rows,
                "MANUAL",
                text(body, "remark", null),
                false);
    }

    private Map<String, Object> importRowError(
            int rowNumber,
            String field,
            String message,
            String rejectedValue) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("rowNumber", rowNumber);
        error.put("field", field);
        error.put("message", message);
        error.put("rejectedValue", rejectedValue);
        return error;
    }

    @Transactional
    public Map<String, Object> createImportedBatch(
            int teacherId,
            int homeworkId,
            List<Map<String, Object>> rows,
            String source,
            String remark) {
        requireHomeworkEditable(teacherId, homeworkId);
        return persistBatch(
                teacherId,
                homeworkId,
                rows,
                source,
                remark,
                true);
    }

    @Transactional
    public Map<String, Object> reviseGradingRecord(
            int teacherId,
            int recordId,
            JsonNode body) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT gr.*, u.studentNo
                FROM grading_record gr
                JOIN app_user u ON u.id = gr.studentId
                WHERE gr.id = ?
                """,
                recordId);
        if (rows.isEmpty()) {
            throw notFound("批改记录不存在。");
        }
        Map<String, Object> source = rows.get(0);
        int homeworkId = ((Number) source.get("homeworkId")).intValue();
        requireHomeworkEditable(teacherId, homeworkId);
        int batchId = ((Number) source.get("batchId")).intValue();
        List<Map<String, Object>> allRows = jdbcTemplate.queryForList(
                """
                SELECT u.studentNo, gr.seqNo, gr.studentAnswer, gr.isCorrect,
                       gr.score, gr.teacherNote, gr.errorTag
                FROM grading_record gr
                JOIN app_user u ON u.id = gr.studentId
                WHERE gr.batchId = ?
                """,
                batchId);
        for (Map<String, Object> row : allRows) {
            boolean selected = ((Number) source.get("seqNo")).intValue()
                    == ((Number) row.get("seqNo")).intValue()
                    && String.valueOf(source.get("studentNo")).equals(
                            String.valueOf(row.get("studentNo")));
            if (!selected) continue;
            if (body.has("studentAnswer")) {
                row.put("studentAnswer", text(body, "studentAnswer", null));
            }
            row.put("isCorrect", booleanValue(
                    body,
                    "isCorrect",
                    Boolean.TRUE.equals(row.get("isCorrect"))));
            row.put("score", decimal(
                    body,
                    "score",
                    new BigDecimal(String.valueOf(row.get("score")))));
            if (body.has("teacherNote")) {
                row.put("teacherNote", text(body, "teacherNote", null));
            }
        }
        Map<String, Object> result = persistBatch(
                teacherId,
                homeworkId,
                allRows,
                "MANUAL",
                text(body, "remark", "单条批改修订"),
                false);
        Map<String, Object> batch = castMap(result.get("batch"));
        int newBatchId = ((Number) batch.get("id")).intValue();
        int questionId = ((Number) source.get("questionId")).intValue();
        int studentId = ((Number) source.get("studentId")).intValue();
        Map<String, Object> revised = jdbcTemplate.queryForMap(
                """
                SELECT gr.id, gr.batchId, gr.homeworkId, gr.studentId,
                       u.studentNo, u.displayName AS studentName,
                       gr.questionId, gr.seqNo, gr.studentAnswer,
                       gr.isCorrect, gr.score, gr.teacherNote, gr.errorTag,
                       gr.createdAt
                FROM grading_record gr
                JOIN app_user u ON u.id = gr.studentId
                WHERE gr.batchId = ? AND gr.questionId = ? AND gr.studentId = ?
                """,
                newBatchId,
                questionId,
                studentId);
        return Map.of(
                "batch", batch,
                "record", gradingRecordMap(revised),
                "statisticsCalculated", true);
    }

    public Map<String, Object> statistics(
            int teacherId,
            int homeworkId) {
        requireHomework(teacherId, homeworkId);
        Integer batchId = currentBatchId(homeworkId);
        if (batchId == null) {
            return emptyStatistics(homeworkId);
        }
        Map<String, Object> batch = batchById(batchId);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                """
                SELECT gr.*, u.studentNo, u.displayName AS studentName,
                       q.stem, q.questionType
                FROM grading_record gr
                JOIN app_user u ON u.id = gr.studentId
                JOIN course_question q ON q.id = gr.questionId
                WHERE gr.batchId = ?
                """,
                batchId);
        Set<Integer> students = new LinkedHashSet<>();
        double scoreSum = 0;
        int scoreCount = 0;
        int totalWrong = 0;
        int totalUnanswered = 0;
        Map<Integer, List<Map<String, Object>>> byQuestion = new LinkedHashMap<>();
        for (Map<String, Object> record : records) {
            int studentId = ((Number) record.get("studentId")).intValue();
            students.add(studentId);
            scoreSum += ((Number) record.get("score")).doubleValue();
            scoreCount++;
            boolean correct = Boolean.TRUE.equals(record.get("isCorrect"));
            String answer = value(record.get("studentAnswer"));
            if (!correct) totalWrong++;
            if (answer == null || answer.isBlank()) totalUnanswered++;
            byQuestion.computeIfAbsent(
                    ((Number) record.get("questionId")).intValue(),
                    ignored -> new ArrayList<>()).add(record);
        }
        Map<Integer, List<Integer>> knowledgeByQuestion = new LinkedHashMap<>();
        Map<Integer, Map<String, Object>> knowledgeMeta = new LinkedHashMap<>();
        jdbcTemplate.query(
                """
                SELECT qkp.questionId, qkp.knowledgePointId,
                       kp.name, kp.parentId
                FROM course_question_kp qkp
                JOIN kp_node kp ON kp.id = qkp.knowledgePointId
                JOIN course_question q ON q.id = qkp.questionId
                WHERE q.homeworkId = ?
                ORDER BY kp.kpLevel, kp.name
                """,
                rows -> {
                    int questionId = rows.getInt("questionId");
                    int knowledgeId = rows.getInt("knowledgePointId");
                    knowledgeByQuestion.computeIfAbsent(
                            questionId,
                            ignored -> new ArrayList<>()).add(knowledgeId);
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("name", rows.getString("name"));
                    meta.put("parentId", rows.getObject("parentId"));
                    knowledgeMeta.put(knowledgeId, meta);
                },
                homeworkId);
        List<Map<String, Object>> questionStats = new ArrayList<>();
        for (Map.Entry<Integer, List<Map<String, Object>>> entry
                : byQuestion.entrySet()) {
            List<Map<String, Object>> rows = entry.getValue();
            int total = rows.size();
            int correct = (int) rows.stream()
                    .filter(row -> Boolean.TRUE.equals(row.get("isCorrect")))
                    .count();
            int unanswered = (int) rows.stream()
                    .filter(row -> value(row.get("studentAnswer")) == null
                            || value(row.get("studentAnswer")).isBlank())
                    .count();
            Map<String, Object> item = new LinkedHashMap<>();
            Map<String, Object> first = rows.get(0);
            item.put("questionId", entry.getKey());
            item.put("seqNo", first.get("seqNo"));
            item.put("questionType", first.get("questionType"));
            item.put("stemPreview", first.get("stem"));
            item.put("totalCount", total);
            item.put("correctCount", correct);
            item.put("wrongCount", total - correct);
            item.put("unansweredCount", unanswered);
            item.put("errorRate", total == 0
                    ? 0
                    : Math.round((total - correct) * 1000.0 / total) / 10.0);
            item.put("avgScore", rows.stream()
                    .mapToDouble(row -> ((Number) row.get("score")).doubleValue())
                    .average()
                    .orElse(0));
            item.put("optionDistribution", Map.of());
            item.put(
                    "knowledgePointIds",
                    knowledgeByQuestion.getOrDefault(
                            entry.getKey(),
                            List.of()));
            questionStats.add(item);
        }
        Map<Integer, double[]> knowledgeCounters = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Map<String, Object>>> entry
                : byQuestion.entrySet()) {
            List<Integer> knowledgeIds = knowledgeByQuestion.getOrDefault(
                    entry.getKey(),
                    List.of());
            for (Integer knowledgeId : knowledgeIds) {
                double[] counts = knowledgeCounters.computeIfAbsent(
                        knowledgeId,
                        ignored -> new double[5]);
                for (Map<String, Object> record : entry.getValue()) {
                    counts[0] += 1;
                    if (Boolean.TRUE.equals(record.get("isCorrect"))) {
                        counts[1] += 1;
                    }
                    String answer = value(record.get("studentAnswer"));
                    if (answer == null || answer.isBlank()) counts[2] += 1;
                    counts[3] += ((Number) record.get("score")).doubleValue();
                    counts[4] += 1;
                }
            }
        }
        List<Map<String, Object>> knowledgePointStats = new ArrayList<>();
        for (Map.Entry<Integer, double[]> entry : knowledgeCounters.entrySet()) {
            double[] counts = entry.getValue();
            int total = (int) counts[0];
            int correct = (int) counts[1];
            int unanswered = (int) counts[2];
            Map<String, Object> fallbackMeta = new LinkedHashMap<>();
            fallbackMeta.put("name", "知识点 #" + entry.getKey());
            fallbackMeta.put("parentId", null);
            Map<String, Object> meta = knowledgeMeta.getOrDefault(
                    entry.getKey(),
                    fallbackMeta);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("knowledgePointId", entry.getKey());
            item.put("name", meta.get("name"));
            item.put("parentId", meta.get("parentId"));
            item.put("totalCount", total);
            item.put("correctCount", correct);
            item.put("wrongCount", total - correct);
            item.put("unansweredCount", unanswered);
            item.put("errorRate", total == 0
                    ? 0
                    : Math.round((total - correct) * 1000.0 / total) / 10.0);
            item.put("avgScore", counts[4] == 0
                    ? null
                    : Math.round(counts[3] * 100.0 / counts[4]) / 100.0);
            knowledgePointStats.add(item);
        }
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("studentCount", students.size());
        overview.put("questionCount", questionStats.size());
        overview.put("totalWrong", totalWrong);
        overview.put("totalUnanswered", totalUnanswered);
        overview.put("averageScore", scoreCount == 0
                ? null
                : Math.round(scoreSum * 100.0 / scoreCount) / 100.0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("homeworkId", homeworkId);
        result.put("batchId", batchId);
        result.put("batchVersionNo", batch.get("versionNo"));
        result.put("calculatedAt", OffsetDateTime.now().toString());
        result.put("overview", overview);
        result.put("questions", questionStats);
        result.put("knowledgePoints", knowledgePointStats);
        return result;
    }

    @Transactional
    public Map<String, Object> getHandout(
            int teacherId,
            int homeworkId) {
        requireHomework(teacherId, homeworkId);
        Integer id = handoutId(homeworkId);
        if (id == null) {
            id = createEmptyHandout(homeworkId);
        }
        return handoutDetail(id);
    }

    @Transactional
    public Map<String, Object> generateHandout(
            int teacherId,
            int homeworkId,
            JsonNode body) {
        requireHomework(teacherId, homeworkId);
        Integer batchId = currentBatchId(homeworkId);
        if (batchId == null) {
            throw conflict("请先导入批改数据，再生成讲评。");
        }
        int handoutId = handoutId(homeworkId) == null
                ? createEmptyHandout(homeworkId)
                : handoutId(homeworkId);
        jdbcTemplate.update(
                """
                UPDATE handout
                SET status = 'UNPUBLISHED', gradingBatchId = ?,
                    version = version + 1, publishedAt = NULL,
                    publishedBy = NULL, withdrawnAt = NULL
                WHERE id = ?
                """,
                batchId,
                handoutId);
        Set<Integer> requested = new LinkedHashSet<>();
        if (body != null && body.path("questionIds").isArray()) {
            for (JsonNode id : body.path("questionIds")) {
                if (id.canConvertToInt()) requested.add(id.asInt());
            }
        }
        List<Map<String, Object>> wrong = jdbcTemplate.queryForList(
                """
                SELECT gr.questionId, q.seqNo, q.stem, q.standardAnswer,
                       q.teacherAnalysis, COUNT(*) AS wrongCount
                FROM grading_record gr
                JOIN course_question q ON q.id = gr.questionId
                WHERE gr.batchId = ? AND gr.isCorrect = 0
                  AND q.isDeleted = 0
                GROUP BY gr.questionId, q.seqNo, q.stem,
                         q.standardAnswer, q.teacherAnalysis
                ORDER BY q.seqNo
                """,
                batchId);
        String templateContent = commentaryTemplate(body);
        List<Map<String, Object>> selectedWrong = wrong.stream()
                .filter(row -> requested.isEmpty()
                        || requested.contains(
                                ((Number) row.get("questionId")).intValue()))
                .toList();
        List<CommentaryDraft> generated = generateCommentaryDrafts(
                selectedWrong,
                templateContent);
        int sortNo = 0;
        for (CommentaryDraft draft : generated) {
            Map<String, Object> row = draft.row();
            int questionId = ((Number) row.get("questionId")).intValue();
            sortNo++;
            String content = draft.content();
            String draftStatus = draft.status();
            String errorCode = draft.errorCode();
            long elapsedMs = draft.elapsedMs();
            int draftId = saveAiDraft(
                    homeworkId,
                    questionId,
                    content,
                    draftStatus,
                    row,
                    elapsedMs,
                    errorCode);
            Integer itemId = findHandoutItem(handoutId, questionId);
            String finalDraftStatus = draftStatus;
            if (itemId == null) {
                GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
                int finalSortNo = sortNo;
                jdbcTemplate.update(connection -> {
                    PreparedStatement statement = connection.prepareStatement(
                            """
                            INSERT INTO handout_item
                              (handoutId, questionId, source, status, sortNo)
                            VALUES (?, ?, 'AI', ?, ?)
                            """,
                            Statement.RETURN_GENERATED_KEYS);
                    statement.setInt(1, handoutId);
                    statement.setInt(2, questionId);
                    statement.setString(
                            3,
                            handoutItemStatus(finalDraftStatus));
                    statement.setInt(4, finalSortNo);
                    return statement;
                }, keyHolder);
                itemId = keyHolder.getKey() == null
                        ? 0
                        : keyHolder.getKey().intValue();
            } else {
                jdbcTemplate.update(
                        """
                        UPDATE handout_item
                        SET source = 'AI', status = ?, sortNo = ?,
                            confirmedBy = NULL, confirmedAt = NULL,
                            excludeReason = NULL
                        WHERE id = ?
                        """,
                        handoutItemStatus(finalDraftStatus),
                        sortNo,
                        itemId);
            }
            int versionNo = nextItemVersion(itemId);
            GeneratedKeyHolder versionKey = new GeneratedKeyHolder();
            int finalItemId = itemId;
            String finalContent = content;
            int finalDraftId = draftId;
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO handout_item_version
                          (itemId, versionNo, content, changeType,
                           changeNote, createdBy)
                        VALUES (?, ?, ?, 'AI_GENERATE', ?, ?)
                        """,
                        Statement.RETURN_GENERATED_KEYS);
                statement.setInt(1, finalItemId);
                statement.setInt(2, versionNo);
                statement.setString(3, finalContent);
                statement.setString(4, "AI 草稿 #" + finalDraftId);
                statement.setInt(5, teacherId);
                return statement;
            }, versionKey);
            int versionId = versionKey.getKey() == null
                    ? 0
                    : versionKey.getKey().intValue();
            jdbcTemplate.update(
                    "UPDATE handout_item SET currentVersionId = ? WHERE id = ?",
                    versionId,
                    itemId);
        }
        return handoutDetail(handoutId);
    }

    private List<CommentaryDraft> generateCommentaryDrafts(
            List<Map<String, Object>> rows,
            String templateContent) {
        if (rows.isEmpty()) return List.of();
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(3, rows.size()));
        try {
            List<Future<CommentaryDraft>> futures = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                futures.add(executor.submit(
                        () -> generateCommentaryDraft(row, templateContent)));
            }
            List<CommentaryDraft> results = new ArrayList<>();
            for (Future<CommentaryDraft> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new PlatformException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_GENERATION_INTERRUPTED",
                    "讲评生成被中断，请重试。",
                    error);
        } catch (Exception error) {
            throw new PlatformException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_GENERATION_FAILED",
                    "讲评生成失败，请稍后重试或手工填写。",
                    error);
        } finally {
            executor.shutdown();
            try {
                executor.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private CommentaryDraft generateCommentaryDraft(
            Map<String, Object> row,
            String templateContent) {
        long started = System.nanoTime();
        String standardAnswer = value(row.get("standardAnswer"));
        String teacherAnalysis = value(row.get("teacherAnalysis"));
        if (isBlank(standardAnswer) && isBlank(teacherAnalysis)) {
            return new CommentaryDraft(
                    row,
                    "依据不足：请教师补充标准答案或教师解析，或直接手工填写讲评。",
                    "INSUFFICIENT_EVIDENCE",
                    "MISSING_EVIDENCE",
                    (System.nanoTime() - started) / 1_000_000);
        }
        try {
            String templateSection = templateContent == null
                            || templateContent.isBlank()
                    ? "未指定审核模板，请使用清晰的“思路—错因—改法”结构。"
                    : templateContent;
            String content = commentaryText.toReadable(deepTutor.chat("""
                    你是初中数学教师。请根据题目、标准答案、教师解析、错误人数和讲评模板，
                    写一段面向学生的讲评。只讲本题，不判断学生人格，不改变标准答案。
                    不要输出 Markdown 代码块，不要编造学生个人情况。

                    讲评模板：
                    %s

                    题目：%s
                    标准答案：%s
                    教师解析：%s
                    错误人数：%s
                    """.formatted(
                    templateSection,
                    row.get("stem"),
                    row.get("standardAnswer"),
                    row.get("teacherAnalysis"),
                    row.get("wrongCount"))));
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("AI 返回空内容");
            }
            return new CommentaryDraft(
                    row,
                    content,
                    "GENERATED",
                    null,
                    (System.nanoTime() - started) / 1_000_000);
        } catch (Exception error) {
            return new CommentaryDraft(
                    row,
                    "AI 暂不可用。请教师结合原题和解析手工填写本题讲评。",
                    "FAILED",
                    "AI_FAILED",
                    (System.nanoTime() - started) / 1_000_000);
        }
    }

    private String handoutItemStatus(String draftStatus) {
        return switch (draftStatus) {
            case "FAILED" -> "AI_FAILED_MANUAL";
            case "INSUFFICIENT_EVIDENCE" -> "INSUFFICIENT_EVIDENCE";
            default -> "DRAFT";
        };
    }

    private String commentaryTemplate(JsonNode body) {
        if (body == null
                || !body.has("templateId")
                || !body.get("templateId").canConvertToInt()) {
            return "";
        }
        int templateId = body.get("templateId").asInt();
        if (templateId <= 0) return "";
        Map<String, Object> template = templateById(templateId);
        if (!"APPROVED".equals(template.get("status"))) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "TEMPLATE_NOT_APPROVED",
                    "只能使用审核通过的公共讲评模板。");
        }
        Object content = template.get("content");
        return content == null ? "" : String.valueOf(content);
    }

    @Transactional
    public Map<String, Object> confirmHandoutItem(
            int teacherId,
            int itemId,
            JsonNode body) {
        Map<String, Object> item = requireTeacherItem(teacherId, itemId);
        String content = text(body, "content", null);
        if (content == null) {
            Object current = item.get("content");
            content = current == null ? "" : String.valueOf(current);
        }
        if (content.isBlank()) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "EMPTY_COMMENTARY",
                    "讲评内容不能为空。");
        }
        requireCommentaryLength(content);
        int versionNo = nextItemVersion(itemId);
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        String finalContent = content;
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO handout_item_version
                      (itemId, versionNo, content, changeType,
                       changeNote, createdBy)
                    VALUES (?, ?, ?, 'TEACHER_CONFIRM', ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, itemId);
            statement.setInt(2, versionNo);
            statement.setString(3, finalContent);
            statement.setString(4, text(body, "changeNote", "教师确认讲评"));
            statement.setInt(5, teacherId);
            return statement;
        }, keyHolder);
        int versionId = keyHolder.getKey() == null ? 0 : keyHolder.getKey().intValue();
        jdbcTemplate.update(
                """
                UPDATE handout_item
                SET status = 'READY', currentVersionId = ?,
                    confirmedBy = ?, confirmedAt = CURRENT_TIMESTAMP(3),
                    excludeReason = NULL
                WHERE id = ?
                """,
                versionId,
                teacherId,
                itemId);
        return itemById(itemId);
    }

    @Transactional
    public Map<String, Object> saveHandoutItem(
            int teacherId,
            int itemId,
            JsonNode body) {
        requireTeacherItem(teacherId, itemId);
        String content = requiredText(body, "content", "讲评内容不能为空。");
        requireCommentaryLength(content);
        int versionNo = nextItemVersion(itemId);
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO handout_item_version
                      (itemId, versionNo, content, changeType,
                       changeNote, createdBy)
                    VALUES (?, ?, ?, 'TEACHER_SAVE', ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, itemId);
            statement.setInt(2, versionNo);
            statement.setString(3, content);
            statement.setString(4, text(body, "changeNote", "保存讲评草稿"));
            statement.setInt(5, teacherId);
            return statement;
        }, keyHolder);
        int versionId = keyHolder.getKey() == null ? 0 : keyHolder.getKey().intValue();
        jdbcTemplate.update(
                """
                UPDATE handout_item
                SET currentVersionId = ?, status = 'DRAFT',
                    confirmedBy = NULL, confirmedAt = NULL
                WHERE id = ?
                """,
                versionId,
                itemId);
        return itemById(itemId);
    }

    @Transactional
    public Map<String, Object> excludeHandoutItem(
            int teacherId,
            int itemId,
            JsonNode body) {
        requireTeacherItem(teacherId, itemId);
        String reason = requiredText(body, "reason", "请填写剔除原因。");
        jdbcTemplate.update(
                """
                UPDATE handout_item
                SET status = 'EXCLUDED', excludeReason = ?,
                    confirmedBy = NULL, confirmedAt = NULL
                WHERE id = ?
                """,
                reason,
                itemId);
        return itemById(itemId);
    }

    @Transactional
    public Map<String, Object> regenerateHandoutItem(
            int teacherId,
            int itemId) {
        Map<String, Object> item = requireTeacherItem(teacherId, itemId);
        String stem = value(item.get("stem"));
        String answer = value(item.get("standardAnswer"));
        String analysis = value(item.get("teacherAnalysis"));
        if (isBlank(answer) && isBlank(analysis)) {
            jdbcTemplate.update(
                    """
                    UPDATE handout_item
                    SET status = 'INSUFFICIENT_EVIDENCE', source = 'AI',
                        confirmedBy = NULL, confirmedAt = NULL
                    WHERE id = ?
                    """,
                    itemId);
            return itemById(itemId);
        }
        String content;
        try {
            content = commentaryText.toReadable(deepTutor.chat("""
                    为初中数学题目重新生成一段简短讲评。
                    题目：%s
                    标准答案：%s
                    教师解析：%s
                    """.formatted(stem, answer, analysis)));
        } catch (Exception error) {
            jdbcTemplate.update(
                    """
                    UPDATE handout_item
                    SET status = 'AI_FAILED_MANUAL', source = 'AI',
                        confirmedBy = NULL, confirmedAt = NULL
                    WHERE id = ?
                    """,
                    itemId);
            return itemById(itemId);
        }
        JsonNode payload = objectMapper.createObjectNode()
                .put("content", content)
                .put("changeNote", "AI 重新生成");
        return saveHandoutItem(teacherId, itemId, payload);
    }

    public List<Map<String, Object>> handoutItemVersions(
            int teacherId,
            int itemId) {
        requireTeacherItem(teacherId, itemId);
        return jdbcTemplate.query(
                """
                SELECT v.*, u.displayName AS createdByName
                FROM handout_item_version v
                LEFT JOIN app_user u ON u.id = v.createdBy
                WHERE v.itemId = ?
                ORDER BY v.versionNo DESC
                """,
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.getInt("id"));
                    item.put("versionNo", row.getInt("versionNo"));
                    item.put("content", row.getString("content"));
                    item.put("changeType", row.getString("changeType"));
                    item.put("changeNote", row.getString("changeNote"));
                    item.put("createdBy", row.getInt("createdBy"));
                    item.put("createdByName", row.getString("createdByName"));
                    item.put("createdAt", instant(row.getTimestamp("createdAt")));
                    return item;
                },
                itemId);
    }

    @Transactional
    public Map<String, Object> publishHandout(
            int teacherId,
            int handoutId,
            JsonNode body) {
        Integer homeworkId = jdbcTemplate.queryForObject(
                "SELECT homeworkId FROM handout WHERE id = ?",
                Integer.class,
                handoutId);
        if (homeworkId == null) throw notFound("讲评不存在。");
        requireHomework(teacherId, homeworkId);
        Map<String, Object> handout = handoutSummary(handoutId);
        if (!Boolean.TRUE.equals(handout.get("publishable"))) {
            @SuppressWarnings("unchecked")
            List<String> reasons = (List<String>) handout.get("blockingReasons");
            throw new PlatformException(
                    HttpStatus.CONFLICT,
                    "HANDOUT_NOT_PUBLISHABLE",
                    "讲评尚未达到发布条件。",
                    Map.of("blockingReasons", reasons));
        }
        jdbcTemplate.update(
                """
                UPDATE handout
                SET status = 'PUBLISHED', publishedAt = CURRENT_TIMESTAMP(3),
                    publishedBy = ?, withdrawnAt = NULL,
                    version = version + 1
                WHERE id = ?
                """,
                teacherId,
                handoutId);
        return handoutDetail(handoutId);
    }

    @Transactional
    public Map<String, Object> withdrawHandout(
            int teacherId,
            int handoutId,
            JsonNode body) {
        Integer homeworkId = jdbcTemplate.queryForObject(
                "SELECT homeworkId FROM handout WHERE id = ?",
                Integer.class,
                handoutId);
        if (homeworkId == null) throw notFound("讲评不存在。");
        requireHomework(teacherId, homeworkId);
        jdbcTemplate.update(
                """
                UPDATE handout
                SET status = 'WITHDRAWN', withdrawnAt = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE id = ?
                """,
                handoutId);
        jdbcTemplate.update(
                """
                UPDATE handout_item
                SET status = 'PENDING_REVIEW',
                    confirmedBy = NULL, confirmedAt = NULL
                WHERE handoutId = ? AND status = 'READY'
                """,
                handoutId);
        return handoutDetail(handoutId);
    }

    public Map<String, Object> handoutLogs(
            int teacherId,
            int handoutId,
            int page,
            int pageSize) {
        Integer homeworkId = jdbcTemplate.queryForObject(
                "SELECT homeworkId FROM handout WHERE id = ?",
                Integer.class,
                handoutId);
        if (homeworkId == null) throw notFound("讲评不存在。");
        requireHomework(teacherId, homeworkId);
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                SELECT l.id, l.action, l.statusCode, l.durationMs, l.createdAt,
                       l.actorId, l.actorName, l.summary
                FROM operation_log l
                WHERE l.path LIKE ? AND l.method = 'POST'
                ORDER BY l.createdAt DESC
                """,
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.getLong("id"));
                    item.put("action", row.getString("action").contains("/publish")
                            ? "PUBLISH"
                            : row.getString("action").contains("/withdraw")
                                    ? "WITHDRAW"
                                    : "REPUBLISH");
                    item.put("fromStatus", null);
                    item.put("toStatus", null);
                    item.put("itemCount", null);
                    item.put("operatorId", row.getObject("actorId"));
                    item.put("operatorName", row.getString("actorName"));
                    item.put("remark", row.getString("summary"));
                    item.put("createdAt", instant(row.getTimestamp("createdAt")));
                    return item;
                },
                "%/api/teacher/handouts/" + handoutId + "/%");
        return page(rows, page, pageSize);
    }

    public Map<String, Object> correctionProgress(
            int teacherId,
            int homeworkId,
            int page,
            int pageSize) {
        requireHomework(teacherId, homeworkId);
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                SELECT u.id AS studentId, u.studentNo,
                       u.displayName AS studentName,
                       SUM(CASE WHEN gr.isCorrect = 0 THEN 1 ELSE 0 END)
                         AS wrongCount,
                       COUNT(DISTINCT c.questionId) AS correctedCount,
                       MAX(c.submittedAt) AS lastSubmittedAt
                FROM grading_record gr
                JOIN app_user u ON u.id = gr.studentId
                LEFT JOIN correction_v2 c
                  ON c.homeworkId = gr.homeworkId
                 AND c.studentId = gr.studentId
                 AND c.questionId = gr.questionId
                WHERE gr.homeworkId = ?
                  AND gr.batchId = (
                    SELECT id FROM grading_batch
                    WHERE homeworkId = ? ORDER BY versionNo DESC LIMIT 1
                  )
                  AND gr.isCorrect = 0
                GROUP BY u.id, u.studentNo, u.displayName
                ORDER BY u.studentNo
                """,
                (row, index) -> {
                    int wrong = row.getInt("wrongCount");
                    int corrected = row.getInt("correctedCount");
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("studentId", row.getInt("studentId"));
                    item.put("studentNo", row.getString("studentNo"));
                    item.put("studentName", row.getString("studentName"));
                    item.put("wrongCount", wrong);
                    item.put("correctedCount", corrected);
                    item.put("pendingCount", Math.max(0, wrong - corrected));
                    item.put("completionRate", wrong == 0
                            ? 0
                            : Math.round(corrected * 1000.0 / wrong) / 10.0);
                    item.put("lastSubmittedAt",
                            instant(row.getTimestamp("lastSubmittedAt")));
                    return item;
                },
                homeworkId,
                homeworkId);
        return page(rows, page, pageSize);
    }

    public Map<String, Object> questionCorrectionProgress(
            int teacherId,
            int homeworkId,
            int page,
            int pageSize) {
        requireHomework(teacherId, homeworkId);
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                SELECT q.id AS questionId, q.seqNo, q.stem,
                       SUM(CASE WHEN gr.isCorrect = 0 THEN 1 ELSE 0 END)
                         AS wrongCount,
                       COUNT(DISTINCT c.studentId) AS correctedCount,
                       MAX(c.submittedAt) AS lastSubmittedAt
                FROM course_question q
                JOIN grading_record gr ON gr.questionId = q.id
                LEFT JOIN correction_v2 c
                  ON c.questionId = q.id AND c.homeworkId = q.homeworkId
                WHERE q.homeworkId = ? AND q.isDeleted = 0
                  AND gr.batchId = (
                    SELECT id FROM grading_batch
                    WHERE homeworkId = ? ORDER BY versionNo DESC LIMIT 1
                  )
                GROUP BY q.id, q.seqNo, q.stem
                ORDER BY q.seqNo
                """,
                (row, index) -> {
                    int wrong = row.getInt("wrongCount");
                    int corrected = row.getInt("correctedCount");
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("questionId", row.getInt("questionId"));
                    item.put("seqNo", row.getInt("seqNo"));
                    item.put("stem", row.getString("stem"));
                    item.put("wrongCount", wrong);
                    item.put("correctedCount", corrected);
                    item.put("pendingCount", Math.max(0, wrong - corrected));
                    item.put("completionRate", wrong == 0
                            ? 0
                            : Math.round(corrected * 1000.0 / wrong) / 10.0);
                    item.put("lastSubmittedAt",
                            instant(row.getTimestamp("lastSubmittedAt")));
                    return item;
                },
                homeworkId,
                homeworkId);
        return page(rows, page, pageSize);
    }

    public Map<String, Object> corrections(
            int teacherId,
            int homeworkId,
            String keyword,
            int page,
            int pageSize) {
        requireHomework(teacherId, homeworkId);
        String query = keyword == null ? "" : keyword.trim();
        String pattern = "%" + query + "%";
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                SELECT c.*, u.studentNo, u.displayName AS studentName,
                       q.seqNo
                FROM correction_v2 c
                JOIN app_user u ON u.id = c.studentId
                JOIN course_question q ON q.id = c.questionId
                WHERE c.homeworkId = ?
                  AND (? = '' OR u.studentNo LIKE ? OR u.displayName LIKE ?)
                ORDER BY c.submittedAt DESC
                """,
                (row, index) -> correctionMap(row),
                homeworkId,
                query,
                pattern,
                pattern);
        return page(rows, page, pageSize);
    }

    public Map<String, Object> doubts(
            int teacherId,
            String status,
            String keyword,
            int page,
            int pageSize) {
        String safeStatus = status == null ? "" : status.trim();
        String query = keyword == null ? "" : keyword.trim();
        String pattern = "%" + query + "%";
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                SELECT d.*, u.studentNo, u.displayName AS studentName,
                       q.seqNo, q.stem, h.title AS homeworkTitle,
                       h.id AS homeworkId
                FROM doubt d
                JOIN app_user u ON u.id = d.studentId
                JOIN handout_item hi ON hi.id = d.handoutItemId
                JOIN handout hd ON hd.id = hi.handoutId
                JOIN course_homework h ON h.id = hd.homeworkId
                JOIN course_question q ON q.id = d.questionId
                JOIN classroom c ON c.id = h.classId
                WHERE c.teacherId = ?
                  AND (? = '' OR d.status = ?)
                  AND (? = '' OR u.studentNo LIKE ? OR u.displayName LIKE ?
                       OR d.content LIKE ? OR h.title LIKE ?)
                ORDER BY d.createdAt DESC
                """,
                (row, index) -> doubtMap(row),
                teacherId,
                safeStatus,
                safeStatus,
                query,
                pattern,
                pattern,
                pattern,
                pattern);
        return page(rows, page, pageSize);
    }

    @Transactional
    public Map<String, Object> replyDoubt(
            int teacherId,
            int doubtId,
            JsonNode body) {
        String content = requiredText(body, "content", "回复内容不能为空。");
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM doubt WHERE id = ?",
                Integer.class,
                doubtId);
        if (count == null || count == 0) throw notFound("疑问不存在。");
        jdbcTemplate.update(
                """
                INSERT INTO doubt_reply (doubtId, teacherId, content)
                VALUES (?, ?, ?)
                """,
                doubtId,
                teacherId,
                content);
        jdbcTemplate.update(
                "UPDATE doubt SET status = 'REPLIED' WHERE id = ?",
                doubtId);
        return doubtById(doubtId);
    }

    private int createEmptyHandout(int homeworkId) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO handout
                      (homeworkId, status, version, gradingBatchId)
                    VALUES (?, 'UNPUBLISHED', 1, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, homeworkId);
            statement.setObject(2, currentBatchId(homeworkId));
            return statement;
        }, keyHolder);
        return keyHolder.getKey() == null ? 0 : keyHolder.getKey().intValue();
    }

    private Integer handoutId(int homeworkId) {
        return jdbcTemplate.query(
                "SELECT id FROM handout WHERE homeworkId = ? LIMIT 1",
                rows -> rows.next() ? rows.getInt(1) : null,
                homeworkId);
    }

    private Map<String, Object> handoutDetail(int handoutId) {
        Map<String, Object> summary = handoutSummary(handoutId);
        List<Map<String, Object>> items = jdbcTemplate.query(
                """
                SELECT hi.*, h.homeworkId, q.seqNo, q.questionType, q.stem, q.options,
                       q.standardAnswer, q.teacherAnalysis, q.score,
                       v.content, v.versionNo,
                       (SELECT COUNT(*) FROM handout_item_version viv
                        WHERE viv.itemId = hi.id) AS versionCount,
                       (SELECT COUNT(*) FROM grading_record gr
                        WHERE gr.questionId = hi.questionId AND gr.isCorrect = 0
                          AND gr.batchId = h.gradingBatchId) AS wrongCount
                FROM handout_item hi
                JOIN handout h ON h.id = hi.handoutId
                JOIN course_question q ON q.id = hi.questionId
                LEFT JOIN handout_item_version v ON v.id = hi.currentVersionId
                WHERE hi.handoutId = ?
                ORDER BY hi.sortNo, hi.id
                """,
                (row, index) -> itemMap(row),
                handoutId);
        summary.put("items", items);
        return summary;
    }

    private Map<String, Object> handoutSummary(int handoutId) {
        return jdbcTemplate.query(
                """
                SELECT hd.*, h.title AS homeworkTitle,
                       COUNT(hi.id) AS itemCount,
                       SUM(CASE WHEN hi.status = 'READY' THEN 1 ELSE 0 END)
                         AS readyCount,
                       SUM(CASE WHEN hi.status = 'PENDING_REVIEW' THEN 1 ELSE 0 END)
                         AS pendingReviewCount,
                       SUM(CASE WHEN hi.status IN
                         ('INSUFFICIENT_EVIDENCE', 'AI_FAILED_MANUAL', 'DRAFT')
                         THEN 1 ELSE 0 END) AS blockedCount,
                       SUM(CASE WHEN hi.status = 'EXCLUDED' THEN 1 ELSE 0 END)
                         AS excludedCount
                FROM handout hd
                JOIN course_homework h ON h.id = hd.homeworkId
                LEFT JOIN handout_item hi ON hi.handoutId = hd.id
                WHERE hd.id = ?
                GROUP BY hd.id, h.title
                """,
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.getInt("id"));
                    item.put("homeworkId", row.getInt("homeworkId"));
                    item.put("homeworkTitle", row.getString("homeworkTitle"));
                    item.put("status", row.getString("status"));
                    item.put("itemCount", row.getInt("itemCount"));
                    item.put("readyCount", row.getInt("readyCount"));
                    item.put("pendingReviewCount",
                            row.getInt("pendingReviewCount"));
                    item.put("blockedCount", row.getInt("blockedCount"));
                    item.put("excludedCount", row.getInt("excludedCount"));
                    int ready = row.getInt("readyCount");
                    List<String> reasons = new ArrayList<>();
                    if (ready == 0) reasons.add("没有可发布的讲评条目");
                    item.put("publishable", reasons.isEmpty());
                    item.put("blockingReasons", reasons);
                    item.put("publishedAt", instant(row.getTimestamp("publishedAt")));
                    item.put("publishedBy", row.getObject("publishedBy"));
                    item.put("withdrawnAt", instant(row.getTimestamp("withdrawnAt")));
                    item.put("version", row.getInt("version"));
                    item.put("gradingBatchId", row.getObject("gradingBatchId"));
                    item.put("createdAt", instant(row.getTimestamp("createdAt")));
                    item.put("updatedAt", instant(row.getTimestamp("updatedAt")));
                    return item;
                },
                handoutId).stream().findFirst().orElseThrow(
                        () -> notFound("讲评不存在。"));
    }

    private Map<String, Object> itemMap(java.sql.ResultSet row)
            throws java.sql.SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getInt("id"));
        item.put("handoutId", row.getInt("handoutId"));
        item.put("questionId", row.getInt("questionId"));
        item.put("seqNo", row.getInt("seqNo"));
        item.put("questionType", row.getString("questionType"));
        item.put("stem", row.getString("stem"));
        item.put("options", parseJson(row.getString("options")));
        item.put("standardAnswer", row.getString("standardAnswer"));
        item.put("teacherAnalysis", row.getString("teacherAnalysis"));
        item.put("score", row.getInt("score"));
        item.put("source", row.getString("source"));
        item.put("status", row.getString("status"));
        item.put("sortNo", row.getInt("sortNo"));
        item.put("currentVersionId", row.getObject("currentVersionId"));
        item.put("content", commentaryText.toReadable(row.getString("content")));
        item.put("versionCount", row.getInt("versionCount"));
        item.put("confirmedBy", row.getObject("confirmedBy"));
        item.put("confirmedAt", instant(row.getTimestamp("confirmedAt")));
        item.put("excludeReason", row.getString("excludeReason"));
        String status = row.getString("status");
        List<String> actions = new ArrayList<>();
        if (!"EXCLUDED".equals(status)) {
            actions.add("CONFIRM");
            actions.add("EXCLUDE");
            actions.add("REGENERATE");
        }
        item.put("allowedActions", actions);
        int homeworkId = row.getInt("homeworkId");
        int questionId = row.getInt("questionId");
        Map<String, Object> draft = workflowMapper.selectLatestAiDraft(
                homeworkId,
                questionId);
        List<Map<String, Object>> evidence = itemEvidence(
                row,
                draft);
        item.put("evidence", evidence);
        item.put("draft", teacherDraft(draft, evidence));
        item.put("wrongCount", row.getInt("wrongCount"));
        return item;
    }

    private List<Map<String, Object>> itemEvidence(
            java.sql.ResultSet row,
            Map<String, Object> draft) throws java.sql.SQLException {
        LinkedHashMap<String, Map<String, Object>> evidence =
                new LinkedHashMap<>();
        if (draft != null) {
            Object raw = draft.get("evidence");
            Object parsed = parseJson(raw == null ? null : String.valueOf(raw));
            if (parsed instanceof List<?> list) {
                for (Object entry : list) {
                    if (!(entry instanceof Map<?, ?> values)) continue;
                    String type = value(values.get("evidenceType"));
                    String text = value(values.get("snapshotText"));
                    if (isBlank(type) || isBlank(text)) continue;
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    normalized.put("evidenceType", type);
                    normalized.put("snapshotText", text);
                    evidence.put(type + "|" + text, normalized);
                }
            }
        }
        addEvidence(
                evidence,
                "QUESTION_STEM",
                row.getString("stem"));
        addEvidence(
                evidence,
                "STANDARD_ANSWER",
                row.getString("standardAnswer"));
        addEvidence(
                evidence,
                "TEACHER_ANALYSIS",
                row.getString("teacherAnalysis"));
        addEvidence(
                evidence,
                "STATISTICS",
                row.getInt("wrongCount") + " 人错误");
        List<String> knowledgeNames = jdbcTemplate.query(
                """
                SELECT k.name
                FROM course_question_kp qk
                JOIN kp_node k ON k.id = qk.knowledgePointId
                WHERE qk.questionId = ?
                ORDER BY k.kpLevel, k.code
                """,
                (result, index) -> result.getString("name"),
                row.getInt("questionId"));
        if (!knowledgeNames.isEmpty()) {
            addEvidence(
                    evidence,
                    "KNOWLEDGE_POINT",
                    String.join("、", knowledgeNames));
        }
        return List.copyOf(evidence.values());
    }

    private void addEvidence(
            Map<String, Map<String, Object>> evidence,
            String type,
            String text) {
        if (isBlank(text)) return;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("evidenceType", type);
        item.put("snapshotText", text);
        evidence.putIfAbsent(type + "|" + text, item);
    }

    private Map<String, Object> teacherDraft(
            Map<String, Object> draft,
            List<Map<String, Object>> evidence) {
        if (draft == null) return null;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", draft.get("id"));
        item.put("homeworkId", null);
        item.put("questionId", null);
        item.put("draftType", "QUESTION_COMMENTARY");
        item.put("content", commentaryText.toReadable(
                value(draft.get("content"))));
        item.put("status", draft.get("status"));
        item.put("model", draft.get("model"));
        item.put("promptVersion", draft.get("promptVersion"));
        item.put("generationNo", draft.get("generationNo"));
        item.put("requestId", null);
        item.put("elapsedMs", draft.get("elapsedMs"));
        item.put("errorCode", draft.get("errorCode"));
        Object createdAt = draft.get("createdAt");
        item.put("createdAt", createdAt == null ? null : createdAt.toString());
        item.put("evidence", evidence);
        return item;
    }

    private Map<String, Object> itemById(int itemId) {
        return jdbcTemplate.query(
                """
                SELECT hi.*, h.homeworkId, q.seqNo, q.questionType, q.stem, q.options,
                       q.standardAnswer, q.teacherAnalysis, q.score,
                       v.content,
                       (SELECT COUNT(*) FROM handout_item_version viv
                        WHERE viv.itemId = hi.id) AS versionCount,
                       (SELECT COUNT(*) FROM grading_record gr
                        WHERE gr.questionId = hi.questionId AND gr.isCorrect = 0
                          AND gr.batchId = h.gradingBatchId) AS wrongCount
                FROM handout_item hi
                JOIN handout h ON h.id = hi.handoutId
                JOIN course_question q ON q.id = hi.questionId
                LEFT JOIN handout_item_version v ON v.id = hi.currentVersionId
                WHERE hi.id = ?
                """,
                (row, index) -> itemMap(row),
                itemId).stream().findFirst().orElseThrow(
                        () -> notFound("讲评条目不存在。"));
    }

    private Map<String, Object> requireTeacherItem(
            int teacherId,
            int itemId) {
        Map<String, Object> item = itemById(itemId);
        int handoutId = ((Number) item.get("handoutId")).intValue();
        Integer homeworkId = jdbcTemplate.queryForObject(
                "SELECT homeworkId FROM handout WHERE id = ?",
                Integer.class,
                handoutId);
        if (homeworkId == null) throw notFound("讲评不存在。");
        requireHomework(teacherId, homeworkId);
        return item;
    }

    private int nextItemVersion(int itemId) {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(versionNo), 0) FROM handout_item_version WHERE itemId = ?",
                Integer.class,
                itemId);
        return (max == null ? 0 : max) + 1;
    }

    private Integer findHandoutItem(int handoutId, int questionId) {
        return jdbcTemplate.query(
                """
                SELECT id FROM handout_item
                WHERE handoutId = ? AND questionId = ?
                LIMIT 1
                """,
                rows -> rows.next() ? rows.getInt(1) : null,
                handoutId,
                questionId);
    }

    private int saveAiDraft(
            int homeworkId,
            int questionId,
            String content,
            String status,
            Map<String, Object> source,
            long elapsedMs,
            String errorCode) {
        Integer generationNo = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(MAX(generationNo), 0) + 1
                FROM ai_comment_draft
                WHERE homeworkId = ? AND questionId = ?
                """,
                Integer.class,
                homeworkId,
                questionId);
        String evidence;
        try {
            List<Map<String, Object>> evidenceItems = new ArrayList<>();
            addAiEvidence(
                    evidenceItems,
                    "QUESTION_STEM",
                    value(source.get("stem")));
            addAiEvidence(
                    evidenceItems,
                    "STANDARD_ANSWER",
                    value(source.get("standardAnswer")));
            addAiEvidence(
                    evidenceItems,
                    "TEACHER_ANALYSIS",
                    value(source.get("teacherAnalysis")));
            addAiEvidence(
                    evidenceItems,
                    "STATISTICS",
                    source.get("wrongCount") + " 人错误");
            List<String> knowledgeNames = jdbcTemplate.query(
                    """
                    SELECT k.name
                    FROM course_question_kp qk
                    JOIN kp_node k ON k.id = qk.knowledgePointId
                    WHERE qk.questionId = ?
                    ORDER BY k.kpLevel, k.code
                    """,
                    (result, index) -> result.getString("name"),
                    questionId);
            if (!knowledgeNames.isEmpty()) {
                addAiEvidence(
                        evidenceItems,
                        "KNOWLEDGE_POINT",
                        String.join("、", knowledgeNames));
            }
            evidence = objectMapper.writeValueAsString(evidenceItems);
        } catch (Exception error) {
            evidence = "[]";
        }
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        int finalGeneration = generationNo == null ? 1 : generationNo;
        String finalEvidence = evidence;
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO ai_comment_draft
                      (homeworkId, questionId, draftType, content, status,
                       model, promptVersion, generationNo, requestId,
                       elapsedMs, errorCode, evidence)
                    VALUES (?, ?, 'QUESTION_COMMENTARY', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, homeworkId);
            statement.setInt(2, questionId);
            statement.setString(3, content);
            statement.setString(4, status);
            statement.setString(5, "deeptutor");
            statement.setString(6, "v1");
            statement.setInt(7, finalGeneration);
            statement.setString(8, java.util.UUID.randomUUID().toString());
            statement.setLong(9, elapsedMs);
            statement.setString(10, errorCode);
            statement.setString(11, finalEvidence);
            return statement;
        }, keyHolder);
        return keyHolder.getKey() == null ? 0 : keyHolder.getKey().intValue();
    }

    private void addAiEvidence(
            List<Map<String, Object>> evidence,
            String type,
            String text) {
        if (isBlank(text)) return;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("evidenceType", type);
        item.put("snapshotText", text);
        evidence.add(item);
    }

    private Map<String, Object> correctionMap(java.sql.ResultSet row)
            throws java.sql.SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getInt("id"));
        item.put("homeworkId", row.getInt("homeworkId"));
        item.put("questionId", row.getInt("questionId"));
        item.put("seqNo", row.getInt("seqNo"));
        item.put("studentId", row.getInt("studentId"));
        item.put("studentNo", row.getString("studentNo"));
        item.put("studentName", row.getString("studentName"));
        item.put("handoutItemId", row.getInt("handoutItemId"));
        item.put("itemVersionId", row.getInt("itemVersionId"));
        item.put("selectedAnswer", row.getString("selectedAnswer"));
        item.put("content", row.getString("content"));
        item.put("versionNo", row.getInt("versionNo"));
        item.put("submittedAt", instant(row.getTimestamp("submittedAt")));
        return item;
    }

    private Map<String, Object> doubtMap(java.sql.ResultSet row)
            throws java.sql.SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        int id = row.getInt("id");
        item.put("id", id);
        item.put("studentId", row.getInt("studentId"));
        item.put("studentNo", row.getString("studentNo"));
        item.put("studentName", row.getString("studentName"));
        item.put("handoutItemId", row.getInt("handoutItemId"));
        item.put("questionId", row.getInt("questionId"));
        item.put("seqNo", row.getInt("seqNo"));
        item.put("content", row.getString("content"));
        item.put("status", row.getString("status"));
        item.put("createdAt", instant(row.getTimestamp("createdAt")));
        item.put("updatedAt", instant(row.getTimestamp("updatedAt")));
        item.put("replies", replies(id));
        item.put("homeworkId", row.getInt("homeworkId"));
        item.put("homeworkTitle", row.getString("homeworkTitle"));
        return item;
    }

    private Map<String, Object> doubtById(int doubtId) {
        return jdbcTemplate.query(
                """
                SELECT d.*, u.studentNo, u.displayName AS studentName,
                       q.seqNo, h.title AS homeworkTitle, h.id AS homeworkId
                FROM doubt d
                JOIN app_user u ON u.id = d.studentId
                JOIN handout_item hi ON hi.id = d.handoutItemId
                JOIN handout hd ON hd.id = hi.handoutId
                JOIN course_homework h ON h.id = hd.homeworkId
                JOIN course_question q ON q.id = d.questionId
                WHERE d.id = ?
                """,
                (row, index) -> doubtMap(row),
                doubtId).stream().findFirst().orElseThrow(
                        () -> notFound("疑问不存在。"));
    }

    private List<Map<String, Object>> replies(int doubtId) {
        return jdbcTemplate.query(
                """
                SELECT r.*, u.displayName AS teacherName
                FROM doubt_reply r
                JOIN app_user u ON u.id = r.teacherId
                WHERE r.doubtId = ?
                ORDER BY r.createdAt
                """,
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.getInt("id"));
                    item.put("teacherId", row.getInt("teacherId"));
                    item.put("teacherName", row.getString("teacherName"));
                    item.put("content", row.getString("content"));
                    item.put("createdAt", instant(row.getTimestamp("createdAt")));
                    return item;
                },
                doubtId);
    }

    public Map<String, Object> homeworkSummaryById(
            int teacherId,
            int homeworkId) {
        return jdbcTemplate.query(
                """
                SELECT h.id, h.classId, c.name AS className, h.title,
                       h.status, h.dueAt, h.publishedAt, h.version, h.createdAt,
                       h.updatedAt, h.description,
                       (SELECT COUNT(*) FROM course_question q
                        WHERE q.homeworkId = h.id AND q.isDeleted = 0)
                         AS questionCount,
                       (SELECT COUNT(*) FROM app_user s
                        WHERE s.classId = h.classId AND s.role = 'STUDENT'
                          AND s.status = 'ACTIVE') AS studentCount,
                       (SELECT gb.id FROM grading_batch gb
                        WHERE gb.homeworkId = h.id
                        ORDER BY gb.versionNo DESC LIMIT 1)
                         AS currentGradingBatchId,
                       (SELECT gb.versionNo FROM grading_batch gb
                        WHERE gb.homeworkId = h.id
                        ORDER BY gb.versionNo DESC LIMIT 1)
                         AS currentGradingBatchVersion,
                       (SELECT hd.id FROM handout hd
                        WHERE hd.homeworkId = h.id LIMIT 1) AS handoutId,
                       (SELECT hd.status FROM handout hd
                        WHERE hd.homeworkId = h.id LIMIT 1) AS handoutStatus
                FROM course_homework h
                JOIN classroom c ON c.id = h.classId
                WHERE h.id = ? AND c.teacherId = ?
                """,
                (row, index) -> homeworkSummary(row),
                homeworkId,
                teacherId).stream().findFirst().orElseThrow(
                        () -> notFound("作业不存在。"));
    }

    private Map<String, Object> persistBatch(
            int teacherId,
            int homeworkId,
            List<Map<String, Object>> rows,
            String source,
            String remark,
            boolean skipMissing) {
        Integer maxVersion = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(versionNo), 0) FROM grading_batch WHERE homeworkId = ?",
                Integer.class,
                homeworkId);
        int versionNo = (maxVersion == null ? 0 : maxVersion) + 1;
        List<Map<String, Object>> validRows = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            Map<String, Object> row = rows.get(index);
            String studentNo = value(row.get("studentNo"));
            int seqNo = intValue(row.get("seqNo"), 0);
            List<Map<String, Object>> students = jdbcTemplate.queryForList(
                    """
                    SELECT id FROM app_user
                    WHERE classId = (SELECT classId FROM course_homework WHERE id = ?)
                      AND studentNo = ? AND role = 'STUDENT'
                    """,
                    homeworkId,
                    studentNo);
            List<Map<String, Object>> questions = jdbcTemplate.queryForList(
                    """
                    SELECT id, score FROM course_question
                    WHERE homeworkId = ? AND seqNo = ? AND isDeleted = 0
                    """,
                    homeworkId,
                    seqNo);
            if (students.isEmpty() || questions.isEmpty()) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("rowNumber", index + 2);
                error.put("field", students.isEmpty() ? "studentNo" : "questionSeq");
                error.put("message", students.isEmpty()
                        ? "班级中不存在该学号"
                        : "作业中不存在该题号");
                error.put("rejectedValue", studentNo);
                errors.add(error);
                continue;
            }
            Map<String, Object> normalized = new LinkedHashMap<>(row);
            normalized.put("studentId", students.get(0).get("id"));
            normalized.put("questionId", questions.get(0).get("id"));
            normalized.put("maxScore", questions.get(0).get("score"));
            validRows.add(normalized);
        }
        if (!errors.isEmpty() && !skipMissing) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "IMPORT_VALIDATION_FAILED",
                    "批改数据存在无效行。",
                    Map.of("rowErrors", errors));
        }
        if (validRows.isEmpty()) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "EMPTY_VALID_IMPORT",
                    "没有有效批改记录。",
                    Map.of("rowErrors", errors));
        }
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        int finalVersionNo = versionNo;
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO grading_batch
                      (homeworkId, versionNo, source, importedBy,
                       totalRows, validRows, remark)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, homeworkId);
            statement.setInt(2, finalVersionNo);
            statement.setString(3, source);
            statement.setInt(4, teacherId);
            statement.setInt(5, rows.size());
            statement.setInt(6, validRows.size());
            statement.setString(7, remark);
            return statement;
        }, keyHolder);
        int batchId = keyHolder.getKey() == null
                ? 0
                : keyHolder.getKey().intValue();
        for (Map<String, Object> row : validRows) {
            boolean correct = Boolean.TRUE.equals(row.get("isCorrect"));
            BigDecimal score = decimal(
                    row.get("score"),
                    BigDecimal.ZERO);
            jdbcTemplate.update(
                    """
                    INSERT INTO grading_record
                      (batchId, homeworkId, studentId, questionId, seqNo,
                       studentAnswer, isCorrect, score, teacherNote, errorTag)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    batchId,
                    homeworkId,
                    intValue(row.get("studentId"), 0),
                    intValue(row.get("questionId"), 0),
                    intValue(row.get("seqNo"), 0),
                    value(row.get("studentAnswer")),
                    correct,
                    score,
                    value(row.get("teacherNote")),
                    value(row.get("errorTag")));
        }
        Map<String, Object> batch = batchById(batchId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batch", batch);
        result.put("currentBatchId", batchId);
        result.put("statisticsCalculated", true);
        if (!errors.isEmpty()) {
            result.put("rowErrors", errors);
        }
        return result;
    }

    private Map<String, Object> classById(int teacherId, int classId) {
        return listClasses(teacherId).stream()
                .filter(item -> ((Number) item.get("id")).intValue() == classId)
                .findFirst()
                .orElseThrow(() -> notFound("班级不存在。"));
    }

    private void requireClass(int teacherId, int classId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom WHERE id = ? AND teacherId = ?",
                Integer.class,
                classId,
                teacherId);
        if (count == null || count == 0) throw notFound("班级不存在。");
    }

    private Map<String, Object> studentByNo(int classId, String studentNo) {
        return jdbcTemplate.query(
                """
                SELECT id, studentNo, displayName, status
                FROM app_user
                WHERE classId = ? AND studentNo = ? AND role = 'STUDENT'
                """,
                (row, index) -> studentMap(row),
                classId,
                studentNo).stream().findFirst().orElseThrow(
                        () -> notFound("学生不存在。"));
    }

    private Map<String, Object> studentById(int classId, int studentId) {
        return jdbcTemplate.query(
                """
                SELECT id, studentNo, displayName, status
                FROM app_user
                WHERE classId = ? AND id = ? AND role = 'STUDENT'
                """,
                (row, index) -> studentMap(row),
                classId,
                studentId).stream().findFirst().orElseThrow(
                        () -> notFound("学生不存在。"));
    }

    private Map<String, Object> studentMap(java.sql.ResultSet row)
            throws java.sql.SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getInt("id"));
        item.put("studentNo", row.getString("studentNo"));
        item.put("name", row.getString("displayName"));
        item.put("status", row.getString("status"));
        return item;
    }

    private Map<String, Object> templateById(int templateId) {
        return jdbcTemplate.query(
                """
                SELECT t.*, u.displayName AS createdByName,
                       r.displayName AS reviewedByName
                FROM comment_template t
                JOIN app_user u ON u.id = t.createdBy
                LEFT JOIN app_user r ON r.id = t.reviewedBy
                WHERE t.id = ?
                """,
                (row, index) -> templateMap(row),
                templateId).stream().findFirst().orElseThrow(
                        () -> notFound("模板不存在。"));
    }

    private Map<String, Object> templateMap(java.sql.ResultSet row)
            throws java.sql.SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getInt("id"));
        item.put("title", row.getString("title"));
        item.put("category", row.getString("category"));
        item.put("structure", parseJson(row.getString("structure")));
        item.put("content", row.getString("content"));
        item.put("status", row.getString("status"));
        item.put("createdBy", row.getInt("createdBy"));
        item.put("createdByName", row.getString("createdByName"));
        item.put("reviewedBy", row.getObject("reviewedBy"));
        item.put("reviewNote", row.getString("reviewNote"));
        item.put("reviewedAt", instant(row.getTimestamp("reviewedAt")));
        item.put("createdAt", instant(row.getTimestamp("createdAt")));
        item.put("updatedAt", instant(row.getTimestamp("updatedAt")));
        return item;
    }

    private Map<String, Object> homeworkSummary(java.sql.ResultSet row)
            throws java.sql.SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getInt("id"));
        item.put("classId", row.getInt("classId"));
        item.put("className", row.getString("className"));
        item.put("title", row.getString("title"));
        item.put("status", row.getString("status"));
        item.put("dueAt", instant(row.getTimestamp("dueAt")));
        item.put("publishedAt", instant(row.getTimestamp("publishedAt")));
        item.put("questionCount", row.getInt("questionCount"));
        item.put("studentCount", row.getInt("studentCount"));
        item.put("currentGradingBatchId", row.getObject("currentGradingBatchId"));
        item.put("currentGradingBatchVersion", row.getObject("currentGradingBatchVersion"));
        item.put("handoutId", row.getObject("handoutId"));
        item.put("handoutStatus", row.getString("handoutStatus"));
        item.put("createdAt", instant(row.getTimestamp("createdAt")));
        item.put("updatedAt", instant(row.getTimestamp("updatedAt")));
        item.put("version", row.getInt("version"));
        item.put("description", row.getString("description"));
        return item;
    }

    private List<Map<String, Object>> questions(int homeworkId) {
        Map<Integer, List<Integer>> knowledge = new LinkedHashMap<>();
        jdbcTemplate.query(
                """
                SELECT questionId, knowledgePointId
                FROM course_question_kp
                WHERE questionId IN (
                  SELECT id FROM course_question WHERE homeworkId = ?
                )
                """,
                rows -> {
                    knowledge.computeIfAbsent(
                            rows.getInt("questionId"),
                            ignored -> new ArrayList<>()).add(
                                    rows.getInt("knowledgePointId"));
                },
                homeworkId);
        return jdbcTemplate.query(
                """
                SELECT id, homeworkId, seqNo, questionType, stem, options,
                       standardAnswer, teacherAnalysis, score, revisionNo
                FROM course_question
                WHERE homeworkId = ? AND isDeleted = 0
                ORDER BY seqNo, id
                """,
                (row, index) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.getInt("id"));
                    item.put("homeworkId", row.getInt("homeworkId"));
                    item.put("seqNo", row.getInt("seqNo"));
                    item.put("questionType", row.getString("questionType"));
                    item.put("stem", row.getString("stem"));
                    item.put("options", parseJson(row.getString("options")));
                    item.put("standardAnswer", row.getString("standardAnswer"));
                    item.put("teacherAnalysis", row.getString("teacherAnalysis"));
                    item.put("score", row.getInt("score"));
                    item.put("knowledgePointIds",
                            knowledge.getOrDefault(row.getInt("id"), List.of()));
                    item.put("revisionNo", row.getInt("revisionNo"));
                    return item;
                },
                homeworkId);
    }

    private Map<String, Object> questionById(int questionId) {
        Map<String, Object> result = jdbcTemplate.queryForMap(
                """
                SELECT id, homeworkId, seqNo, questionType, stem, options,
                       standardAnswer, teacherAnalysis, score, revisionNo
                FROM course_question
                WHERE id = ? AND isDeleted = 0
                """,
                questionId);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", result.get("id"));
        item.put("homeworkId", result.get("homeworkId"));
        item.put("seqNo", result.get("seqNo"));
        item.put("questionType", result.get("questionType"));
        item.put("stem", result.get("stem"));
        item.put("options", parseJson(value(result.get("options"))));
        item.put("standardAnswer", result.get("standardAnswer"));
        item.put("teacherAnalysis", result.get("teacherAnalysis"));
        item.put("score", result.get("score"));
        item.put("knowledgePointIds", jdbcTemplate.queryForList(
                """
                SELECT knowledgePointId FROM course_question_kp
                WHERE questionId = ?
                """,
                Integer.class,
                questionId));
        item.put("revisionNo", result.get("revisionNo"));
        return item;
    }

    private int nextQuestionSeq(int homeworkId) {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(seqNo), 0) FROM course_question WHERE homeworkId = ?",
                Integer.class,
                homeworkId);
        return (max == null ? 0 : max) + 1;
    }

    private void attachKnowledgePoints(
            int questionId,
            JsonNode ids) {
        if (!ids.isArray()) return;
        for (JsonNode id : ids) {
            if (!id.canConvertToInt()) continue;
            try {
                jdbcTemplate.update(
                        """
                        INSERT IGNORE INTO course_question_kp
                          (questionId, knowledgePointId)
                        VALUES (?, ?)
                        """,
                        questionId,
                        id.asInt());
            } catch (Exception ignored) {
                // Ignore stale knowledge point ids from an old browser tab.
            }
        }
    }

    private void touchHomework(int homeworkId) {
        jdbcTemplate.update(
                "UPDATE course_homework SET version = version + 1 WHERE id = ?",
                homeworkId);
    }

    private int requireHomeworkVersion(
            int teacherId,
            int homeworkId,
            int expectedVersion) {
        int current = currentHomeworkVersion(teacherId, homeworkId);
        if (expectedVersion >= 0 && expectedVersion != current) {
            throw conflict("作业已被其他操作修改，请刷新后重试。");
        }
        return current;
    }

    private int currentHomeworkVersion(int teacherId, int homeworkId) {
        List<Integer> versions = jdbcTemplate.query(
                """
                SELECT h.version
                FROM course_homework h
                JOIN classroom c ON c.id = h.classId
                WHERE h.id = ? AND c.teacherId = ?
                """,
                (rows, index) -> rows.getInt("version"),
                homeworkId,
                teacherId);
        if (versions.isEmpty()) throw notFound("作业不存在。");
        return versions.get(0);
    }

    private void requireHomework(int teacherId, int homeworkId) {
        currentHomeworkVersion(teacherId, homeworkId);
    }

    private void requireHomeworkEditable(int teacherId, int homeworkId) {
        String status = workflowMapper.selectHomeworkStatus(
                teacherId,
                homeworkId);
        if (status == null) throw notFound("作业不存在。");
        if ("ARCHIVED".equals(status)) {
            throw new PlatformException(
                    HttpStatus.CONFLICT,
                    "HOMEWORK_ARCHIVED",
                    "作业已归档，不能继续修改题目或批改记录。");
        }
    }

    private Integer currentBatchId(int homeworkId) {
        return jdbcTemplate.query(
                """
                SELECT id FROM grading_batch
                WHERE homeworkId = ?
                ORDER BY versionNo DESC LIMIT 1
                """,
                rows -> rows.next() ? rows.getInt(1) : null,
                homeworkId);
    }

    private Map<String, Object> batchById(int batchId) {
        return jdbcTemplate.query(
                """
                SELECT gb.*, u.displayName AS importedByName
                FROM grading_batch gb
                LEFT JOIN app_user u ON u.id = gb.importedBy
                WHERE gb.id = ?
                """,
                (row, index) -> batchMap(row),
                batchId).stream().findFirst().orElseThrow(
                        () -> notFound("批改批次不存在。"));
    }

    private Map<String, Object> batchMap(java.sql.ResultSet row)
            throws java.sql.SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getInt("id"));
        item.put("homeworkId", row.getInt("homeworkId"));
        item.put("versionNo", row.getInt("versionNo"));
        item.put("source", row.getString("source"));
        item.put("importedBy", row.getInt("importedBy"));
        item.put("importedByName", row.getObject("importedByName"));
        item.put("totalRows", row.getInt("totalRows"));
        item.put("validRows", row.getInt("validRows"));
        item.put("remark", row.getString("remark"));
        item.put("createdAt", instant(row.getTimestamp("createdAt")));
        return item;
    }

    private Map<String, Object> gradingRecordMap(
            java.sql.ResultSet row) throws java.sql.SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getInt("id"));
        item.put("batchId", row.getInt("batchId"));
        item.put("homeworkId", row.getInt("homeworkId"));
        item.put("studentId", row.getInt("studentId"));
        item.put("studentNo", row.getString("studentNo"));
        item.put("studentName", row.getString("studentName"));
        item.put("questionId", row.getInt("questionId"));
        item.put("seqNo", row.getInt("seqNo"));
        item.put("studentAnswer", row.getString("studentAnswer"));
        item.put("isCorrect", row.getBoolean("isCorrect"));
        item.put("score", row.getBigDecimal("score"));
        item.put("teacherNote", row.getString("teacherNote"));
        item.put("errorTag", row.getString("errorTag"));
        item.put("createdAt", instant(row.getTimestamp("createdAt")));
        return item;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> gradingRecordMap(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.get("id"));
        item.put("batchId", row.get("batchId"));
        item.put("homeworkId", row.get("homeworkId"));
        item.put("studentId", row.get("studentId"));
        item.put("studentNo", row.get("studentNo"));
        item.put("studentName", row.get("studentName"));
        item.put("questionId", row.get("questionId"));
        item.put("seqNo", row.get("seqNo"));
        item.put("studentAnswer", row.get("studentAnswer"));
        item.put("isCorrect", row.get("isCorrect"));
        item.put("score", row.get("score"));
        item.put("teacherNote", row.get("teacherNote"));
        item.put("errorTag", row.get("errorTag"));
        item.put("createdAt", row.get("createdAt"));
        return item;
    }

    private Map<String, Object> emptyStatistics(int homeworkId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("homeworkId", homeworkId);
        result.put("batchId", 0);
        result.put("batchVersionNo", 0);
        result.put("calculatedAt", OffsetDateTime.now().toString());
        result.put("overview", Map.of(
                "studentCount", 0,
                "questionCount", 0,
                "totalWrong", 0,
                "totalUnanswered", 0,
                "averageScore", 0));
        result.put("questions", List.of());
        result.put("knowledgePoints", List.of());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
    }

    private Map<String, Object> page(
            List<Map<String, Object>> rows,
            int page,
            int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        int from = Math.min(rows.size(), (safePage - 1) * safeSize);
        int to = Math.min(rows.size(), from + safeSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", rows.subList(from, to));
        result.put("page", safePage);
        result.put("pageSize", safeSize);
        result.put("total", rows.size());
        return result;
    }

    private void appendUpdate(
            List<String> updates,
            List<Object> arguments,
            String column,
            JsonNode body,
            String field) {
        String value = nullableText(body, field);
        if (value == null) return;
        updates.add(column + " = ?");
        arguments.add(value);
    }

    private String requiredText(
            JsonNode body,
            String field,
            String message) {
        String value = nullableText(body, field);
        if (value == null || value.isBlank()) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_ERROR",
                    message);
        }
        return value;
    }

    private String text(JsonNode body, String field, String fallback) {
        if (body == null || !body.has(field) || body.get(field).isNull()) {
            return fallback;
        }
        return body.get(field).asText(fallback);
    }

    private String nullableText(JsonNode body, String field) {
        return text(body, field, null);
    }

    private int integer(JsonNode body, String field, int fallback) {
        if (body == null || !body.has(field) || body.get(field).isNull()) {
            return fallback;
        }
        return body.get(field).asInt(fallback);
    }

    private boolean booleanValue(
            JsonNode body,
            String field,
            boolean fallback) {
        if (body == null || !body.has(field) || body.get(field).isNull()) {
            return fallback;
        }
        return body.get(field).asBoolean(fallback);
    }

    private BigDecimal decimal(
            JsonNode body,
            String field,
            BigDecimal fallback) {
        if (body == null || !body.has(field) || body.get(field).isNull()) {
            return fallback;
        }
        return body.get(field).decimalValue();
    }

    private BigDecimal decimal(Object value, BigDecimal fallback) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception error) {
            return fallback;
        }
    }

    private int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private String value(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null
                || value.isBlank()
                || "null".equalsIgnoreCase(value.trim());
    }

    private void requireCommentaryLength(String content) {
        int length = content.codePointCount(0, content.length());
        if (length > 2000) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "COMMENTARY_TOO_LONG",
                    "讲评正文最多 2000 个字符。",
                    Map.of("maxLength", 2000, "actualLength", length));
        }
    }

    private String normalizeQuestionType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if ("SINGLE_CORRECT".equals(normalized)) {
            return "SINGLE_CHOICE";
        }
        if ("SINGLE_CHOICE".equals(normalized)
                || "TRUE_FALSE".equals(normalized)) {
            return normalized;
        }
        throw new PlatformException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_QUESTION_TYPE",
                "第一版只支持单选题和判断题。");
    }

    private Object timestampOrNull(JsonNode body, String field) {
        String value = nullableText(body, field);
        if (value == null || value.isBlank()) return null;
        try {
            return java.time.OffsetDateTime.parse(value)
                    .atZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai"))
                    .toLocalDateTime();
        } catch (Exception ignored) {
            return java.time.LocalDateTime.parse(value);
        }
    }

    private String jsonString(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception error) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_JSON",
                    "数据格式不合法。",
                    error);
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

    private String instant(java.sql.Timestamp value) {
        return value == null
                ? null
                : value.toInstant().toString();
    }

    private PlatformException notFound(String message) {
        return new PlatformException(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                message);
    }

    private PlatformException conflict(String message) {
        return new PlatformException(
                HttpStatus.CONFLICT,
                "VERSION_CONFLICT",
                message);
    }

    private record CommentaryDraft(
            Map<String, Object> row,
            String content,
            String status,
            String errorCode,
            long elapsedMs) {}
}
