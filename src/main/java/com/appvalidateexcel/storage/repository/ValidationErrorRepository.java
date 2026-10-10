package com.appvalidateexcel.storage.repository;

import com.appvalidateexcel.storage.model.ValidationErrorModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Repository
public class ValidationErrorRepository {

    private final JdbcTemplate jdbcTemplate;

    public ValidationErrorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ValidationErrorModel> rowMapper = (rs, rowNum) -> ValidationErrorModel.builder()
            .id(rs.getString("id"))
            .recordId(rs.getString("record_id"))
            .columnNumber(rs.getObject("column_number") != null ? rs.getInt("column_number") : null)
            .errorCode(rs.getString("error_code"))
            .message(rs.getString("message"))
            .actualValue(rs.getString("actual_value"))
            .expectedValue(rs.getString("expected_value"))
            .createdAt(rs.getString("created_at"))
            .build();

    public void saveBatch(List<ValidationErrorModel> errors) {
        if (errors == null || errors.isEmpty()) return;

        String sql = "INSERT INTO validation_errors (id, record_id, column_number, error_code, message, actual_value, expected_value, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        String now = Instant.now().toString();
        List<Object[]> batchArgs = errors.stream().map(err -> new Object[]{
                err.getId() != null ? err.getId() : UUID.randomUUID().toString(),
                err.getRecordId(),
                err.getColumnNumber(),
                err.getErrorCode(),
                err.getMessage(),
                err.getActualValue(),
                err.getExpectedValue(),
                err.getCreatedAt() != null ? err.getCreatedAt() : now
        }).toList();

        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    public List<ValidationErrorModel> findByRecordId(String recordId) {
        if (recordId == null) return Collections.emptyList();
        return jdbcTemplate.query(
                "SELECT * FROM validation_errors WHERE record_id = ? ORDER BY column_number ASC",
                rowMapper,
                recordId
        );
    }

    public void deleteByRecordId(String recordId) {
        jdbcTemplate.update("DELETE FROM validation_errors WHERE record_id = ?", recordId);
    }

    public void deleteByRecordIds(List<String> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) return;
        int chunkSize = 500;
        for (int i = 0; i < recordIds.size(); i += chunkSize) {
            List<String> chunk = recordIds.subList(i, Math.min(i + chunkSize, recordIds.size()));
            String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
            jdbcTemplate.update("DELETE FROM validation_errors WHERE record_id IN (" + placeholders + ")", chunk.toArray());
        }
    }

    public java.util.Map<String, List<ValidationErrorModel>> findByRecordIds(List<String> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) return Collections.emptyMap();
        java.util.Map<String, List<ValidationErrorModel>> map = new java.util.HashMap<>();
        int chunkSize = 500;
        for (int i = 0; i < recordIds.size(); i += chunkSize) {
            List<String> chunk = recordIds.subList(i, Math.min(i + chunkSize, recordIds.size()));
            String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
            String sql = "SELECT * FROM validation_errors WHERE record_id IN (" + placeholders + ") ORDER BY column_number ASC";
            List<ValidationErrorModel> list = jdbcTemplate.query(sql, rowMapper, chunk.toArray());
            for (ValidationErrorModel err : list) {
                map.computeIfAbsent(err.getRecordId(), k -> new java.util.ArrayList<>()).add(err);
            }
        }
        return map;
    }
}
