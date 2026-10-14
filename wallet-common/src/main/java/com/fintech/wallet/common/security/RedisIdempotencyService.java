package com.fintech.wallet.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisIdempotencyService {

    private final StringRedisTemplate redisTemplate;

    // Key prefix để phân biệt với các dữ liệu khác trong Redis
    private static final String IDEMPOTENCY_PREFIX = "idempotency:";

    /**
     * Thử khóa idempotencyKey bằng Redis SETNX với thời gian sống (TTL)
     * @param idempotencyKey Key gửi lên từ Client
     * @param ttlInSeconds Thời gian khóa key (Ví dụ: 120 giây)
     * @return true nếu ghi thành công (Key mới), false nếu key đã tồn tại (Request bị trùng)
     */
    public boolean lockKey(String idempotencyKey, long ttlInSeconds) {
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;

        // setIfAbsent tương đương với lệnh SETNX trong Redis
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "LOCKED", Duration.ofSeconds(ttlInSeconds));

        return Boolean.TRUE.equals(success);
    }

    /**
     * Giải phóng key trong trường hợp xử lý giao dịch gặp lỗi hệ thống (để Client có thể retry)
     */
    public void unlockKey(String idempotencyKey) {
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        redisTemplate.delete(redisKey);
    }
}