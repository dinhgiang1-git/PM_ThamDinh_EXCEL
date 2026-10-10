package com.appvalidateexcel.auth;

import com.appvalidateexcel.storage.migration.SqliteSchemaMigrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tạo và kiểm tra JWT (HS256). Khóa bí mật được sinh ngẫu nhiên lần đầu và lưu trong bảng app_settings.
 */
@Service
public class JwtService {

    public static final long TOKEN_TTL_SECONDS = 8 * 60 * 60; // 8 giờ

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();
    private byte[] secret;

    // tham số migrator chỉ để đảm bảo bảng đã được tạo trước khi dùng
    public JwtService(JdbcTemplate jdbc, SqliteSchemaMigrator migrator) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void loadOrCreateSecret() {
        String stored;
        try {
            stored = jdbc.queryForObject("SELECT setting_value FROM app_settings WHERE setting_key = 'jwt_secret'", String.class);
        } catch (EmptyResultDataAccessException e) {
            byte[] raw = new byte[48];
            new SecureRandom().nextBytes(raw);
            stored = Base64.getEncoder().encodeToString(raw);
            jdbc.update("INSERT INTO app_settings(setting_key, setting_value) VALUES ('jwt_secret', ?)", stored);
        }
        secret = Base64.getDecoder().decode(stored);
    }

    public String createToken(UserAccount user) {
        try {
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            long now = Instant.now().getEpochSecond();
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", user.username());
            claims.put("uid", user.id());
            claims.put("ver", user.tokenVersion());
            claims.put("iat", now);
            claims.put("exp", now + TOKEN_TTL_SECONDS);
            String unsigned = b64(mapper.writeValueAsBytes(header)) + "." + b64(mapper.writeValueAsBytes(claims));
            return unsigned + "." + b64(hmac(unsigned));
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được token", e);
        }
    }

    /** Trả về claims nếu token đúng chữ ký và chưa hết hạn, ngược lại trả null. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> verify(String token) {
        try {
            if (token == null) return null;
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;
            byte[] expected = hmac(parts[0] + "." + parts[1]);
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) return null;
            Map<String, Object> claims = mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            long exp = ((Number) claims.get("exp")).longValue();
            if (Instant.now().getEpochSecond() >= exp) return null;
            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] hmac(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
