package com.appvalidateexcel.storage.crypto;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Tiện ích mã hóa file SQLite bằng SQLCipher (AES-256).
 * Mật khẩu DB được giữ trong bộ nhớ (DbKey), không ghi ra đĩa.
 */
public final class DbCipher {

    public enum State { ABSENT, PLAIN, ENCRYPTED }

    private static final byte[] PLAIN_HEADER = "SQLite format 3\0".getBytes(StandardCharsets.ISO_8859_1);

    // Chạy từ fat jar (trước khi Spring khởi động) thì DriverManager chưa tự thấy driver: nạp tường minh.
    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private DbCipher() {
    }

    /** JDBC URL; key = null nghĩa là mở không khóa (dev/test hoặc để mã hóa lần đầu). */
    public static String jdbcUrl(Path dbFile, String key) {
        String path = dbFile.toAbsolutePath().toString().replace('\\', '/');
        if (key == null) {
            return "jdbc:sqlite:" + path;
        }
        return "jdbc:sqlite:file:" + path + "?cipher=sqlcipher&legacy=4&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
    }

    /** Xác định file DB: chưa có, chưa mã hóa (SQLite thường) hay đã mã hóa. */
    public static State state(Path dbFile) throws IOException {
        if (!Files.exists(dbFile) || Files.size(dbFile) == 0) return State.ABSENT;
        byte[] head = new byte[16];
        try (var in = Files.newInputStream(dbFile)) {
            int n = in.readNBytes(head, 0, 16);
            if (n == 16 && java.util.Arrays.equals(head, PLAIN_HEADER)) return State.PLAIN;
        }
        return State.ENCRYPTED;
    }

    /** Đúng mật khẩu thì đọc được sqlite_master. */
    public static boolean verify(Path dbFile, String key) {
        try (Connection c = DriverManager.getConnection(jdbcUrl(dbFile, key));
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM sqlite_master")) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    /** Mã hóa tại chỗ một DB SQLite thường (cần journal_mode không phải WAL). */
    public static void encryptInPlace(Path dbFile, String key) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:file:" + dbFile.toAbsolutePath().toString().replace('\\', '/') + "?cipher=sqlcipher&legacy=4");
             Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=DELETE");
            s.execute("PRAGMA rekey='" + key.replace("'", "''") + "'");
        }
    }

    /** Đổi mật khẩu DB (không được có kết nối nào khác đang mở file). */
    public static void rekey(Path dbFile, String oldKey, String newKey) throws Exception {
        try (Connection c = DriverManager.getConnection(jdbcUrl(dbFile, oldKey));
             Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=DELETE");
            s.execute("PRAGMA rekey='" + newKey.replace("'", "''") + "'");
        }
    }
}
