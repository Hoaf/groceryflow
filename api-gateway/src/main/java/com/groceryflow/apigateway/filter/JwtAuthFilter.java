package com.groceryflow.apigateway.filter;

import com.groceryflow.apigateway.config.RouteConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
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

    // Constants — tránh magic strings rải rác trong code
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String CLAIM_ROLE = "role";

    private final RouteConfig routeConfig;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // Cache JwtParser — khởi tạo 1 lần lúc startup, tái dùng cho mọi request
    // Lý do: Jwts.parser().build() (kèm key setup) tốn CPU, không nên gọi mỗi request
    private JwtParser jwtParser;

    // @PostConstruct: chạy sau khi Spring inject xong tất cả dependencies
    // Đây là nơi an toàn để khởi tạo các object phụ thuộc vào @Value
    @PostConstruct
    private void init() {
        jwtParser = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return unauthorized(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();

            String userId = claims.getSubject();
            String userRole = claims.get(CLAIM_ROLE, String.class);

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header(HEADER_USER_ID, userId)
                    .header(HEADER_USER_ROLE, userRole != null ? userRole : "")
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

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
