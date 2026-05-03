package com.groceryflow.productservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// ═══════════════════════════════════════════════════════════
// UpdateCategoryRequest — DTO cho PUT /api/categories/{id}
//
// Tại sao tách Create và Update thành 2 DTO riêng?
//   - Cách 1: Dùng chung 1 DTO → thường phải nullable tất cả field để
//     hỗ trợ partial update (PATCH). Dễ nhầm lẫn validation.
//   - Cách 2 (đang dùng): 2 DTO riêng → rõ ràng từng use case.
//     PUT thường update toàn bộ object → cả name và description đều được gửi.
//   - Cách 3: Dùng PATCH với @JsonMergePatch → phức tạp hơn, overkill cho CRUD đơn giản.
//   → Chọn Cách 2: simple, explicit, dễ validate.
// ═══════════════════════════════════════════════════════════
@Data
public class UpdateCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name must not exceed 100 characters")
    private String name;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;
}
