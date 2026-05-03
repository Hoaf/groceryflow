package com.groceryflow.productservice.dto.response;

import com.groceryflow.productservice.model.Product;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ═══════════════════════════════════════════════════════════
// ProductResponse — DTO trả về cho client sau mọi thao tác với Product.
//
// Tại sao cần ProductResponse thay vì trả thẳng Product entity?
//   - Entity có @Column, @PrePersist... — annotation JPA không nên lộ ra ngoài.
//   - Entity trong tương lai có thể có field nhạy cảm (cost, margin...) → DTO kiểm soát
//     chính xác field nào được expose.
//   - Giúp tách biệt "DB model" và "API contract" → có thể thay đổi DB schema
//     mà không break API (và ngược lại).
//
// Static factory method "from(Product product)":
//   - Thay vì inject ModelMapper hoặc viết mapping ở nhiều nơi.
//   - Mapping logic nằm trong DTO → dễ tìm, dễ test.
//   - Alternatives:
//     Cách 1: ModelMapper / MapStruct → tự động map theo tên field → ít code hơn,
//             nhưng khó debug khi field không khớp.
//     Cách 2: Static factory như đây → explicit mapping → code nhiều hơn nhưng rõ ràng.
//     → Chọn Cách 2 vì: project nhỏ, rõ ràng quan trọng hơn tiết kiệm code.
// ═══════════════════════════════════════════════════════════
@Data
@Builder
public class ProductResponse {

    private String id;
    private String name;
    private String barcode;
    private BigDecimal price;
    private String unit;
    private String categoryId;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Static factory: Product entity → ProductResponse DTO.
     * Gọi ở service layer sau khi save/find để trả về client.
     */
    public static ProductResponse from(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .barcode(product.getBarcode())
                .price(product.getPrice())
                .unit(product.getUnit())
                .categoryId(product.getCategoryId())
                .active(product.isActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
