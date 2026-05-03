package com.groceryflow.productservice.controller;

import com.groceryflow.productservice.dto.request.DeductStockRequest;
import com.groceryflow.productservice.dto.response.ApiResponse;
import com.groceryflow.productservice.dto.response.StockResponse;
import com.groceryflow.productservice.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// ═══════════════════════════════════════════════════════════
// StockController — 2 nhóm endpoint:
//
// 1. /internal/stocks/** — gọi bởi order-service (service-to-service)
//    - KHÔNG qua API Gateway (internal network)
//    - KHÔNG cần JWT auth (trust internal network)
//    - Trong K8s: có thể dùng NetworkPolicy để restrict chỉ order-service gọi được
//
// 2. /api/stocks/** — gọi bởi frontend (qua API Gateway)
//    - QUA API Gateway → JWT được verify ở Gateway trước
//    - Dùng để hiển thị tồn kho cho chủ tiệm trên web dashboard
//
// Tại sao tách /internal và /api?
//   - API Gateway routing: chỉ forward /api/** → product-service
//   - /internal/** KHÔNG được expose ra ngoài qua Gateway
//   - Rõ ràng intent: ai được gọi endpoint này
// ═══════════════════════════════════════════════════════════
@RestController
@RequiredArgsConstructor
@Slf4j
public class StockController {

    private final StockService stockService;

    /**
     * Internal endpoint — gọi bởi order-service để trừ kho khi có đơn hàng.
     *
     * POST /internal/stocks/{productId}/deduct
     *
     * Flow trong Saga:
     *   order-service tạo order → gọi endpoint này → product-service trừ kho
     *   Nếu thành công → order confirmed
     *   Nếu thất bại (Insufficient stock) → order cancelled (saga compensate)
     *
     * @param productId ID sản phẩm cần trừ kho
     * @param request   số lượng cần trừ + orderId để trace
     * @return 200 OK với StockResponse (quantity còn lại)
     *         400 BAD REQUEST nếu không đủ hàng hoặc không acquire lock
     */
    @PostMapping("/internal/stocks/{productId}/deduct")
    public ResponseEntity<ApiResponse<StockResponse>> deduct(
            @PathVariable String productId,
            @Valid @RequestBody DeductStockRequest request) {
        log.info("Internal deduct stock request: productId={}, quantity={}, orderId={}",
                productId, request.getQuantity(), request.getOrderId());
        StockResponse response = stockService.deductStock(productId, request);
        return ResponseEntity.ok(ApiResponse.success("Stock deducted successfully", response));
    }

    /**
     * External endpoint — gọi bởi frontend để hiển thị tồn kho.
     *
     * GET /api/stocks/{productId}
     *
     * Qua API Gateway → JWT đã được verify trước khi đến đây.
     * Stock KHÔNG cache → luôn đọc từ DB → accuracy cao.
     *
     * @param productId ID sản phẩm cần query
     * @return 200 OK với StockResponse (quantity hiện tại)
     *         400 BAD REQUEST nếu productId không tồn tại
     */
    @GetMapping("/api/stocks/{productId}")
    public ResponseEntity<ApiResponse<StockResponse>> getStock(
            @PathVariable String productId) {
        StockResponse response = stockService.getStock(productId);
        return ResponseEntity.ok(ApiResponse.success("Stock retrieved successfully", response));
    }
}
