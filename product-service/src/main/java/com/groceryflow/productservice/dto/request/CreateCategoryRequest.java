package com.groceryflow.productservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// ═══════════════════════════════════════════════════════════
// CreateCategoryRequest — DTO nhận dữ liệu từ client khi tạo Category mới.
//
// Tại sao dùng DTO thay vì nhận thẳng Entity?
//   - Entity là "bản đồ" của bảng DB — có trường id, createdAt, updatedAt
//     mà client không nên (và không cần) gửi lên.
//   - Nếu nhận thẳng Entity: client có thể gửi id tùy ý → security risk.
//   - DTO chỉ chứa đúng những field client được phép gửi → separation of concerns.
//
// Bean Validation (@NotBlank, @Size):
//   - Được xử lý bởi Spring tự động khi controller có @Valid trước @RequestBody.
//   - Nếu vi phạm → Spring ném MethodArgumentNotValidException → GlobalExceptionHandler
//     bắt và trả về 400 Bad Request tự động.
// ═══════════════════════════════════════════════════════════
@Data
public class CreateCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name must not exceed 100 characters")
    private String name;

    // description là optional — không có @NotBlank
    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;
}
