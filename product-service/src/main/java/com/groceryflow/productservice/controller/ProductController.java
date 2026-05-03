package com.groceryflow.productservice.controller;

import com.groceryflow.productservice.dto.request.CreateProductRequest;
import com.groceryflow.productservice.dto.request.UpdateProductRequest;
import com.groceryflow.productservice.dto.response.ApiResponse;
import com.groceryflow.productservice.dto.response.ProductResponse;
import com.groceryflow.productservice.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// ═══════════════════════════════════════════════════════════
// ProductController — HTTP layer, ánh xạ HTTP request → ProductService.
//
// Endpoints:
//   POST   /api/products                       → 201 tạo mới
//   GET    /api/products                       → 200 danh sách active
//   GET    /api/products/{id}                  → 200 theo ID
//   GET    /api/products/barcode/{barcode}     → 200 theo barcode (scan)
//   GET    /api/products/category/{categoryId} → 200 theo category
//   PUT    /api/products/{id}                  → 200 cập nhật
//   DELETE /api/products/{id}                  → 200 soft delete
//
// Lưu ý thứ tự mapping barcode và {id}:
//   Spring MVC map theo thứ tự khai báo trong class.
//   "/barcode/{barcode}" phải trước "/{id}" để tránh "barcode" bị nhầm thành id.
//   Thực ra Spring phân biệt được vì path khác nhau, nhưng tốt hơn là để rõ ràng.
// ═══════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * POST /api/products
     * Tạo sản phẩm mới → 201 Created.
     *
     * @Valid kích hoạt Bean Validation trên CreateProductRequest:
     *   - @NotBlank, @NotNull, @Positive được validate tự động.
     *   - Nếu lỗi → MethodArgumentNotValidException → GlobalExceptionHandler → 400.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @Valid @RequestBody CreateProductRequest request) {
        ProductResponse response = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", response));
    }

    /**
     * GET /api/products
     * Lấy danh sách tất cả sản phẩm đang active → 200 OK.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> findAll() {
        List<ProductResponse> products = productService.findAll();
        return ResponseEntity.ok(ApiResponse.success("Products fetched successfully", products));
    }

    /**
     * GET /api/products/{id}
     * Lấy sản phẩm theo ID → 200 OK.
     * Nếu không tìm thấy → service ném IllegalArgumentException → 400 Bad Request.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> findById(@PathVariable String id) {
        ProductResponse response = productService.findById(id);
        return ResponseEntity.ok(ApiResponse.success("Product fetched successfully", response));
    }

    /**
     * GET /api/products/barcode/{barcode}
     * Tìm sản phẩm theo barcode — chức năng scan barcode cho mobile app.
     *
     * Tại sao dùng path variable thay vì query param (/api/products?barcode=xxx)?
     *   - Path variable: /api/products/barcode/8934588 → RESTful hơn, resource-based.
     *   - Query param: /api/products?barcode=8934588 → phù hợp khi filter/search nhiều param.
     *   - Barcode là định danh duy nhất → path variable phù hợp hơn.
     *
     * Endpoint này sẽ được cache với Redis trong Step 2.3:
     *   - Nhân viên scan nhanh → nhiều lần cùng barcode → cache hit → không query DB.
     */
    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<ApiResponse<ProductResponse>> findByBarcode(
            @PathVariable String barcode) {
        ProductResponse response = productService.findByBarcode(barcode);
        return ResponseEntity.ok(ApiResponse.success("Product fetched successfully", response));
    }

    /**
     * GET /api/products/category/{categoryId}
     * Lấy danh sách sản phẩm theo category → 200 OK.
     * Chỉ trả sản phẩm active.
     */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> findByCategory(
            @PathVariable String categoryId) {
        List<ProductResponse> products = productService.findByCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success("Products fetched successfully", products));
    }

    /**
     * PUT /api/products/{id}
     * Cập nhật sản phẩm → 200 OK.
     *
     * PUT = replace toàn bộ → client phải gửi đủ: name, price, unit, categoryId.
     * active là optional field → null = giữ nguyên trạng thái.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateProductRequest request) {
        ProductResponse response = productService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", response));
    }

    /**
     * DELETE /api/products/{id}
     * Soft delete sản phẩm (set active=false) → 200 OK.
     *
     * Tại sao trả 200 OK + body thay vì 204 No Content?
     *   - 204 No Content: không có response body → client không biết kết quả.
     *   - 200 OK + ApiResponse: đồng nhất với tất cả endpoints khác → DX tốt hơn.
     *   - Convention trong project: luôn wrap response trong ApiResponse.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", null));
    }
}
