package com.groceryflow.userservice.controller;

import com.groceryflow.userservice.dto.request.LoginRequest;
import com.groceryflow.userservice.dto.request.RefreshTokenRequest;
import com.groceryflow.userservice.dto.request.RegisterRequest;
import com.groceryflow.userservice.dto.response.ApiResponse;
import com.groceryflow.userservice.dto.response.LoginResponse;
import com.groceryflow.userservice.dto.response.UserResponse;
import com.groceryflow.userservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// ═══════════════════════════════════════════════════════════
// AuthController: HTTP layer — chỉ lo parse request và format response
//
// Public paths (gateway không check JWT):
//   POST /api/users/auth/login
//   POST /api/users/auth/register
//   POST /api/users/auth/refresh   ← mới (access token có thể đã hết hạn)
//
// Protected paths (gateway check JWT + inject X-User-Id):
//   POST /api/users/auth/logout    ← mới
// ═══════════════════════════════════════════════════════════
@Slf4j
@RestController
@RequestMapping("/api/users/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", response));
    }

    // PUBLIC — access token có thể đã hết hạn nên không yêu cầu JWT
    // Client gửi refreshToken trong body để lấy accessToken mới
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
    }

    // PROTECTED — Gateway validate access token và inject X-User-Id
    // Tại sao cần Authorization header nếu đã có X-User-Id?
    //   → X-User-Id chỉ là userId string
    //   → Cần Authorization header để lấy jti + exp từ token (cho blacklist)
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("Authorization") String authHeader) {
        authService.logout(userId, authHeader);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }
}
