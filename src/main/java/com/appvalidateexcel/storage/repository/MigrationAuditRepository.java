package com.appvalidateexcel.storage.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class MigrationAuditRepository {

    private final JdbcTemplate jdbcTemplate;

    public MigrationAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void logMigration(String migrationId, String sourceType, int recordCount, int unitCount, String checksum, String status, String detailsJson) {
        jdbcTemplate.update(
                "INSERT INTO migration_audit (id, migration_id, source_type, record_count, unit_count, checksum, status, details_json, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(),
                migrationId,
                sourceType,
                recordCount,
                unitCount,
                checksum,
                status,
                detailsJson,
                Instant.now().toString()
        );
    }

    public List<Map<String, Object>> getLatestAudits(int limit) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM migration_audit ORDER BY created_at DESC LIMIT ?",
                limit
        );
    }

    public boolean isMigrationCompleted(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) return false;
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM migration_audit WHERE (migration_id = ? OR checksum = ?) AND status = 'SUCCESS'",
                Integer.class,
                identifier.trim(),
                identifier.trim()
        );
        return count != null && count > 0;
    }
}
