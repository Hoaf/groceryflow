package com.groceryflow.productservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

// ═══════════════════════════════════════════════════════════
// DeductStockRequest — request body để trừ tồn kho.
//
// Tại sao tách orderId riêng thay vì chỉ truyền quantity?
//   - orderId dùng để trace: khi log "Stock deducted", ta biết
//     đây là trừ kho cho đơn hàng nào → debug dễ hơn.
//   - Sau này nếu cần idempotency (order-service retry), có thể
//     dùng orderId để check "đã trừ kho cho order này chưa".
//   - orderId là optional vì internal call có thể không có order context
//     (ví dụ: import-service điều chỉnh kho thủ công).
//
// @NotNull + @Positive cho quantity:
//   - @NotNull: không cho phép null → phải truyền quantity
//   - @Positive: quantity > 0 → không cho trừ 0 hoặc số âm
//   - Kết hợp: đảm bảo luôn trừ một số dương hợp lệ
// ═══════════════════════════════════════════════════════════
@Data
public class DeductStockRequest {

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be positive")
    private Integer quantity;

    // Optional — dùng để trace/log, không bắt buộc
    private String orderId;
}
