package com.appvalidateexcel.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/**
 * Lớp bảo vệ đầu tiên cho mọi request:
 * - Chỉ chấp nhận Host là localhost / 127.0.0.1 / [::1] (chống DNS rebinding).
 * - Nếu có header Origin thì cũng phải là localhost (chống trang web lạ gọi API từ trình duyệt).
 * - Gắn các header bảo mật cơ bản.
 */
@Component
@Order(1)
public class RequestGuardFilter extends OncePerRequestFilter {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        applySecurityHeaders(response);

        if (!isLocalHostHeader(request.getHeader("Host")) || !isLocalOrigin(request.getHeader("Origin"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Dùng chung với cổng mở khóa chạy trước Spring. */
    public static void applySecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Content-Security-Policy", "frame-ancestors 'none'");
    }

    public static boolean isLocalHostHeader(String hostHeader) {
        if (hostHeader == null) return false;
        String host = hostHeader.trim().toLowerCase();
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            host = end > 0 ? host.substring(0, end + 1) : host;
        } else {
            int colon = host.indexOf(':');
            if (colon >= 0) host = host.substring(0, colon);
        }
        return LOCAL_HOSTS.contains(host);
    }

    /** Không có Origin (điều hướng thường, công cụ dòng lệnh) thì cho qua; có thì phải là localhost. */
    public static boolean isLocalOrigin(String origin) {
        if (origin == null || origin.isEmpty()) return true;
        try {
            String host = URI.create(origin).getHost();
            return host != null && LOCAL_HOSTS.contains(host.toLowerCase().replace("[", "").replace("]", ""));
        } catch (Exception e) {
            return false;
        }
    }
}
