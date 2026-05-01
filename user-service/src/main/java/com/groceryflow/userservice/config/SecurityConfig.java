package com.groceryflow.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

// ═══════════════════════════════════════════════════════════
// Tại sao dùng Spring Security ở user-service?
//
// Chỉ để lấy BCryptPasswordEncoder — không dùng SecurityFilterChain để auth.
// JWT auth đã được API Gateway xử lý trước khi request tới đây.
//
// Nếu không có SecurityConfig:
//   - Spring Security auto-protect tất cả endpoints (mặc định yêu cầu login)
//   - /api/users/auth/login bị block → không login được!
//
// → Phải khai báo SecurityFilterChain permit all để disable auto-protection
// ═══════════════════════════════════════════════════════════
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // BCryptPasswordEncoder: thuật toán hash password an toàn
    // Tại sao BCrypt thay vì MD5/SHA?
    //   - MD5/SHA: hash nhanh → dễ brute force
    //   - BCrypt: có "cost factor" → hash chậm có chủ đích → brute force tốn thời gian
    //   - BCrypt tự thêm "salt" ngẫu nhiên → cùng password không ra cùng hash
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Tắt Spring Security auto-protection:
    //   - csrf: không cần (stateless JWT, không dùng session/cookie)
    //   - sessionManagement: STATELESS — không tạo HTTP session
    //   - authorizeRequests: permitAll — JWT check đã ở Gateway
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
