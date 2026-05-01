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
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String CLAIM_ROLE = "role";
    private static final String BLACKLIST_PREFIX = "bl:";

    private final RouteConfig routeConfig;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // ═══════════════════════════════════════════════════════════
    // Tại sao dùng ReactiveStringRedisTemplate thay vì StringRedisTemplate?
    //
    // Gateway là WebFlux (reactive/non-blocking):
    //   - Mọi operation phải trả về Mono/Flux — không được block thread
    //   - StringRedisTemplate (blocking): gọi .get() sẽ block thread → phá vỡ reactive model
    //   - ReactiveStringRedisTemplate: trả Mono<String> → non-blocking → phù hợp WebFlux
    //
    // user-service dùng StringRedisTemplate vì nó là servlet (blocking) — OK
    // api-gateway phải dùng Reactive version vì là WebFlux
    // ═══════════════════════════════════════════════════════════
    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private JwtParser jwtParser;

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

        Claims claims;
        try {
            claims = jwtParser.parseSignedClaims(token).getPayload();
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return unauthorized(exchange);
        }

        String userId = claims.getSubject();
        String userRole = claims.get(CLAIM_ROLE, String.class);
        String jti = claims.getId();  // JWT ID — dùng để check blacklist

        // ═══════════════════════════════════════════════════════════
        // Blacklist check — reactive style
        //
        // hasKey() trả Mono<Boolean> → flatMap để chain tiếp
        // Nếu key tồn tại trong Redis → token đã bị logout → 401
        // Nếu không → forward request bình thường
        //
        // Tại sao check sau khi parse JWT (không phải trước)?
        //   → Parse JWT để lấy jti (cần jti để query Redis)
        //   → Nếu JWT invalid thì không cần query Redis
        //   → Tối ưu: tránh Redis roundtrip khi token sai chữ ký
        // ═══════════════════════════════════════════════════════════
        return redisTemplate.hasKey(BLACKLIST_PREFIX + jti)
                .flatMap(isBlacklisted -> {
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        log.warn("Blacklisted token used for path: {}", path);
                        return unauthorized(exchange);
                    }

                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header(HEADER_USER_ID, userId)
                            .header(HEADER_USER_ROLE, userRole != null ? userRole : "")
                            .build();

                    log.debug("JWT valid for user: {}, role: {}", userId, userRole);
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
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
