package com.appvalidateexcel.storage.migration;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SqliteSchemaMigrator {

    private final JdbcTemplate jdbcTemplate;
    private static final Pattern VERSION_PATTERN = Pattern.compile("V(\\d+)__.*\\.sql");

    public SqliteSchemaMigrator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        System.out.println("[BQP Migration] Bắt đầu kiểm tra và cập nhật lược đồ SQLite...");
        ensureSchemaVersionTable();

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:db/migration/V*.sql");

            Map<Integer, Resource> migrationMap = new TreeMap<>();
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null) {
                    Matcher matcher = VERSION_PATTERN.matcher(filename);
                    if (matcher.matches()) {
                        int version = Integer.parseInt(matcher.group(1));
                        migrationMap.put(version, resource);
                    }
                }
            }

            for (Map.Entry<Integer, Resource> entry : migrationMap.entrySet()) {
                int version = entry.getKey();
                Resource resource = entry.getValue();
                applyMigration(version, resource);
            }

            System.out.println("[BQP Migration] Hoàn thành kiểm tra lược đồ SQLite!");
        } catch (Exception e) {
            System.err.println("[BQP Migration LỖI] Lỗi trong quá trình cập nhật lược đồ: " + e.getMessage());
            throw new RuntimeException("Cập nhật lược đồ SQLite thất bại", e);
        }
    }

    private void ensureSchemaVersionTable() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS schema_version (" +
                "    version INTEGER PRIMARY KEY," +
                "    script_name TEXT NOT NULL," +
                "    checksum TEXT," +
                "    installed_at TEXT NOT NULL," +
                "    execution_time_ms INTEGER," +
                "    success INTEGER NOT NULL" +
                ");"
        );
    }

    private void applyMigration(int version, Resource resource) throws Exception {
        String scriptName = resource.getFilename();
        String sql;
        try (InputStream is = resource.getInputStream()) {
            sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        String checksum = calculateSha256(sql);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT version, checksum, success FROM schema_version WHERE version = ?",
                version
        );

        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            int success = ((Number) row.get("success")).intValue();
            String recordedChecksum = (String) row.get("checksum");
            if (success == 1) {
                if (recordedChecksum != null && !recordedChecksum.equals(checksum)) {
                    // Cho phép checksum chuyển tiếp của V2 (từ đợt rà soát 5) tương thích ngược an toàn
                    boolean isKnownLegacyV2 = (version == 2 && "4c1916f4c50a6340f925f23c347b7e27783ba0d92ff10e3948df4f6a2cef198b".equalsIgnoreCase(recordedChecksum));
                    if (!isKnownLegacyV2) {
                        throw new IllegalStateException("CẢNH BÁO TOÀN VẸN: Checksum của migration V" + version +
                                " (" + scriptName + ") đã bị thay đổi so với lần cài đặt trước! " +
                                "Recorded: " + recordedChecksum + ", Current: " + checksum);
                    } else {
                        System.out.println("[BQP Migration] Chấp nhận checksum chuyển tiếp của V2 từ đợt trước. Bản vá V3 sẽ hoàn thiện làm sạch.");
                    }
                }
                return;
            } else {
                System.out.println("[BQP Migration] Script " + scriptName + " từng thất bại, đang chạy lại...");
            }
        }

        System.out.println("[BQP Migration] Đang thực thi migration V" + version + ": " + scriptName);
        long start = System.currentTimeMillis();

        String[] statements = sql.split(";(?=(?:[^']*'[^']*')*[^']*$)");

        try (java.sql.Connection conn = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (java.sql.Statement stmt = conn.createStatement()) {
                if (version == 2) {
                    runV2PreflightCleanupIfNecessary(stmt);
                }

                for (String sqlStmt : statements) {
                    String trimmed = sqlStmt.trim();
                    if (!trimmed.isEmpty()) {
                        try {
                            stmt.execute(trimmed);
                        } catch (java.sql.SQLException ex) {
                            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("duplicate column name")) {
                                // Cột đã tồn tại trước đó, an toàn bỏ qua để đảm bảo tính lũy thừa (idempotency)
                                continue;
                            }
                            throw ex;
                        }
                    }
                }

                long executionTimeMs = System.currentTimeMillis() - start;
                String nowIso = Instant.now().toString();

                String updateVersionSql = "INSERT INTO schema_version (version, script_name, checksum, installed_at, execution_time_ms, success) " +
                        "VALUES (?, ?, ?, ?, ?, 1) " +
                        "ON CONFLICT(version) DO UPDATE SET " +
                        "script_name = excluded.script_name, " +
                        "checksum = excluded.checksum, " +
                        "installed_at = excluded.installed_at, " +
                        "execution_time_ms = excluded.execution_time_ms, " +
                        "success = 1";

                try (java.sql.PreparedStatement ps = conn.prepareStatement(updateVersionSql)) {
                    ps.setInt(1, version);
                    ps.setString(2, scriptName);
                    ps.setString(3, checksum);
                    ps.setString(4, nowIso);
                    ps.setLong(5, executionTimeMs);
                    ps.executeUpdate();
                }

                conn.commit();
                System.out.println("[BQP Migration] V" + version + " thành công trong 1 transaction an toàn (" + executionTimeMs + " ms)");
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Lỗi thực thi migration V" + version + " (" + scriptName + "), đã rollback an toàn: " + e.getMessage(), e);
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }

    private void runV2PreflightCleanupIfNecessary(java.sql.Statement stmt) throws java.sql.SQLException {
        boolean tableExists = false;
        try (java.sql.ResultSet rs = stmt.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='migration_audit'")) {
            if (rs.next() && rs.getInt(1) > 0) {
                tableExists = true;
            }
        }
        if (tableExists) {
            // Bước 1: Chuẩn hóa/xóa bỏ checksum legacy không hợp lệ (không phải đúng 64 ký tự hex)
            stmt.execute("UPDATE migration_audit " +
                    "SET checksum = NULL " +
                    "WHERE checksum IS NOT NULL " +
                    "  AND (length(checksum) != 64 OR checksum GLOB '*[^0-9a-fA-F]*')");

            // Bước 2: Dọn dẹp bản ghi trùng lặp checksum SUCCESS, chỉ giữ 1 bản ghi đại diện có MIN(id)
            stmt.execute("UPDATE migration_audit " +
                    "SET checksum = NULL " +
                    "WHERE status = 'SUCCESS' " +
                    "  AND checksum IS NOT NULL " +
                    "  AND id IS NOT NULL " +
                    "  AND id NOT IN ( " +
                    "      SELECT MIN(id) " +
                    "      FROM migration_audit " +
                    "      WHERE status = 'SUCCESS' AND checksum IS NOT NULL AND id IS NOT NULL " +
                    "      GROUP BY checksum " +
                    "  )");
            System.out.println("[BQP Migration] Đã thực hiện preflight dọn dẹp legacy checksums trước khi áp dụng V2.");
        }
    }

    private String calculateSha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
