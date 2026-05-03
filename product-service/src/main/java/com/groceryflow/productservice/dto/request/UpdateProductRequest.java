package com.groceryflow.productservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

// ═══════════════════════════════════════════════════════════
// UpdateProductRequest — DTO nhận dữ liệu từ client khi cập nhật sản phẩm.
//
// Khác biệt với CreateProductRequest:
//   - Có thêm field "active" (Boolean, nullable) để activate/deactivate sản phẩm.
//   - PUT = replace toàn bộ → client phải gửi đủ name, price, unit, categoryId.
//   - active là Optional → chỉ update nếu client truyền vào (non-null).
//
// Tại sao active là Boolean (wrapper) thay vì boolean (primitive)?
//   - boolean primitive: luôn có giá trị → không phân biệt "client không truyền"
//     vs "client truyền false".
//   - Boolean wrapper: null = client không truyền → service giữ nguyên giá trị cũ.
//   - Pattern: dùng wrapper type (Integer, Boolean...) cho optional field trong DTO.
// ═══════════════════════════════════════════════════════════
@Data
public class UpdateProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    // barcode optional — giống CreateProductRequest
    private String barcode;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    @NotBlank(message = "Unit is required")
    private String unit;

    @NotBlank(message = "Category ID is required")
    private String categoryId;

    // active: optional — null = không thay đổi trạng thái active
    // true = activate lại sản phẩm đã bị deactivate
    // false = soft-delete sản phẩm (deactivate)
    // Tại sao có thể activate/deactivate qua PUT /api/products/{id}?
    //   - Đơn giản hơn: không cần thêm endpoint riêng PATCH /api/products/{id}/status.
    //   - Chủ tiệm muốn "sửa thông tin + ẩn sản phẩm" trong 1 lần gọi API.
    //   - Trade-off: PUT semantics "replace all" bị vi phạm nhẹ (active có thể bỏ qua nếu null).
    //     Nhưng acceptable cho MVP.
    private Boolean active;
}
