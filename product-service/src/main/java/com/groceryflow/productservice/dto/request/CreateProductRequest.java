package com.groceryflow.productservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

// ═══════════════════════════════════════════════════════════
// CreateProductRequest — DTO nhận dữ liệu từ client khi tạo sản phẩm mới.
//
// Tại sao dùng DTO thay vì nhận thẳng Entity Product?
//   - Vấn đề bảo mật: nếu nhận thẳng Entity, client có thể truyền field "id",
//     "active", "createdAt"... và ghi đè giá trị hệ thống (Mass Assignment Attack).
//   - DTO định nghĩa chính xác "client được phép truyền field nào".
//   - Entity có thể có @Version, @CreatedDate... không muốn client chạm vào.
//
// Validation annotations — Bean Validation (JSR-380):
//   - @NotBlank: không được null, không được blank (chỉ space).
//   - @NotNull: không được null (nhưng có thể blank — ít dùng cho String).
//   - @Positive: > 0 (dùng cho price — giá không được âm hoặc bằng 0).
//   - @DecimalMin: giá trị tối thiểu cho BigDecimal.
//   Spring tự validate khi controller dùng @Valid → nếu lỗi → 400 Bad Request.
// ═══════════════════════════════════════════════════════════
@Data
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    // barcode là optional — một số sản phẩm không có mã vạch (sản phẩm tự làm, hàng lẻ...)
    // null hoặc blank đều chấp nhận được → service sẽ bỏ qua check duplicate nếu null/blank
    private String barcode;

    // @NotNull: price không được null
    // @Positive: price phải > 0 (không bán miễn phí, không âm)
    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    // unit: đơn vị tính — "cái", "hộp", "kg", "lít"...
    // Tại sao không dùng Enum?
    //   - Enum cứng nhắc hơn — nếu thêm đơn vị mới phải recompile.
    //   - String linh hoạt, đủ cho MVP tiệm tạp hóa gia đình.
    //   - Production: có thể thêm @Pattern(regexp="...") để validate format.
    @NotBlank(message = "Unit is required")
    private String unit;

    // categoryId: FK tham chiếu đến Category.
    // Service sẽ verify categoryId tồn tại trước khi lưu.
    @NotBlank(message = "Category ID is required")
    private String categoryId;
}
