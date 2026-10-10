package com.appvalidateexcel.auth;

import com.appvalidateexcel.storage.migration.SqliteSchemaMigrator;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Quản lý bảng users: tạo, đăng nhập, đổi mật khẩu, khóa tài khoản khi nhập sai nhiều lần. */
@Service
public class UserService {

    public static final String DEFAULT_ADMIN = "admin";
    public static final String DEFAULT_ADMIN_PASSWORD = "123456";

    private static final int ITERATIONS = 200_000;
    private static final int MAX_FAILED = 5;
    private static final long LOCK_SECONDS = 5 * 60;
    private static final int MIN_PASSWORD = 6;

    private final JdbcTemplate jdbc;

    // tham số migrator chỉ để đảm bảo bảng users đã được tạo trước khi dùng
    public UserService(JdbcTemplate jdbc, SqliteSchemaMigrator migrator) {
        this.jdbc = jdbc;
    }

    /** Lần đầu chạy (bảng rỗng) tạo tài khoản admin mặc định, không bắt buộc đổi mật khẩu. */
    @PostConstruct
    void seedAdmin() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        if (count != null && count == 0) {
            insertUser(DEFAULT_ADMIN, "Quản trị viên", DEFAULT_ADMIN_PASSWORD, false);
            System.out.println("[BQP Auth] Đã tạo tài khoản mặc định '" + DEFAULT_ADMIN + "'. Hãy đổi mật khẩu sau khi đăng nhập.");
        }
    }

    public UserAccount createUser(String username, String displayName, String password) {
        validateUsername(username);
        validatePassword(password);
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username.trim());
        if (exists != null && exists > 0) throw new IllegalArgumentException("Tên đăng nhập đã tồn tại.");
        String id = insertUser(username, displayName, password, true);
        return findById(id);
    }

    private String insertUser(String username, String displayName, String password, boolean mustChange) {
        String id = UUID.randomUUID().toString();
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        jdbc.update("INSERT INTO users(id, username, display_name, password_salt, password_hash, must_change_password, created_at) "
                        + "VALUES (?,?,?,?,?,?,?)",
                id, username.trim(), displayName, b64(salt), b64(hash(password, salt)), mustChange ? 1 : 0, Instant.now().toString());
        return id;
    }

    /** Thông báo chung cho mọi trường hợp thất bại để không lộ tài khoản có tồn tại, đang khóa hay bị vô hiệu hóa. */
    private static final String LOGIN_FAILED = "Đăng nhập thất bại: sai thông tin đăng nhập, hoặc tài khoản đang tạm khóa/vô hiệu hóa.";

    /** Đăng nhập; ném SecurityException với thông báo chung nếu thất bại. */
    public UserAccount authenticate(String username, String password) {
        if (username == null || password == null) throw new SecurityException(LOGIN_FAILED);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM users WHERE username = ?", username.trim());
        if (rows.isEmpty()) {
            hash(password, new byte[16]); // làm chậm tương đương để khó dò tên đăng nhập
            throw new SecurityException(LOGIN_FAILED);
        }
        Map<String, Object> row = rows.get(0);
        String id = (String) row.get("id");

        // Luôn băm mật khẩu trước để thời gian phản hồi không tiết lộ trạng thái khóa
        byte[] salt = Base64.getDecoder().decode((String) row.get("password_salt"));
        byte[] expected = Base64.getDecoder().decode((String) row.get("password_hash"));
        boolean passwordOk = MessageDigest.isEqual(expected, hash(password, salt));

        String lockedUntil = (String) row.get("locked_until");
        boolean locked = lockedUntil != null && Instant.parse(lockedUntil).isAfter(Instant.now());
        boolean active = ((Number) row.get("is_active")).intValue() == 1;
        if (locked || !active) {
            throw new SecurityException(LOGIN_FAILED);
        }

        if (!passwordOk) {
            int failed = ((Number) row.get("failed_attempts")).intValue() + 1;
            if (failed >= MAX_FAILED) {
                jdbc.update("UPDATE users SET failed_attempts = 0, locked_until = ? WHERE id = ?",
                        Instant.now().plusSeconds(LOCK_SECONDS).toString(), id);
            } else {
                jdbc.update("UPDATE users SET failed_attempts = ? WHERE id = ?", failed, id);
            }
            throw new SecurityException(LOGIN_FAILED);
        }
        jdbc.update("UPDATE users SET failed_attempts = 0, locked_until = NULL, last_login_at = ? WHERE id = ?",
                Instant.now().toString(), id);
        return findById(id);
    }

    /** Đăng xuất: tăng token_version để token đã cấp (kể cả bản bị sao chép) mất hiệu lực ngay. */
    public void invalidateTokens(String id) {
        jdbc.update("UPDATE users SET token_version = token_version + 1 WHERE id = ?", id);
    }

    public void changePassword(String id, String currentPassword, String newPassword, String confirmPassword) {
        validatePassword(newPassword);
        if (!newPassword.equals(confirmPassword)) throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
        Map<String, Object> row = jdbc.queryForList("SELECT * FROM users WHERE id = ?", id).stream().findFirst()
                .orElseThrow(() -> new SecurityException("Không tìm thấy tài khoản."));
        byte[] salt = Base64.getDecoder().decode((String) row.get("password_salt"));
        byte[] expected = Base64.getDecoder().decode((String) row.get("password_hash"));
        if (currentPassword == null || !MessageDigest.isEqual(expected, hash(currentPassword, salt))) {
            throw new SecurityException("Mật khẩu hiện tại không đúng.");
        }
        setPassword(id, newPassword, false);
    }

    /** Quên mật khẩu: sau khi đã xác thực bằng khóa khôi phục, đặt mật khẩu mới cho tài khoản (không bắt đổi lại). */
    public void resetPasswordByRecovery(String username, String newPassword) {
        validatePassword(newPassword);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM users WHERE username = ?", username == null ? "" : username.trim());
        if (rows.isEmpty()) throw new IllegalArgumentException("Không tìm thấy tài khoản.");
        setPassword((String) rows.get(0).get("id"), newPassword, false);
    }

    /** Đặt lại mật khẩu: người dùng phải đổi lại ở lần đăng nhập kế tiếp. */
    public void resetPassword(String id, String newPassword) {
        validatePassword(newPassword);
        if (findById(id) == null) throw new IllegalArgumentException("Không tìm thấy tài khoản.");
        setPassword(id, newPassword, true);
    }

    public void setActive(String id, boolean active) {
        jdbc.update("UPDATE users SET is_active = ?, token_version = token_version + 1 WHERE id = ?", active ? 1 : 0, id);
    }

    /** Đổi mật khẩu cũng tăng token_version để mọi token cũ mất hiệu lực. */
    private void setPassword(String id, String password, boolean mustChange) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        jdbc.update("UPDATE users SET password_salt = ?, password_hash = ?, must_change_password = ?, "
                        + "token_version = token_version + 1, failed_attempts = 0, locked_until = NULL WHERE id = ?",
                b64(salt), b64(hash(password, salt)), mustChange ? 1 : 0, id);
    }

    public UserAccount findById(String id) {
        return jdbc.query("SELECT * FROM users WHERE id = ?", (rs, i) -> new UserAccount(
                        rs.getString("id"), rs.getString("username"), rs.getString("display_name"),
                        rs.getInt("is_active") == 1, rs.getInt("must_change_password") == 1, rs.getInt("token_version")), id)
                .stream().findFirst().orElse(null);
    }

    public List<Map<String, Object>> listUsers() {
        return jdbc.queryForList("SELECT id, username, display_name AS displayName, is_active AS active, "
                + "must_change_password AS mustChangePassword, created_at AS createdAt, last_login_at AS lastLoginAt "
                + "FROM users ORDER BY username");
    }

    private void validateUsername(String username) {
        if (username == null || !username.trim().matches("[A-Za-z0-9._-]{3,50}")) {
            throw new IllegalArgumentException("Tên đăng nhập gồm 3-50 ký tự chữ, số, '.', '_' hoặc '-'.");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new IllegalArgumentException("Mật khẩu phải có ít nhất " + MIN_PASSWORD + " ký tự.");
        }
    }

    private byte[] hash(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Không băm được mật khẩu", e);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
