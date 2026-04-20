package com.groceryflow.apigateway.filter;

import com.groceryflow.apigateway.config.RouteConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

// ═══════════════════════════════════════════════════════════
// Tại sao dùng GlobalFilter thay vì RouteFilter?
//
// RouteFilter: chỉ áp dụng cho 1 route cụ thể → phải khai báo ở mỗi route
// GlobalFilter: áp dụng cho TẤT CẢ routes → khai báo 1 lần
//
// JWT cần check mọi request (trừ public paths) → GlobalFilter phù hợp hơn
// ═══════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════
// Tại sao Gateway dùng reactive (Mono/Flux) thay servlet?
//
// Servlet (blocking): mỗi request chiếm 1 thread → 1000 request = 1000 threads
// Reactive (non-blocking): 1 thread xử lý nhiều request → ít thread hơn, hiệu quả hơn
//
// API Gateway nhận rất nhiều traffic → reactive giúp scale tốt hơn
// Mono<T>: stream 0 hoặc 1 phần tử (như Optional nhưng async)
// Flux<T>: stream 0..N phần tử
// ═══════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════
// JWT validate ở Gateway vs mỗi service tự validate?
//
// Cách 1 — Chỉ Gateway validate (cách này):
//   + Không lặp code ở mỗi service
//   + Services tin tưởng Gateway (internal network)
//   - Nếu ai bypass Gateway → service không có bảo vệ
//
// Cách 2 — Mỗi service tự validate:
//   + Bảo mật sâu hơn (defense in depth)
//   - Lặp code JWT ở mỗi service
//   - Tăng latency (validate 2 lần)
//
// → Chọn Cách 1 vì: internal network đã isolated, đủ an toàn cho tiệm tạp hóa
// ═══════════════════════════════════════════════════════════

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final RouteConfig routeConfig;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // AntPathMatcher: so khớp URL pattern kiểu Ant (/api/users/auth/**)
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Bước 1: Kiểm tra public path → skip JWT check
        if (isPublicPath(path)) {
            log.debug("Public path, skipping JWT check: {}", path);
            return chain.filter(exchange);
        }

        // Bước 2: Lấy token từ Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return unauthorized(exchange);
        }

        String token = authHeader.substring(7); // bỏ "Bearer " (7 ký tự)

        // Bước 3: Validate token
        try {
            Claims claims = validateToken(token);

            // Bước 4: Lấy user info từ claims, forward xuống service qua header
            // ─────────────────────────────────────────────────────────────
            // Tại sao forward qua header thay vì gọi lại Gateway?
            // → Service cần biết user nào đang gọi (để lưu created_by, check quyền)
            // → Forward qua header: đơn giản, không cần thêm round-trip
            // → Dùng prefix X- để phân biệt với header chuẩn HTTP
            // ─────────────────────────────────────────────────────────────
            String userId = claims.getSubject();
            String userRole = claims.get("role", String.class);

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Role", userRole != null ? userRole : "")
                    .build();

            log.debug("JWT valid for user: {}, role: {}", userId, userRole);
            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return unauthorized(exchange);
        }
    }

    private boolean isPublicPath(String path) {
        return routeConfig.getPublicPaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Claims validateToken(String token) {
        // Keys.hmacShaKeyFor: tạo SecretKey từ secret string
        // JJWT 0.12.x dùng parseSignedClaims thay vì parseClaimsJws (deprecated)
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
        // setComplete(): kết thúc response, không forward xuống service
    }

    @Override
    public int getOrder() {
        return -1;
        // getOrder(): thứ tự ưu tiên của filter, số càng nhỏ càng chạy trước
        // -1: chạy trước tất cả filter mặc định của Spring Cloud Gateway
    }
}
