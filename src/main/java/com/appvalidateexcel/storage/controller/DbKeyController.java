package com.appvalidateexcel.storage.controller;

import com.appvalidateexcel.storage.config.DataDirectoryResolver;
import com.appvalidateexcel.storage.crypto.DbKey;
import com.appvalidateexcel.storage.crypto.DbKeyStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;

/**
 * Đổi mật khẩu DB và tạo lại khóa khôi phục. DB được mã hóa bằng khóa chính nằm trong DbKey;
 * mật khẩu chỉ là lớp bọc khóa chính trong app-keys.json nên đổi mật khẩu không cần mã hóa lại DB.
 */
@RestController
@RequestMapping("/api/db")
public class DbKeyController {

    private static final int MIN_LENGTH = 6;

    private final Path dbFile;

    public DbKeyController(DataDirectoryResolver directoryResolver) {
        this.dbFile = directoryResolver.getDatabaseFile();
    }

    @PostMapping("/change-key")
    public ResponseEntity<Map<String, String>> changeKey(@RequestBody Map<String, String> body) {
        String current = body.get("currentPassword");
        String next = body.get("newPassword");
        String confirm = body.get("confirmPassword");

        if (DbKey.get() == null || !DbKeyStore.exists(dbFile)) {
            return error(400, "Cơ sở dữ liệu đang chạy ở chế độ không mã hóa.");
        }
        if (DbKeyStore.unlockWithPassword(dbFile, current) == null) {
            return error(400, "Mật khẩu cơ sở dữ liệu hiện tại không đúng.");
        }
        if (next == null || next.length() < MIN_LENGTH) {
            return error(400, "Mật khẩu mới phải có ít nhất " + MIN_LENGTH + " ký tự.");
        }
        if (!next.equals(confirm)) {
            return error(400, "Mật khẩu xác nhận không khớp.");
        }
        try {
            DbKeyStore.changePassword(dbFile, DbKey.get(), next);
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Đã đổi mật khẩu cơ sở dữ liệu."));
        } catch (Exception e) {
            return error(500, "Không đổi được mật khẩu cơ sở dữ liệu: " + e.getMessage());
        }
    }

    /** Tạo khóa khôi phục mới (khóa cũ mất hiệu lực). Cần nhập đúng mật khẩu DB hiện tại. */
    @PostMapping("/recovery-key")
    public ResponseEntity<Map<String, String>> regenerateRecoveryKey(@RequestBody Map<String, String> body) {
        if (DbKey.get() == null || !DbKeyStore.exists(dbFile)) {
            return error(400, "Cơ sở dữ liệu đang chạy ở chế độ không mã hóa.");
        }
        if (DbKeyStore.unlockWithPassword(dbFile, body.get("currentPassword")) == null) {
            return error(400, "Mật khẩu cơ sở dữ liệu hiện tại không đúng.");
        }
        try {
            String recovery = DbKeyStore.regenerateRecovery(dbFile, DbKey.get());
            return ResponseEntity.ok(Map.of("status", "ok", "recoveryKey", recovery));
        } catch (Exception e) {
            return error(500, "Không tạo được khóa khôi phục: " + e.getMessage());
        }
    }

    private ResponseEntity<Map<String, String>> error(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", "error", "message", message));
    }
}
