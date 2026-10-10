package com.appvalidateexcel.storage.repository;

import com.appvalidateexcel.storage.model.ImportModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ImportRepository {

    private final JdbcTemplate jdbcTemplate;

    public ImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ImportModel> rowMapper = (rs, rowNum) -> {
        ImportModel.ImportModelBuilder builder = ImportModel.builder()
                .id(rs.getString("id"))
                .fileName(rs.getString("file_name"))
                .fileHash(rs.getString("file_hash"))
                .storedFilePath(rs.getString("stored_file_path"))
                .status(rs.getString("status"))
                .totalRows(rs.getInt("total_rows"))
                .acceptedRows(rs.getInt("accepted_rows"))
                .rejectedRows(rs.getInt("rejected_rows"))
                .errorSummary(rs.getString("error_summary"))
                .createdAt(rs.getString("created_at"))
                .completedAt(rs.getString("completed_at"));

        try { builder.parentUnitId(rs.getString("parent_unit_id")); } catch (Exception ignored) {}
        try { builder.fileUnitId(rs.getString("file_unit_id")); } catch (Exception ignored) {}
        try { builder.originalFileName(rs.getString("original_file_name")); } catch (Exception ignored) {}
        try { builder.displayUnitName(rs.getString("display_unit_name")); } catch (Exception ignored) {}
        try { builder.parserMode(rs.getString("parser_mode")); } catch (Exception ignored) {}
        try { builder.templateSignature(rs.getString("template_signature")); } catch (Exception ignored) {}
        try { builder.currentRecordCount(rs.getInt("current_record_count")); } catch (Exception ignored) {}

        return builder.build();
    };

    public void save(ImportModel model) {
        String now = Instant.now().toString();
        if (model.getCreatedAt() == null) model.setCreatedAt(now);

        jdbcTemplate.update(
                "INSERT INTO imports (id, file_name, file_hash, stored_file_path, status, total_rows, accepted_rows, rejected_rows, error_summary, parent_unit_id, file_unit_id, original_file_name, display_unit_name, parser_mode, template_signature, created_at, completed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET " +
                "file_name = excluded.file_name, " +
                "file_hash = excluded.file_hash, " +
                "stored_file_path = excluded.stored_file_path, " +
                "status = excluded.status, " +
                "total_rows = excluded.total_rows, " +
                "accepted_rows = excluded.accepted_rows, " +
                "rejected_rows = excluded.rejected_rows, " +
                "error_summary = excluded.error_summary, " +
                "parent_unit_id = excluded.parent_unit_id, " +
                "file_unit_id = excluded.file_unit_id, " +
                "original_file_name = excluded.original_file_name, " +
                "display_unit_name = excluded.display_unit_name, " +
                "parser_mode = excluded.parser_mode, " +
                "template_signature = excluded.template_signature, " +
                "completed_at = excluded.completed_at",
                model.getId(),
                model.getFileName(),
                model.getFileHash(),
                model.getStoredFilePath(),
                model.getStatus(),
                model.getTotalRows() != null ? model.getTotalRows() : 0,
                model.getAcceptedRows() != null ? model.getAcceptedRows() : 0,
                model.getRejectedRows() != null ? model.getRejectedRows() : 0,
                model.getErrorSummary(),
                model.getParentUnitId(),
                model.getFileUnitId(),
                model.getOriginalFileName(),
                model.getDisplayUnitName(),
                model.getParserMode() != null ? model.getParserMode() : "STANDARD_CTC",
                model.getTemplateSignature(),
                model.getCreatedAt(),
                model.getCompletedAt()
        );
    }

    public void update(ImportModel model) {
        jdbcTemplate.update(
                "UPDATE imports SET status = ?, total_rows = ?, accepted_rows = ?, rejected_rows = ?, error_summary = ?, parent_unit_id = ?, file_unit_id = ?, original_file_name = ?, display_unit_name = ?, parser_mode = ?, template_signature = ?, completed_at = ? WHERE id = ?",
                model.getStatus(),
                model.getTotalRows(),
                model.getAcceptedRows(),
                model.getRejectedRows(),
                model.getErrorSummary(),
                model.getParentUnitId(),
                model.getFileUnitId(),
                model.getOriginalFileName(),
                model.getDisplayUnitName(),
                model.getParserMode(),
                model.getTemplateSignature(),
                model.getCompletedAt(),
                model.getId()
        );
    }

    public Optional<ImportModel> findById(String id) {
        List<ImportModel> list = jdbcTemplate.query(
                "SELECT i.*, (SELECT COUNT(*) FROM personnel_records r WHERE r.import_id = i.id) AS current_record_count " +
                "FROM imports i WHERE i.id = ?",
                rowMapper,
                id
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<ImportModel> findByFileHash(String fileHash) {
        if (fileHash == null || fileHash.trim().isEmpty()) return Optional.empty();
        List<ImportModel> list = jdbcTemplate.query(
                "SELECT i.*, (SELECT COUNT(*) FROM personnel_records r WHERE r.import_id = i.id) AS current_record_count " +
                "FROM imports i WHERE i.file_hash = ? ORDER BY i.created_at DESC LIMIT 1",
                rowMapper,
                fileHash.trim()
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<ImportModel> findAll() {
        return jdbcTemplate.query(
                "SELECT i.*, (SELECT COUNT(*) FROM personnel_records r WHERE r.import_id = i.id) AS current_record_count " +
                "FROM imports i ORDER BY i.created_at DESC",
                rowMapper
        );
    }

    public void deleteImport(String importId) {
        jdbcTemplate.update("DELETE FROM personnel_records WHERE import_id = ?", importId);
        jdbcTemplate.update("DELETE FROM imports WHERE id = ?", importId);
    }

    public int countImportsReferencingUnit(String unitId) {
        if (unitId == null || unitId.trim().isEmpty()) return 0;
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM imports WHERE parent_unit_id = ? OR file_unit_id = ?",
                Integer.class,
                unitId, unitId
        );
        return count != null ? count : 0;
    }

    public void clearUnitReferences(String unitId) {
        if (unitId == null || unitId.trim().isEmpty()) return;
        jdbcTemplate.update("UPDATE imports SET parent_unit_id = NULL WHERE parent_unit_id = ?", unitId);
        jdbcTemplate.update("UPDATE imports SET file_unit_id = NULL WHERE file_unit_id = ?", unitId);
    }

    public void reassignUnitReferences(String sourceUnitId, String targetUnitId) {
        if (sourceUnitId == null || targetUnitId == null) return;
        jdbcTemplate.update("UPDATE imports SET parent_unit_id = ? WHERE parent_unit_id = ?", targetUnitId, sourceUnitId);
        jdbcTemplate.update("UPDATE imports SET file_unit_id = ? WHERE file_unit_id = ?", targetUnitId, sourceUnitId);
    }

    public void syncAllImportsStatus() {
        jdbcTemplate.update(
                "UPDATE imports " +
                "SET status = 'CLEARED', accepted_rows = 0 " +
                "WHERE id IN (" +
                "    SELECT i.id FROM imports i " +
                "    LEFT JOIN personnel_records r ON r.import_id = i.id " +
                "    GROUP BY i.id " +
                "    HAVING COUNT(r.id) = 0" +
                ") AND status != 'CLEARED'"
        );
    }
}
