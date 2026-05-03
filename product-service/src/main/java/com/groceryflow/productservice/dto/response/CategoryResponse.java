package com.groceryflow.productservice.dto.response;

import com.groceryflow.productservice.model.Category;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

// ═══════════════════════════════════════════════════════════
// CategoryResponse — DTO trả về cho client sau các thao tác CRUD.
//
// Tại sao có static factory method `from(Category)`?
//   - Cách 1: Service tự map từng field → code lặp lại nhiều, dễ quên field mới.
//   - Cách 2 (đang dùng): Đặt logic mapping trong DTO → Single Responsibility:
//     "CategoryResponse biết cách tạo ra chính mình từ Category".
//   - Cách 3: Dùng MapStruct (annotation processor) → zero-boilerplate, type-safe,
//     nhưng cần thêm dependency và interface riêng. Phù hợp khi nhiều DTO phức tạp.
//   → Chọn Cách 2: đủ đơn giản, không over-engineer cho project này.
// ═══════════════════════════════════════════════════════════
@Data
@Builder
public class CategoryResponse {

    private String id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Static factory: chuyển Entity → DTO.
     * Service gọi CategoryResponse.from(entity) thay vì map thủ công từng field.
     */
    public static CategoryResponse from(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
