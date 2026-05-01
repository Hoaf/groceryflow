package com.groceryflow.userservice.service;

import com.groceryflow.userservice.dto.request.LoginRequest;
import com.groceryflow.userservice.dto.request.RefreshTokenRequest;
import com.groceryflow.userservice.dto.request.RegisterRequest;
import com.groceryflow.userservice.dto.response.LoginResponse;
import com.groceryflow.userservice.dto.response.UserResponse;
import com.groceryflow.userservice.model.User;
import com.groceryflow.userservice.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final TokenStoreService tokenStoreService;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    private SecretKey secretKey;
    // Cache parser để verify token (dùng ở refresh và logout)
    private JwtParser jwtParser;

    @PostConstruct
    private void init() {
        secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        jwtParser = Jwts.parser().verifyWith(secretKey).build();
    }

    // ───────────────────────────────────────────────────────
    // LOGIN — cập nhật: thêm jti vào token + lưu refresh token vào Redis
    // ───────────────────────────────────────────────────────
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Failed login attempt for username: {}", request.getUsername());
            throw new IllegalArgumentException("Invalid credentials");
        }

        if (!user.isActive()) {
            throw new IllegalArgumentException("Account is disabled");
        }

        String accessToken = generateToken(user, expirationMs);
        String refreshToken = generateToken(user, refreshExpirationMs);

        // Lưu refresh token vào Redis whitelist
        // Key: rt:{userId}, TTL = 7 ngày
        // Login lại → ghi đè → session cũ tự động bị invalidate
        long refreshTtlSeconds = refreshExpirationMs / 1000;
        tokenStoreService.saveRefreshToken(user.getId(), refreshToken, refreshTtlSeconds);

        log.debug("User logged in: {}", user.getUsername());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expirationMs)
                .user(toUserResponse(user))
                .build();
    }

    // ───────────────────────────────────────────────────────
    // REFRESH — lấy access token mới bằng refresh token
    //
    // Flow:
    // 1. Parse JWT refresh token (verify signature + expiry)
    // 2. Lấy userId từ sub claim
    // 3. So sánh với token đang lưu trong Redis (whitelist check)
    // 4. Nếu khớp → generate access token mới
    //
    // Tại sao cần whitelist check nếu JWT đã verify được chữ ký?
    //   → JWT hợp lệ về mặt cryptographic nhưng có thể đã bị logout
    //   → Whitelist đảm bảo chỉ token "đang active" mới dùng được
    // ───────────────────────────────────────────────────────
    public LoginResponse refresh(RefreshTokenRequest request) {
        Claims claims;
        try {
            claims = jwtParser.parseSignedClaims(request.getRefreshToken()).getPayload();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        String userId = claims.getSubject();

        // Kiểm tra whitelist — token gửi lên phải khớp với token trong Redis
        if (!tokenStoreService.isRefreshTokenValid(userId, request.getRefreshToken())) {
            throw new IllegalArgumentException("Refresh token has been revoked");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String newAccessToken = generateToken(user, expirationMs);
        log.debug("Token refreshed for user: {}", user.getUsername());

        // Trả về cấu trúc giống login để client xử lý đồng nhất
        // refreshToken giữ nguyên — không rotation ở step này
        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(request.getRefreshToken())
                .expiresIn(expirationMs)
                .user(toUserResponse(user))
                .build();
    }

    // ───────────────────────────────────────────────────────
    // LOGOUT — blacklist access token + xóa refresh token
    //
    // userId: lấy từ X-User-Id header (Gateway inject)
    // authHeader: "Bearer <token>" — lấy từ Authorization header
    //
    // Tại sao cần authHeader thay vì chỉ X-User-Id?
    //   → Cần parse jti từ token để làm key blacklist
    //   → Cần exp để tính TTL cho Redis key
    //   → X-User-Id chỉ là userId string, không đủ thông tin
    // ───────────────────────────────────────────────────────
    public void logout(String userId, String authHeader) {
        String token = authHeader.substring(7); // bỏ "Bearer "

        Claims claims;
        try {
            claims = jwtParser.parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            // Token hết hạn hay invalid — vẫn xóa refresh token
            tokenStoreService.deleteRefreshToken(userId);
            return;
        }

        String jti = claims.getId();
        Date exp = claims.getExpiration();

        // Tính thời gian còn lại của access token (giây)
        long remainingSeconds = (exp.getTime() - System.currentTimeMillis()) / 1000;

        if (remainingSeconds > 0) {
            // Blacklist access token cho đến khi nó hết hạn tự nhiên
            // Sau đó Redis tự xóa key → không tốn bộ nhớ mãi mãi
            tokenStoreService.blacklistToken(jti, remainingSeconds);
        }

        // Xóa refresh token khỏi whitelist
        tokenStoreService.deleteRefreshToken(userId);
        log.debug("User logged out: {}", userId);
    }

    // ───────────────────────────────────────────────────────
    // REGISTER
    // ───────────────────────────────────────────────────────
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .active(true)
                .build();

        User saved = userRepository.save(user);
        log.debug("New user registered: {}, role: {}", saved.getUsername(), saved.getRole());

        return toUserResponse(saved);
    }

    // ───────────────────────────────────────────────────────
    // JWT GENERATION
    //
    // Thêm mới: .id(UUID) → jti claim (JWT ID)
    //
    // jti (JWT ID) là gì?
    //   → Claim chuẩn theo RFC 7519
    //   → Unique identifier cho mỗi token
    //   → Dùng làm key blacklist: bl:{jti}
    //   → Ngắn (UUID) hơn hash toàn bộ token string
    // ───────────────────────────────────────────────────────
    private String generateToken(User user, long expiryMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())             // jti — unique per token
                .subject(user.getId())                        // sub = userId
                .claim("role", user.getRole().name())         // OWNER | STAFF
                .claim("username", user.getUsername())        // display name
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)                          // HMAC-SHA256
                .compact();
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
