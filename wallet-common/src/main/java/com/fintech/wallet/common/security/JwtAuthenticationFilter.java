package com.fintech.wallet.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // Khóa bí mật nội bộ chỉ có API Gateway nắm giữ
    private static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";
    private static final String GATEWAY_SECRET_VALUE = "fintech-secret-key-2026";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // =========================================================================
        // 1. CHẶN ĐỨNG TRUY CẬP TRỰC TIẾP VÀO CỔNG DỊCH VỤ (BẮT BUỘC QUA GATEWAY)
        // =========================================================================
        String gatewaySecret = request.getHeader(GATEWAY_SECRET_HEADER);
        if (gatewaySecret == null || !GATEWAY_SECRET_VALUE.equals(gatewaySecret)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            String jsonResponse = "{\"success\":false,\"code\":5003,\"message\":\"Truy cập trực tiếp bị từ chối! Mọi yêu cầu bắt buộc phải đi qua API Gateway (cổng 80).\"}";
            response.getWriter().write(jsonResponse);
            return; // Dừng lại ngay, không cho request đi tiếp vào Controller
        }

        // =========================================================================
        // 2. BÓC TÁCH DANH TÍNH TỪ GATEWAY NẠP VÀO SPRING SECURITY
        // =========================================================================
        String userId = request.getHeader("X-User-Id");
        String role = request.getHeader("X-User-Roles");

        if (userId != null && role != null) {
            SimpleGrantedAuthority authority = new SimpleGrantedAuthority(role);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userId, // Principal
                    null,   // Credentials
                    Collections.singletonList(authority) // Roles
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}