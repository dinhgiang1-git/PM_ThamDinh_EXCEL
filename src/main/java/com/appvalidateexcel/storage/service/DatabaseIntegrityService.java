package com.appvalidateexcel.storage.service;

import com.appvalidateexcel.storage.config.DataDirectoryResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class DatabaseIntegrityService {

    private final JdbcTemplate jdbcTemplate;
    private final DataDirectoryResolver directoryResolver;
    private final StorageMaintenanceLock maintenanceLock;

    public DatabaseIntegrityService(JdbcTemplate jdbcTemplate, DataDirectoryResolver directoryResolver, StorageMaintenanceLock maintenanceLock) {
        this.jdbcTemplate = jdbcTemplate;
        this.directoryResolver = directoryResolver;
        this.maintenanceLock = maintenanceLock;
    }

    public Map<String, Object> checkIntegrity() {
        return maintenanceLock.callWithReadAccess(() -> {
            Map<String, Object> report = new LinkedHashMap<>();

            // 1. SQLite PRAGMA integrity_check
            List<String> integrityResults = jdbcTemplate.query(
                    "PRAGMA integrity_check;",
                    (rs, rowNum) -> rs.getString(1)
            );
            boolean isOk = integrityResults.size() == 1 && "ok".equalsIgnoreCase(integrityResults.get(0));
            report.put("integrityCheck", isOk ? "PASSED" : "FAILED");
            report.put("integrityDetails", integrityResults);

            // 2. PRAGMA foreign_key_check
            List<Map<String, Object>> fkViolations = jdbcTemplate.queryForList("PRAGMA foreign_key_check;");
            report.put("foreignKeyCheck", fkViolations.isEmpty() ? "PASSED" : "VIOLATIONS_FOUND");
            report.put("foreignKeyViolations", fkViolations);

            // 3. Thông tin file và dung lượng đĩa
            Path dbPath = directoryResolver.getDatabaseFile();
            File dbFile = dbPath.toFile();
            report.put("databasePath", dbPath.toAbsolutePath().toString());
            report.put("databaseSizeBytes", dbFile.exists() ? dbFile.length() : 0);
            report.put("freeDiskSpaceBytes", dbFile.exists() ? dbFile.getFreeSpace() : 0);

            return report;
        });
    }

    public Map<String, Object> getStorageStatus() {
        return maintenanceLock.callWithReadAccess(() -> {
            Map<String, Object> status = new LinkedHashMap<>();

            Path dbPath = directoryResolver.getDatabaseFile();
            File dbFile = dbPath.toFile();

            status.put("initialized", dbFile.exists());
            status.put("databasePath", dbPath.toAbsolutePath().toString());
            status.put("databaseSizeBytes", dbFile.exists() ? dbFile.length() : 0);

            // Đếm số lượng đơn vị và hồ sơ
            try {
                Integer totalUnits = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM units", Integer.class);
                Integer totalRecords = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM personnel_records", Integer.class);
                Integer errorRecords = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM personnel_records WHERE has_errors = 1", Integer.class);

                status.put("totalUnits", totalUnits != null ? totalUnits : 0);
                status.put("totalRecords", totalRecords != null ? totalRecords : 0);
                status.put("errorRecords", errorRecords != null ? errorRecords : 0);

                // Kiểm tra có dữ liệu trong SQLite không
                status.put("hasSqliteData", (totalRecords != null && totalRecords > 0) || (totalUnits != null && totalUnits > 0));
            } catch (Exception e) {
                status.put("error", e.getMessage());
                status.put("hasSqliteData", false);
            }

            return status;
        });
    }
}
