package com.groceryflow.productservice.dto.response;

import com.groceryflow.productservice.model.Stock;
import lombok.Builder;
import lombok.Data;

// ═══════════════════════════════════════════════════════════
// StockResponse — DTO trả về thông tin tồn kho.
//
// Tại sao không trả thẳng Stock entity?
//   - Entity gắn với DB schema → thay đổi schema phải thay đổi API contract.
//   - DTO cho phép chọn lọc field: Stock entity có id, createdAt, updatedAt
//     → caller thường không cần → ẩn đi để response gọn.
//   - Separation of concerns: persistence layer ≠ API layer.
//
// Tại sao chỉ có productId và quantity?
//   - Order-service chỉ cần biết: "sản phẩm này còn bao nhiêu sau khi trừ?"
//   - Frontend cũng chỉ hiển thị số lượng tồn kho → 2 field là đủ.
//   - YAGNI (You Ain't Gonna Need It): không thêm field mà chưa có use case.
// ═══════════════════════════════════════════════════════════
@Data
@Builder
public class StockResponse {

    private String productId;

    // Số lượng còn lại sau khi trừ (hoặc hiện tại nếu chỉ query)
    private int quantity;

    /**
     * Static factory method — chuyển đổi Stock entity → StockResponse DTO.
     *
     * Tại sao dùng static factory thay vì constructor?
     *   - Tên rõ nghĩa hơn: StockResponse.from(stock) → biết ngay là map từ entity.
     *   - Nếu sau này cần transform thêm field (ví dụ: format, compute) → sửa 1 chỗ.
     *   - Builder pattern từ Lombok: type-safe, readable, dễ extend.
     */
    public static StockResponse from(Stock stock) {
        return StockResponse.builder()
                .productId(stock.getProductId())
                .quantity(stock.getQuantity())
                .build();
    }
}
