package com.groceryflow.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

// ═══════════════════════════════════════════════════════════
// TokenStoreService: tất cả thao tác Redis liên quan đến token
//
// Tách khỏi AuthService vì:
//   - AuthService lo business logic (validate, generate token)
//   - TokenStoreService lo infrastructure (Redis key scheme, TTL)
//   - Single Responsibility Principle
//
// Redis key scheme:
//   rt:{userId}  → refreshToken string     TTL = 7 ngày
//   bl:{jti}     → "1"                     TTL = remaining access token lifetime
//
// Tại sao dùng StringRedisTemplate thay vì RedisTemplate<K,V>?
//   - StringRedisTemplate: serialize cả key và value thành String
//   - RedisTemplate mặc định: dùng Java serialization → binary, không đọc được bằng redis-cli
//   - Với token (String) → StringRedisTemplate là đủ và dễ debug hơn
// ═══════════════════════════════════════════════════════════
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenStoreService {

    private static final String PREFIX_REFRESH = "rt:";
    private static final String PREFIX_BLACKLIST = "bl:";

    private final StringRedisTemplate redisTemplate;

    // ───────────────────────────────────────────────────────
    // REFRESH TOKEN — Whitelist
    // ───────────────────────────────────────────────────────

    // Lưu refresh token khi login
    // Key: rt:{userId} → 1 user chỉ có 1 refresh token
    // Login lại → ghi đè token cũ (invalidate session cũ tự động)
    public void saveRefreshToken(String userId, String refreshToken, long ttlSeconds) {
        String key = PREFIX_REFRESH + userId;
        redisTemplate.opsForValue().set(key, refreshToken, ttlSeconds, TimeUnit.SECONDS);
        log.debug("Saved refresh token for user: {}", userId);
    }

    // Kiểm tra refresh token có hợp lệ không
    // So sánh token gửi lên với token đang lưu trong Redis
    public boolean isRefreshTokenValid(String userId, String refreshToken) {
        String key = PREFIX_REFRESH + userId;
        String stored = redisTemplate.opsForValue().get(key);
        return refreshToken.equals(stored);
    }

    // Xóa refresh token khi logout
    public void deleteRefreshToken(String userId) {
        redisTemplate.delete(PREFIX_REFRESH + userId);
        log.debug("Deleted refresh token for user: {}", userId);
    }

    // ───────────────────────────────────────────────────────
    // ACCESS TOKEN — Blacklist
    // ───────────────────────────────────────────────────────

    // Thêm access token vào blacklist khi logout
    // TTL = thời gian còn lại của token → key tự xóa khi token hết hạn
    // Tại sao TTL = remaining time?
    //   → Sau khi token hết hạn tự nhiên, nó cũng không dùng được nữa
    //   → Không cần giữ trong blacklist mãi mãi → tiết kiệm Redis memory
    public void blacklistToken(String jti, long remainingSeconds) {
        String key = PREFIX_BLACKLIST + jti;
        redisTemplate.opsForValue().set(key, "1", remainingSeconds, TimeUnit.SECONDS);
        log.debug("Blacklisted token jti: {}", jti);
    }

    // Gateway dùng để check xem token có bị blacklist không
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX_BLACKLIST + jti));
    }
}
