package com.fintech.wallet.modules.auth.repository;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import java.time.Duration;

@Repository
public class BlacklistTokenRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    public BlacklistTokenRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Đưa Token vào sổ đen với thời gian sống (TTL) cụ thể.
     */
    public void blacklistToken(String token, long remainingSeconds) {
        if (remainingSeconds > 0) {
            String key = BLACKLIST_PREFIX + token;
            redisTemplate.opsForValue().set(key, "revoked", Duration.ofSeconds(remainingSeconds));
        }
    }

    /**
     * Kiểm tra nhanh Token có nằm trong sổ đen không. (O(1) trong Redis)
     */
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }
}
