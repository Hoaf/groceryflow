package com.groceryflow.productservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// ═══════════════════════════════════════════════════════════
// Product Entity — thông tin sản phẩm (mô tả, giá, đơn vị)
//
// Tại sao tách Product và Stock thành 2 bảng?
//   - Product = thông tin "tĩnh" (tên, giá, barcode) → ít thay đổi
//   - Stock = thông tin "động" (số lượng tồn) → thay đổi liên tục
//   - Nếu gộp 1 bảng: mỗi lần bán hàng UPDATE cả row product → lock nhiều hơn
//   - Tách riêng: chỉ lock row stock khi trừ kho → giảm contention
//     (quan trọng khi dùng Distributed Lock ở Step 2.4)
//
// Tại sao dùng BigDecimal cho price thay vì double?
//   - double có lỗi làm tròn nhị phân: 0.1 + 0.2 = 0.30000000000000004
//   - Tiền tệ cần chính xác tuyệt đối → BigDecimal
//
// Tại sao lưu categoryId (String) thay vì @ManyToOne Category?
//   - Microservice design: không dùng JPA join cross-service
//   - Trong cùng service (product-service), có thể dùng @ManyToOne
//     nhưng lưu plain ID đơn giản hơn và đủ dùng cho phase này
// ═══════════════════════════════════════════════════════════
@Entity
@Table(name = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @Column(columnDefinition = "VARCHAR(36)")
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    // barcode UNIQUE → 1 mã vạch chỉ thuộc 1 sản phẩm
    // nullable = true (barcode có thể null nếu sản phẩm không có mã vạch)
    @Column(unique = true, length = 50)
    private String barcode;

    // DECIMAL(10,2) → tối đa 10 chữ số, 2 số thập phân
    // Ví dụ: 99999999.99 (đủ cho giá VND)
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    // unit: đơn vị tính — "cái", "hộp", "kg", "lít", ...
    @Column(nullable = false, length = 20)
    private String unit;

    // Lưu FK thủ công thay vì @ManyToOne để đơn giản hơn
    // Khi cần load Category, gọi CategoryRepository.findById(categoryId)
    @Column(name = "category_id", nullable = false, length = 36)
    private String categoryId;

    // isActive: soft delete — không xóa thật, chỉ đánh dấu inactive
    // Tại sao soft delete?
    //   - Giữ lịch sử bán hàng tham chiếu đến product cũ
    //   - Có thể restore nếu xóa nhầm
    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (isActive == null) isActive = true;  // mặc định active khi tạo mới
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
