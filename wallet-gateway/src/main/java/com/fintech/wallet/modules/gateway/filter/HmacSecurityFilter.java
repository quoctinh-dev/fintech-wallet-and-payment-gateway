package com.fintech.wallet.modules.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.modules.gateway.config.HmacProperties;
import com.fintech.wallet.modules.gateway.dto.ApiResponse;
import com.fintech.wallet.modules.gateway.util.HmacUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Component
public class HmacSecurityFilter implements GlobalFilter, Ordered {

    private final HmacUtil hmacUtil;
    private final HmacProperties hmacProperties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public HmacSecurityFilter(HmacUtil hmacUtil, HmacProperties hmacProperties, ObjectMapper objectMapper) {
        this.hmacUtil = hmacUtil;
        this.hmacProperties = hmacProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestPath = request.getURI().getPath();

        // 1. Kiểm tra xem route hiện tại có nằm trong danh sách cần bảo vệ không
        if (!isPathProtected(requestPath)) {
            return chain.filter(exchange);
        }

        // 2. Kiểm tra Headers bắt buộc
        String clientSignature = request.getHeaders().getFirst("X-Signature");
        String timestampStr = request.getHeaders().getFirst("X-Timestamp");

        if (clientSignature == null || timestampStr == null) {
            return onError(exchange, "Missing X-Signature or X-Timestamp");
        }

        // 3. Chống Replay Attack (5 phút)
        long requestTime;
        try {
            requestTime = Long.parseLong(timestampStr);
        } catch (Exception e) {
            return onError(exchange, "Invalid X-Timestamp");
        }

        long currentTime = Instant.now().toEpochMilli();
        if (Math.abs(currentTime - requestTime) > 5 * 60 * 1000L) {
            return onError(exchange, "Request Expired (Replay Attack Detected)");
        }

        // 4. Tuyến đường không mang Body (GET, DELETE)
        if (request.getMethod() == HttpMethod.GET || request.getMethod() == HttpMethod.DELETE) {
            String payloadToHash = request.getMethod().name() + request.getURI().getPath() + timestampStr;
            return verifyHmac(payloadToHash, clientSignature, exchange, chain);
        }

        // 5. Tuyến đường mang Body (POST, PUT, PATCH) - Tái tạo Body an toàn với Flux.defer
        return DataBufferUtils.join(request.getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    String bodyString = new String(bytes, StandardCharsets.UTF_8);
                    String payloadToHash = request.getMethod().name() + request.getURI().getPath() + timestampStr + bodyString;

                    ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(request) {
                        @Override
                        public HttpHeaders getHeaders() {
                            HttpHeaders httpHeaders = new HttpHeaders();
                            httpHeaders.putAll(super.getHeaders());
                            httpHeaders.setContentLength(bytes.length);
                            return httpHeaders;
                        }

                        @Override
                        public Flux<DataBuffer> getBody() {
                            return Flux.defer(() -> {
                                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                                return Flux.just(buffer);
                            });
                        }
                    };

                    ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
                    return verifyHmac(payloadToHash, clientSignature, mutatedExchange, chain);
                });
    }

    private boolean isPathProtected(String path) {
        List<String> protectedPaths = hmacProperties.getProtectedPaths();
        if (protectedPaths == null || protectedPaths.isEmpty()) {
            return false;
        }
        return protectedPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> verifyHmac(String payload, String clientSignature, ServerWebExchange exchange, GatewayFilterChain chain) {
        String serverSignature = hmacUtil.calculateHmac(payload);

        System.out.println("========== HMAC SECURITY CHECK ==========");
        System.out.println("Payload: " + payload);
        System.out.println("Client Sig: " + clientSignature);
        System.out.println("Server Sig: " + serverSignature);

        if (!serverSignature.equals(clientSignature)) {
            return onError(exchange, "HMAC Signature Invalid! Data might be tampered.");
        }
        return chain.filter(exchange);
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message) {
        System.out.println("BỊ CHẶN BỞI HMAC: " + message);
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Khởi tạo DTO ApiResponse chuẩn của hệ thống (Mã lỗi 5003 = FORBIDDEN)
        ApiResponse<Void> apiResponse = ApiResponse.error(5003, "Bảo mật Gateway: " + message);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(apiResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -2;
    }
}