package com.groceryflow.productservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

// ═══════════════════════════════════════════════════════════
// RedisConfig — cấu hình RedisTemplate và ObjectMapper cho product-service.
//
// ## Tại sao cần cấu hình RedisTemplate?
//
// Spring Boot tự-configure RedisTemplate<Object, Object> với JdkSerializationRedisSerializer.
// Vấn đề:
//   - JdkSerializationRedisSerializer serialize thành binary → không đọc được bằng redis-cli.
//   - Mỗi lần debug cache phải dùng Java code để decode → rất bất tiện khi phát triển.
//   - Khi thay đổi class structure → deserialization có thể fail (vì JDK serialization gắn
//     với class version).
//
// Cách 1: JdkSerializationRedisSerializer (default của Spring Boot)
//   Pros: không cần config, tự động.
//   Cons: binary format → không đọc được trực tiếp, version-sensitive.
//
// Cách 2: RedisTemplate<String, String> + Jackson JSON (đây) — Cache-Aside manual
//   Pros: JSON dễ đọc bằng redis-cli, flexible, debug dễ.
//   Cons: phải tự serialize/deserialize bằng ObjectMapper.
//
// Cách 3: Spring Cache Abstraction (@Cacheable, @CacheEvict) + RedisCacheManager
//   Pros: annotation-driven → ít code ở service, tự động cache/evict.
//   Cons: ẩn logic cache → khó hiểu khi học, khó control TTL per-entry.
//
// → Chọn Cách 2 vì: project này dùng để HỌC → explicit code giúp hiểu cơ chế rõ hơn.
//   Trong production, Cách 3 thường được dùng.
//
// ## Tại sao dùng StringRedisSerializer cho key VÀ value?
//
//   - Key: luôn là String (ví dụ "product:abc-123") → StringRedisSerializer tối ưu.
//   - Value: chúng ta tự serialize thành JSON String trước khi lưu → cũng là String.
//   - Nhất quán: cả key và value đều là String → redis-cli GET product:abc-123 trả ra JSON.
// ═══════════════════════════════════════════════════════════
@Configuration
public class RedisConfig {

    /**
     * RedisTemplate<String, String> — template làm việc với Redis.
     *
     * Dùng StringRedisSerializer cho tất cả key/value → lưu dạng plain text JSON.
     * Khi debug: redis-cli GET product:abc-123 → thấy ngay JSON của product.
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);

        return template;
    }

    /**
     * ObjectMapper — Jackson instance để serialize/deserialize Java objects ↔ JSON.
     *
     * Cần register JavaTimeModule để serialize LocalDateTime:
     *   - Mặc định Jackson không biết cách serialize LocalDateTime → throw exception.
     *   - JavaTimeModule thêm support cho java.time.* (LocalDate, LocalDateTime, ZonedDateTime...).
     *   - WRITE_DATES_AS_TIMESTAMPS=false → serialize thành ISO-8601 string ("2025-05-03T10:00:00")
     *     thay vì số nguyên timestamp → dễ đọc hơn trong Redis.
     *
     * Tại sao define @Bean thay vì new ObjectMapper() trực tiếp trong ProductService?
     *   - @Bean → Spring quản lý lifecycle, singleton → không tạo nhiều instance.
     *   - Có thể inject vào nhiều service khác → reuse configuration.
     *   - ObjectMapper thread-safe sau khi configure → safe để share.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
