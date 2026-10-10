package com.appvalidateexcel.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Bắt buộc JWT cho mọi request (trừ trang đăng nhập và API đăng nhập/đăng xuất).
 * Token lấy từ cookie HttpOnly "BQP_TOKEN" hoặc header "Authorization: Bearer ...".
 * Tắt được bằng bqp.auth.enabled=false (dùng cho test).
 */
@Component
@Order(2)
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "BQP_TOKEN";
    public static final String ATTR_USER = "bqp.user";

    private final JwtService jwtService;
    private final UserService userService;

    @Value("${bqp.auth.enabled:true}")
    private boolean enabled;

    public JwtAuthFilter(JwtService jwtService, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!enabled || isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        UserAccount user = authenticate(request);
        boolean api = path.startsWith("/api/");
        if (user == null) {
            reject(response, api, 401, "Chưa đăng nhập hoặc phiên đã hết hạn.");
            return;
        }
        // Chưa đổi mật khẩu bắt buộc: chỉ cho dùng API xác thực
        if (user.mustChangePassword() && !path.startsWith("/api/auth/")) {
            reject(response, api, 403, "Bạn phải đổi mật khẩu trước khi sử dụng.");
            return;
        }
        request.setAttribute(ATTR_USER, user);
        chain.doFilter(request, response);
    }

    private boolean isPublic(String path) {
        return path.equals("/login.html") || path.equals("/api/auth/login") || path.equals("/api/auth/logout")
                || path.equals("/api/auth/forgot-password")
                || path.equals("/favicon.ico");
    }

    private UserAccount authenticate(HttpServletRequest request) {
        String token = null;
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7).trim();
        } else if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (COOKIE_NAME.equals(c.getName())) token = c.getValue();
            }
        }
        Map<String, Object> claims = jwtService.verify(token);
        if (claims == null) return null;
        UserAccount user = userService.findById((String) claims.get("uid"));
        if (user == null || !user.active()) return null;
        // token cũ (trước khi đổi mật khẩu/khóa tài khoản) không còn hợp lệ
        if (((Number) claims.get("ver")).intValue() != user.tokenVersion()) return null;
        return user;
    }

    private void reject(HttpServletResponse response, boolean api, int status, String message) throws IOException {
        if (api) {
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":" + status + ",\"error\":\"" + message + "\"}");
        } else {
            response.sendRedirect("/login.html");
        }
    }
}
