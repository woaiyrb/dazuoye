package com.zuoye.mark.config;

import com.zuoye.mark.service.PasswordService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoDataInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordService passwordService;

    public DemoDataInitializer(
            JdbcTemplate jdbcTemplate,
            PasswordService passwordService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordService = passwordService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String passwordHash = passwordService.encode("123456");
        ensureUser(
                "teacher01",
                passwordHash,
                "张老师",
                "TEACHER",
                null);
        ensureUser(
                "research01",
                passwordHash,
                "李教研",
                "RESEARCH",
                null);

        int teacherId = userId("teacher01");
        int classId = ensureClass(teacherId);
        ensureUser(
                "S2024013",
                passwordHash,
                "小明",
                "STUDENT",
                "S2024013");
        ensureUser(
                "S2024014",
                passwordHash,
                "小红",
                "STUDENT",
                "S2024014");
        jdbcTemplate.update(
                """
                UPDATE app_user
                SET classId = ?
                WHERE username IN ('S2024013', 'S2024014')
                """,
                classId);

        ensureKnowledgePoints();
        ensureHomework(classId, teacherId);
    }

    private void ensureUser(
            String username,
            String passwordHash,
            String displayName,
            String role,
            String studentNo) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE username = ?",
                Integer.class,
                username);
        if (count != null && count > 0) {
            jdbcTemplate.update(
                    """
                    UPDATE app_user
                    SET displayName = ?, role = ?, status = 'ACTIVE',
                        studentNo = COALESCE(studentNo, ?)
                    WHERE username = ?
                    """,
                    displayName,
                    role,
                    studentNo,
                    username);
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO app_user
                  (username, passwordHash, displayName, role, status, studentNo)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?)
                """,
                username,
                passwordHash,
                displayName,
                role,
                studentNo);
    }

    private int ensureClass(int teacherId) {
        List<Integer> ids = jdbcTemplate.query(
                """
                SELECT id FROM classroom
                WHERE teacherId = ? AND name = '初一（1）班'
                LIMIT 1
                """,
                (rows, index) -> rows.getInt("id"),
                teacherId);
        if (!ids.isEmpty()) return ids.get(0);
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO classroom (name, grade, subject, status, teacherId)
                    VALUES ('初一（1）班', '七年级', '数学', 'ACTIVE', ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, teacherId);
            return statement;
        }, keyHolder);
        return keyHolder.getKey() == null ? 1 : keyHolder.getKey().intValue();
    }

    private void ensureKnowledgePoints() {
        insertKnowledgePoint("MATH-7-01", "有理数加法", null, 1);
        insertKnowledgePoint("MATH-7-02", "绝对值", null, 1);
        insertKnowledgePoint("MATH-7-03", "一元一次方程", null, 1);
    }

    private void insertKnowledgePoint(
            String code,
            String name,
            Integer parentId,
            int level) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kp_node WHERE code = ?",
                Integer.class,
                code);
        if (count != null && count > 0) return;
        jdbcTemplate.update(
                """
                INSERT INTO kp_node
                  (parentId, code, name, kpLevel, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """,
                parentId,
                code,
                name,
                level);
    }

    private void ensureHomework(int classId, int teacherId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_homework",
                Integer.class);
        if (count != null && count > 0) return;
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO course_homework
                      (classId, teacherId, title, description, status,
                       publishedAt, version)
                    VALUES (?, ?, ?, ?, 'PUBLISHED', CURRENT_TIMESTAMP(3), 1)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, classId);
            statement.setInt(2, teacherId);
            statement.setString(3, "初一数学·有理数基础练习");
            statement.setString(
                    4,
                    "通过选择题和判断题复习有理数加法、绝对值与一元一次方程。");
            return statement;
        }, keyHolder);
        int homeworkId = keyHolder.getKey() == null
                ? 1
                : keyHolder.getKey().intValue();
        insertQuestion(
                homeworkId,
                1,
                "SINGLE_CHOICE",
                "计算：(-2) + 5 = ?",
                """
                [{"key":"A","text":"2"},{"key":"B","text":"3"},
                 {"key":"C","text":"-3"},{"key":"D","text":"-7"}]
                """,
                "B",
                "先确定符号，再计算绝对值。",
                1,
                1);
        insertQuestion(
                homeworkId,
                2,
                "TRUE_FALSE",
                "绝对值等于 3 的数只有 3。",
                """
                [{"key":"A","text":"正确"},{"key":"B","text":"错误"}]
                """,
                "B",
                "绝对值等于 3 的数是 3 和 -3。",
                1,
                2);
        insertQuestion(
                homeworkId,
                3,
                "SINGLE_CHOICE",
                "方程 3x = 12 的解是 x = ?",
                """
                [{"key":"A","text":"2"},{"key":"B","text":"3"},
                 {"key":"C","text":"4"},{"key":"D","text":"6"}]
                """,
                "C",
                "方程两边同时除以 3。",
                1,
                3);
    }

    private void insertQuestion(
            int homeworkId,
            int seqNo,
            String type,
            String stem,
            String options,
            String answer,
            String analysis,
            int score,
            int knowledgePointId) {
        jdbcTemplate.update(
                """
                INSERT INTO course_question
                  (homeworkId, seqNo, questionType, stem, options,
                   standardAnswer, teacherAnalysis, score, revisionNo)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
                """,
                homeworkId,
                seqNo,
                type,
                stem,
                options,
                answer,
                analysis,
                score);
        Integer questionId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM course_question
                WHERE homeworkId = ? AND seqNo = ?
                """,
                Integer.class,
                homeworkId,
                seqNo);
        if (questionId != null) {
            jdbcTemplate.update(
                    """
                    INSERT IGNORE INTO course_question_kp
                      (questionId, knowledgePointId)
                    VALUES (?, ?)
                    """,
                    questionId,
                    knowledgePointId);
        }
    }

    private int userId(String username) {
        Integer id = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE username = ?",
                Integer.class,
                username);
        return id == null ? 0 : id;
    }
}
