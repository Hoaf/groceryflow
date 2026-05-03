package com.groceryflow.productservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

// ═══════════════════════════════════════════════════════════
// Stock Entity — số lượng tồn kho của từng sản phẩm
//
// Tại sao quan hệ 1-1 giữa Product và Stock?
//   - Mỗi sản phẩm chỉ có 1 "túi kho" → UNIQUE constraint trên product_id
//   - Khi tạo product mới → tự động tạo stock với quantity = 0
//     (business rule, implement ở service layer)
//
// Tại sao quantity là int (không phải long)?
//   - Tiệm tạp hóa gia đình: tối đa vài nghìn sản phẩm/loại → int đủ
//   - Long: dùng cho kho lớn (Amazon warehouse), over-engineering ở đây
//
// Distributed Lock (Step 2.4):
//   Khi nhiều nhân viên bán cùng lúc → race condition trên quantity
//   Redis lock key: "stock:lock:{productId}"
//   → Chỉ 1 thread được UPDATE stock tại 1 thời điểm
// ═══════════════════════════════════════════════════════════
@Entity
@Table(name = "stocks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stock {

    @Id
    @Column(columnDefinition = "VARCHAR(36)")
    private String id;

    // UNIQUE: 1 product chỉ có 1 stock record
    @Column(name = "product_id", nullable = false, unique = true, length = 36)
    private String productId;

    // quantity mặc định 0 — sản phẩm mới chưa có hàng
    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        // quantity = 0 là giá trị default của int primitive
        // Không cần set nếu caller không truyền → tự nhiên là 0
        // Chỉ ghi chú để rõ ràng intent
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
