package com.zuoye.mark.service;

import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MvpPersistenceService {

    private final JdbcTemplate jdbcTemplate;

    public MvpPersistenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<StoredJson> findReview(String assignmentId) {
        return find(
                "SELECT draftJson, status, version FROM math_ai_review_draft WHERE assignmentId = ?",
                assignmentId,
                (rows, index) -> new StoredJson(
                        rows.getString("draftJson"),
                        rows.getString("status"),
                        rows.getInt("version")));
    }

    public void saveReview(
            String assignmentId,
            String json,
            String status,
            int version) {
        jdbcTemplate.update(
                """
                INSERT INTO math_ai_review_draft
                  (assignmentId, draftJson, status, version, updatedAt)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE
                  draftJson = VALUES(draftJson),
                  status = VALUES(status),
                  version = VALUES(version),
                  updatedAt = CURRENT_TIMESTAMP(3)
                """,
                assignmentId,
                json,
                status,
                version);
    }

    public Optional<String> findSubmission(String assignmentId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT resultJson FROM math_ai_student_submission WHERE assignmentId = ?",
                    String.class,
                    assignmentId));
        } catch (EmptyResultDataAccessException error) {
            return Optional.empty();
        }
    }

    public void saveSubmission(String assignmentId, String json) {
        jdbcTemplate.update(
                """
                INSERT INTO math_ai_student_submission
                  (assignmentId, resultJson, updatedAt)
                VALUES (?, ?, CURRENT_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE
                  resultJson = VALUES(resultJson),
                  updatedAt = CURRENT_TIMESTAMP(3)
                """,
                assignmentId,
                json);
    }

    public List<StoredCorrection> findCorrections(String assignmentId) {
        return jdbcTemplate.query(
                """
                SELECT id, recordJson, status
                FROM math_ai_student_correction
                WHERE assignmentId = ?
                ORDER BY submittedAt DESC
                """,
                (rows, index) -> new StoredCorrection(
                        rows.getString("id"),
                        rows.getString("recordJson"),
                        rows.getString("status")),
                assignmentId);
    }

    public void saveCorrection(
            String id,
            String assignmentId,
            String json,
            String status) {
        jdbcTemplate.update(
                """
                INSERT INTO math_ai_student_correction
                  (id, assignmentId, recordJson, status, submittedAt)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE
                  recordJson = VALUES(recordJson),
                  status = VALUES(status)
                """,
                id,
                assignmentId,
                json,
                status);
    }

    public Optional<StoredCorrection> findCorrection(String correctionId) {
        return find(
                """
                SELECT id, recordJson, status
                FROM math_ai_student_correction
                WHERE id = ?
                """,
                correctionId,
                (rows, index) -> new StoredCorrection(
                        rows.getString("id"),
                        rows.getString("recordJson"),
                        rows.getString("status")));
    }

    private <T> Optional<T> find(
            String sql,
            Object argument,
            org.springframework.jdbc.core.RowMapper<T> mapper) {
        List<T> rows = jdbcTemplate.query(sql, mapper, argument);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public record StoredJson(String json, String status, int version) {}

    public record StoredCorrection(String id, String json, String status) {}
}
