package com.appvalidateexcel.storage.service;

import com.appvalidateexcel.storage.config.DataDirectoryResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BackupService {

    private final JdbcTemplate jdbcTemplate;
    private final DataDirectoryResolver directoryResolver;
    private final StorageMaintenanceLock maintenanceLock;

    public BackupService(JdbcTemplate jdbcTemplate, DataDirectoryResolver directoryResolver, StorageMaintenanceLock maintenanceLock) {
        this.jdbcTemplate = jdbcTemplate;
        this.directoryResolver = directoryResolver;
        this.maintenanceLock = maintenanceLock;
    }

    public Map<String, Object> createBackup() {
        return maintenanceLock.callWithReadAccess(() -> {
            Map<String, Object> result = new LinkedHashMap<>();
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + "_" + UUID.randomUUID().toString().substring(0, 6);
                String backupFileName = "app-backup-" + timestamp + ".db";
                Path backupFilePath = directoryResolver.getBackupsDirectory().resolve(backupFileName);

                // Dùng VACUUM INTO của SQLite để sao lưu an toàn khi đang mở WAL, escape dấu nháy đơn
                String targetPathStr = backupFilePath.toAbsolutePath().toString().replace('\\', '/').replace("'", "''");
                jdbcTemplate.execute("VACUUM INTO '" + targetPathStr + "'");

                File bFile = backupFilePath.toFile();
                result.put("status", "SUCCESS");
                result.put("backupFile", backupFilePath.toAbsolutePath().toString());
                result.put("backupFileName", backupFileName);
                result.put("backupSizeBytes", bFile.length());
                result.put("timestamp", timestamp);

                System.out.println("[BQP Backup] Đã tạo bản sao lưu an toàn: " + backupFilePath);
            } catch (Exception e) {
                result.put("status", "FAILED");
                result.put("error", e.getMessage());
                System.err.println("[BQP Backup LỖI] " + e.getMessage());
            }
            return result;
        });
    }

    public Map<String, Object> restore(String backupFileOrName) {
        return maintenanceLock.callWithExclusiveMaintenance(() -> doRestore(backupFileOrName));
    }

    private Map<String, Object> doRestore(String backupFileOrName) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (backupFileOrName == null || backupFileOrName.trim().isEmpty()) {
            result.put("status", "FAILED");
            result.put("error", "Tên file hoặc đường dẫn backup không được để trống");
            return result;
        }

        try {
            Path backupsDir = directoryResolver.getBackupsDirectory().toAbsolutePath().normalize();
            Path backupPath = backupsDir.resolve(Path.of(backupFileOrName.trim()).getFileName()).normalize();

            // Chống path traversal: file bắt buộc phải nằm trong thư mục backups
            if (!backupPath.startsWith(backupsDir) || !Files.exists(backupPath) || !Files.isRegularFile(backupPath)) {
                result.put("status", "FAILED");
                result.put("error", "File sao lưu không hợp lệ hoặc không nằm trong thư mục backups cho phép: " + backupFileOrName);
                return result;
            }

            // 1. Kiểm tra tính toàn vẹn của file backup nguồn trước khi thực hiện
            String backupJdbcUrl = com.appvalidateexcel.storage.crypto.DbCipher.jdbcUrl(backupPath, com.appvalidateexcel.storage.crypto.DbKey.get());
            try (java.sql.Connection testConn = java.sql.DriverManager.getConnection(backupJdbcUrl);
                 java.sql.Statement testStmt = testConn.createStatement();
                 java.sql.ResultSet rs = testStmt.executeQuery("PRAGMA integrity_check;")) {
                if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                    result.put("status", "FAILED");
                    result.put("error", "File sao lưu bị lỗi cấu trúc hoặc hư hại toàn vẹn (integrity_check FAILED). Hủy khôi phục!");
                    return result;
                }
            }

            // 2. Tạo bản sao lưu dự phòng (checkpoint) của DB hiện tại trước khi ghi đè (BẮT BUỘC)
            String checkpointTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + "_" + UUID.randomUUID().toString().substring(0, 6);
            Path checkpointPath = backupsDir.resolve("app-checkpoint-pre-restore-" + checkpointTimestamp + ".db");
            String checkpointEscaped = checkpointPath.toAbsolutePath().toString().replace('\\', '/').replace("'", "''");
            try {
                jdbcTemplate.execute("VACUUM INTO '" + checkpointEscaped + "'");
            } catch (Exception e) {
                result.put("status", "FAILED");
                result.put("error", "Không thể tạo checkpoint dự phòng trước khi khôi phục. Hủy bỏ để bảo vệ an toàn dữ liệu: " + e.getMessage());
                return result;
            }

            // 3. Thực hiện khôi phục qua SQLite Online Backup / Restore API
            try (java.sql.Connection conn = java.util.Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
                org.sqlite.SQLiteConnection sqliteConn = conn.unwrap(org.sqlite.SQLiteConnection.class);
                sqliteConn.getDatabase().restore("main", backupPath.toAbsolutePath().toString(), null);
            }

            // 4. Kiểm tra tính toàn vẹn sau khi khôi phục
            String postCheck = jdbcTemplate.queryForObject("PRAGMA integrity_check;", String.class);
            if (postCheck == null || !"ok".equalsIgnoreCase(postCheck)) {
                // Tự động khôi phục lại từ checkpoint
                try (java.sql.Connection conn = java.util.Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
                    org.sqlite.SQLiteConnection sqliteConn = conn.unwrap(org.sqlite.SQLiteConnection.class);
                    sqliteConn.getDatabase().restore("main", checkpointPath.toAbsolutePath().toString(), null);
                }
                result.put("status", "FAILED");
                result.put("error", "Kiểm tra toàn vẹn sau khôi phục thất bại (integrity_check: " + postCheck + "). Đã tự động phục hồi về checkpoint.");
                return result;
            }

            // 5. Kiểm tra khóa ngoại sau khôi phục
            List<Map<String, Object>> fkViolations = jdbcTemplate.queryForList("PRAGMA foreign_key_check;");
            if (!fkViolations.isEmpty()) {
                // Tự động khôi phục lại từ checkpoint
                try (java.sql.Connection conn = java.util.Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
                    org.sqlite.SQLiteConnection sqliteConn = conn.unwrap(org.sqlite.SQLiteConnection.class);
                    sqliteConn.getDatabase().restore("main", checkpointPath.toAbsolutePath().toString(), null);
                }
                result.put("status", "FAILED");
                result.put("error", "Kiểm tra khóa ngoại sau khôi phục thất bại (foreign_key_check phát hiện " + fkViolations.size() + " vi phạm). Đã tự động phục hồi về checkpoint.");
                result.put("foreignKeyViolationsCount", fkViolations.size());
                return result;
            }

            result.put("status", "SUCCESS");
            result.put("message", "Đã khôi phục cơ sở dữ liệu thành công từ: " + backupPath.getFileName());
            result.put("restoredFrom", backupPath.toAbsolutePath().toString());
            result.put("postIntegrityCheck", postCheck);
            result.put("foreignKeyViolationsCount", fkViolations.size());
            result.put("checkpointCreated", checkpointPath.toAbsolutePath().toString());

            System.out.println("[BQP Restore] Khôi phục thành công từ: " + backupPath);
        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("error", "Lỗi khôi phục cơ sở dữ liệu: " + e.getMessage());
            System.err.println("[BQP Restore LỖI] " + e.getMessage());
        }
        return result;
    }

    public java.util.List<Map<String, Object>> listBackups() {
        java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
        try {
            Path backupDir = directoryResolver.getBackupsDirectory();
            if (Files.exists(backupDir)) {
                try (java.util.stream.Stream<Path> stream = Files.list(backupDir)) {
                    stream.filter(p -> p.toString().endsWith(".db") || p.toString().endsWith(".json"))
                          .sorted(java.util.Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                          .forEach(p -> {
                              File f = p.toFile();
                              Map<String, Object> item = new LinkedHashMap<>();
                              item.put("fileName", f.getName());
                              item.put("sizeBytes", f.length());
                              item.put("lastModified", Instant.ofEpochMilli(f.lastModified()).toString());
                              item.put("path", f.getAbsolutePath());
                              list.add(item);
                          });
                }
            }
        } catch (Exception ignored) {}
        return list;
    }
}
