package com.fintech.wallet.modules.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {
    private final SecretKey key;
    private final long jwtExpiration; // Ví dụ: 15 phút (900000 ms)

    public JwtTokenProvider(
            @Value("${jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250655368566D5971}") String secret,
            @Value("${jwt.expiration:900000}") long jwtExpiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpiration = jwtExpiration;
    }

    // 1. Tạo Access Token (Đưa thêm ROLE vào payload)
    public String generateAccessToken(UUID userId, String role) {
        var now = new Date();
        var expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role) // Nhét Role vào token để Gateway và các Service phân quyền
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    // 2. Tạo Refresh Token (Chỉ là UUID ngẫu nhiên)
    public String generateRefreshToken() {
        return UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
    }

    // 3. Tính số giây còn lại của Access Token (Để ném vào Redis Blacklist khi Logout)
    public long getExpirationInSeconds(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Date expiration = claims.getExpiration();
            Date now = new Date();
            if (expiration != null && expiration.after(now)) {
                return (expiration.getTime() - now.getTime()) / 1000;
            }
        } catch (Exception e) {
            // Token đã hết hạn hoặc sai chữ ký
        }
        return 0;
    }

    // 4. Lấy UUID từ Token
    public UUID getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return UUID.fromString(claims.getSubject());
    }
}
