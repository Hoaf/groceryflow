package com.groceryflow.productservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

// ═══════════════════════════════════════════════════════════
// Category Entity — nhóm sản phẩm (ví dụ: Đồ uống, Bánh kẹo, ...)
//
// Tại sao tách Category thành bảng riêng?
//   - Nếu lưu category thẳng vào products (column "category" = string):
//     → Không thể rename category một lần cho tất cả sản phẩm
//     → Không thể thêm metadata (mô tả, icon...) cho category
//   - Normalize DB: Category là một thực thể độc lập → bảng riêng
//     → Sửa tên category 1 nơi, tất cả sản phẩm tự cập nhật
// ═══════════════════════════════════════════════════════════
@Entity
@Table(name = "categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    @Column(columnDefinition = "VARCHAR(36)")
    private String id;

    // name UNIQUE → không có 2 category trùng tên
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // @PrePersist: tự động chạy trước khi INSERT vào DB
    // Caller không cần tự set id, createdAt, updatedAt
    @PrePersist
    private void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        createdAt = updatedAt = LocalDateTime.now();
    }

    // @PreUpdate: tự động cập nhật updatedAt trước mỗi UPDATE
    @PreUpdate
    private void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
