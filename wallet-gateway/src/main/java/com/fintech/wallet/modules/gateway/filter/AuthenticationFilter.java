package com.fintech.wallet.modules.gateway.filter;

import com.fintech.wallet.modules.gateway.util.JwtUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate redisTemplate;
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    public static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";
    public static final String GATEWAY_SECRET_VALUE = "fintech-secret-key-2026";

    public AuthenticationFilter(JwtUtil jwtUtil, ReactiveStringRedisTemplate redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. Cho phép các API Public đi qua, nhưng BẮT BUỘC gắn X-Gateway-Secret
        if (path.contains("/api/v1/auth/login")
                || path.contains("/api/v1/auth/register")
                || path.contains("/api/v1/auth/refresh")
                || path.contains("/payments/VNPAY/")
                || path.contains("/v3/api-docs")) {

            ServerHttpRequest publicMutatedRequest = exchange.getRequest().mutate()
                    .header(GATEWAY_SECRET_HEADER, GATEWAY_SECRET_VALUE)
                    .build();

            return chain.filter(exchange.mutate().request(publicMutatedRequest).build());
        }

        // 2. Kiểm tra Header Authorization
        if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            return onError(
                    exchange,
                    5001,
                    "Yêu cầu chưa được xác thực! Vui lòng đính kèm Token trong Header Authorization.",
                    HttpStatus.UNAUTHORIZED
            );
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return onError(
                    exchange,
                    5001,
                    "Định dạng Token không hợp lệ! Header Authorization bắt buộc phải bắt đầu bằng 'Bearer '.",
                    HttpStatus.UNAUTHORIZED
            );
        }

        String token = authHeader.substring(7);

        try {
            // 3. Kiểm tra tính hợp lệ của Token
            jwtUtil.validateToken(token);
            String userId = jwtUtil.getUserIdFromToken(token);

            // 4. Kiểm tra Redis Blacklist (Token đã đăng xuất)
            String redisKey = BLACKLIST_PREFIX + token;
            return redisTemplate.hasKey(redisKey)
                    .flatMap(isBlacklisted -> {
                        if (Boolean.TRUE.equals(isBlacklisted)) {
                            return onError(
                                    exchange,
                                    5002,
                                    "Phiên đăng nhập đã bị thu hồi do bạn đã đăng xuất khỏi hệ thống!",
                                    HttpStatus.UNAUTHORIZED
                            );
                        }

                        // 5. Token hợp lệ -> Bơm X-User-Id, Role và X-Gateway-Secret
                        String role = jwtUtil.getRoleFromToken(token);
                        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                                .header("X-User-Id", userId)
                                .header("X-User-Roles", role)
                                .header(GATEWAY_SECRET_HEADER, GATEWAY_SECRET_VALUE)
                                .build();

                        return chain.filter(exchange.mutate().request(modifiedRequest).build());
                    });

        } catch (Exception e) {
            return onError(
                    exchange,
                    5001,
                    "Token không hợp lệ hoặc đã hết hạn! Vui lòng đăng nhập lại.",
                    HttpStatus.UNAUTHORIZED
            );
        }
    }

    private Mono<Void> onError(ServerWebExchange exchange, int code, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String json = String.format("{\"code\":%d,\"message\":\"%s\",\"data\":null}", code, message);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);

        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
