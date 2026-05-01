package com.groceryflow.userservice.controller;

import com.groceryflow.userservice.dto.response.ApiResponse;
import com.groceryflow.userservice.dto.response.UserResponse;
import com.groceryflow.userservice.model.User;
import com.groceryflow.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// ═══════════════════════════════════════════════════════════
// UserController: các endpoint cần JWT (protected routes)
//
// Tại sao không dùng @RequestHeader("Authorization") để parse JWT ở đây?
//   → Gateway đã validate JWT và extract userId → inject vào X-User-Id header
//   → Service chỉ cần đọc X-User-Id, không cần parse JWT lại
//   → Nguyên tắc: mỗi tầng chỉ làm 1 việc
// ═══════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    // GET /api/users/profile
    // Header X-User-Id được inject bởi JwtAuthFilter trong api-gateway
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(
            @RequestHeader("X-User-Id") String userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UserResponse response = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success("Profile retrieved", response));
    }
}
