package com.appvalidateexcel.storage.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Kho khóa (app-keys.json cạnh file DB): DB được mã hóa bằng một "khóa chính" ngẫu nhiên.
 * Khóa chính được bọc riêng bằng (1) mật khẩu DB và (2) khóa khôi phục, nên quên mật khẩu
 * vẫn mở lại được bằng khóa khôi phục, và đổi mật khẩu chỉ cần bọc lại khóa chính.
 */
public final class DbKeyStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 200_000;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private DbKeyStore() {
    }

    public record Created(String masterKey, String recoveryKey) {
    }

    public static Path fileFor(Path dbFile) {
        return dbFile.toAbsolutePath().resolveSibling("app-keys.json");
    }

    public static boolean exists(Path dbFile) {
        return Files.exists(fileFor(dbFile));
    }

    public static void deleteIfExists(Path dbFile) throws Exception {
        Files.deleteIfExists(fileFor(dbFile));
    }

    /** Tạo khóa chính mới + khóa khôi phục mới, bọc bằng mật khẩu và khóa khôi phục, ghi file. */
    public static Created create(Path dbFile, String password) throws Exception {
        String master = HexFormat.of().formatHex(randomBytes(32));
        String recovery = newRecoveryKey();
        ObjectNode root = MAPPER.createObjectNode();
        root.set("password", wrap(master, password));
        root.set("recovery", wrap(master, normalize(recovery)));
        write(dbFile, root);
        return new Created(master, recovery);
    }

    /** Trả về khóa chính nếu mật khẩu đúng, ngược lại null. */
    public static String unlockWithPassword(Path dbFile, String password) {
        return unwrapField(dbFile, "password", password);
    }

    /** Trả về khóa chính nếu khóa khôi phục đúng, ngược lại null. */
    public static String unlockWithRecovery(Path dbFile, String recoveryKey) {
        return unwrapField(dbFile, "recovery", normalize(recoveryKey));
    }

    /** Đổi mật khẩu: bọc lại khóa chính, không cần mã hóa lại DB. */
    public static void changePassword(Path dbFile, String masterKey, String newPassword) throws Exception {
        ObjectNode root = read(dbFile);
        root.set("password", wrap(masterKey, newPassword));
        write(dbFile, root);
    }

    /** Sinh khóa khôi phục mới, thay thế khóa cũ. */
    public static String regenerateRecovery(Path dbFile, String masterKey) throws Exception {
        String recovery = newRecoveryKey();
        ObjectNode root = read(dbFile);
        root.set("recovery", wrap(masterKey, normalize(recovery)));
        write(dbFile, root);
        return recovery;
    }

    // ---------------------------------------------------------------- nội bộ

    private static String unwrapField(Path dbFile, String field, String secret) {
        try {
            if (secret == null || secret.isEmpty()) return null;
            JsonNode node = read(dbFile).get(field);
            byte[] salt = Base64.getDecoder().decode(node.get("salt").asText());
            byte[] iv = Base64.getDecoder().decode(node.get("iv").asText());
            byte[] data = Base64.getDecoder().decode(node.get("data").asText());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, derive(secret, salt), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null; // sai mật khẩu/khóa thì giải mã GCM thất bại
        }
    }

    private static ObjectNode wrap(String master, String secret) throws Exception {
        byte[] salt = randomBytes(16);
        byte[] iv = randomBytes(12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, derive(secret, salt), new GCMParameterSpec(128, iv));
        byte[] data = cipher.doFinal(master.getBytes(StandardCharsets.UTF_8));
        ObjectNode n = MAPPER.createObjectNode();
        n.put("salt", Base64.getEncoder().encodeToString(salt));
        n.put("iv", Base64.getEncoder().encodeToString(iv));
        n.put("data", Base64.getEncoder().encodeToString(data));
        return n;
    }

    private static SecretKeySpec derive(String secret, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, 256);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        return new SecretKeySpec(key, "AES");
    }

    private static ObjectNode read(Path dbFile) throws Exception {
        return (ObjectNode) MAPPER.readTree(Files.readAllBytes(fileFor(dbFile)));
    }

    private static void write(Path dbFile, ObjectNode root) throws Exception {
        Path target = fileFor(dbFile);
        Path tmp = target.resolveSibling("app-keys.json.tmp");
        Files.write(tmp, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    /** Khóa khôi phục dạng XXXX-XXXX-... gồm 32 ký tự base32 (160 bit). */
    private static String newRecoveryKey() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(BASE32.charAt(RANDOM.nextInt(32)));
        }
        return sb.toString();
    }

    /** Bỏ dấu gạch, khoảng trắng và chuẩn hóa chữ hoa để nhập khóa khôi phục dễ hơn. */
    private static String normalize(String recoveryKey) {
        return recoveryKey == null ? "" : recoveryKey.replaceAll("[\\s-]", "").toUpperCase();
    }
}
