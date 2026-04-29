package com.groceryflow.userservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(columnDefinition = "VARCHAR(36)")
    private String id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;          // BCrypt hash — không bao giờ lưu plain text

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // @PrePersist: chạy tự động trước khi INSERT
    // Set UUID và timestamp tại đây — caller không cần lo
    // Tại sao set UUID thủ công thay vì @GeneratedValue?
    // → Outbox Pattern (Phase 2) cần biết id trước khi ghi DB
    @PrePersist
    private void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
