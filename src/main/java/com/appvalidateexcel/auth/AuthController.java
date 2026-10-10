package com.appvalidateexcel.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final java.nio.file.Path dbFile;
    private volatile int failedRecoveryAttempts = 0;

    public AuthController(UserService userService, JwtService jwtService,
                          com.appvalidateexcel.storage.config.DataDirectoryResolver directoryResolver) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.dbFile = directoryResolver.getDatabaseFile();
    }

    /**
     * Quên mật khẩu đăng nhập: dùng chính khóa khôi phục của cơ sở dữ liệu để đặt mật khẩu mới cho một tài khoản.
     * Khóa khôi phục có 160 bit nên không thể dò; vẫn làm chậm sau mỗi lần sai.
     */
    @PostMapping("/auth/forgot-password")
    public synchronized ResponseEntity<Map<String, String>> forgotPassword(@RequestBody Map<String, String> body) throws InterruptedException {
        if (!com.appvalidateexcel.storage.crypto.DbKeyStore.exists(dbFile)) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Hệ thống chưa có khóa khôi phục (cơ sở dữ liệu chưa mã hóa)."));
        }
        String master = com.appvalidateexcel.storage.crypto.DbKeyStore.unlockWithRecovery(dbFile, body.get("recoveryKey"));
        if (master == null) {
            failedRecoveryAttempts++;
            Thread.sleep(Math.min(failedRecoveryAttempts, 5) * 1000L);
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", "Khóa khôi phục không đúng."));
        }
        failedRecoveryAttempts = 0;
        try {
            userService.resetPasswordByRecovery(body.get("username"), body.get("newPassword"));
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Đã đặt mật khẩu mới. Vui lòng đăng nhập."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body, HttpServletResponse response) {
        try {
            UserAccount user = userService.authenticate(body.get("username"), body.get("password"));
            setCookie(response, jwtService.createToken(user), JwtService.TOKEN_TTL_SECONDS);
            return ResponseEntity.ok(userInfo(user));
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/auth/logout")
    public Map<String, String> logout(HttpServletRequest request, HttpServletResponse response) {
        // Thu hồi token thật sự ở phía máy chủ nếu request mang token hợp lệ
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie c : request.getCookies()) {
                if (JwtAuthFilter.COOKIE_NAME.equals(c.getName())) {
                    Map<String, Object> claims = jwtService.verify(c.getValue());
                    if (claims != null) userService.invalidateTokens((String) claims.get("uid"));
                }
            }
        }
        setCookie(response, "", 0);
        return Map.of("status", "ok");
    }

    @GetMapping("/auth/me")
    public Map<String, Object> me(HttpServletRequest request) {
        return userInfo(current(request));
    }

    /** Đổi mật khẩu; thành công thì cấp token mới (token cũ bị vô hiệu do token_version tăng). */
    @PostMapping("/auth/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@RequestBody Map<String, String> body,
                                                              HttpServletRequest request, HttpServletResponse response) {
        UserAccount user = current(request);
        try {
            userService.changePassword(user.id(), body.get("currentPassword"), body.get("newPassword"), body.get("confirmPassword"));
            setCookie(response, jwtService.createToken(userService.findById(user.id())), JwtService.TOKEN_TTL_SECONDS);
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Đổi mật khẩu thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // ---- Quản lý người dùng (mọi người dùng đã đăng nhập đều dùng được, không phân quyền) ----

    @GetMapping("/users")
    public List<Map<String, Object>> listUsers() {
        return userService.listUsers();
    }

    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, String> body) {
        try {
            UserAccount u = userService.createUser(body.get("username"), body.get("displayName"), body.get("password"));
            return ResponseEntity.ok(userInfo(u));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            userService.resetPassword(id, body.get("newPassword"));
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/users/{id}/active")
    public ResponseEntity<Map<String, String>> setActive(@PathVariable String id, @RequestBody Map<String, Boolean> body,
                                                         HttpServletRequest request) {
        boolean active = Boolean.TRUE.equals(body.get("active"));
        if (!active && current(request).id().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Không thể tự vô hiệu hóa chính mình."));
        }
        userService.setActive(id, active);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private UserAccount current(HttpServletRequest request) {
        UserAccount user = (UserAccount) request.getAttribute(JwtAuthFilter.ATTR_USER);
        if (user == null) throw new SecurityException("Chưa đăng nhập.");
        return user;
    }

    private Map<String, Object> userInfo(UserAccount u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id());
        m.put("username", u.username());
        m.put("displayName", u.displayName());
        m.put("mustChangePassword", u.mustChangePassword());
        return m;
    }

    /** Cookie HttpOnly + SameSite=Strict: JS trong trang không đọc được token, chống CSRF cơ bản. */
    private void setCookie(HttpServletResponse response, String value, long maxAge) {
        response.addHeader("Set-Cookie", JwtAuthFilter.COOKIE_NAME + "=" + value
                + "; Path=/; Max-Age=" + maxAge + "; HttpOnly; SameSite=Strict");
    }
}
