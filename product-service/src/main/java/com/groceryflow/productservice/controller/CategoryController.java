package com.groceryflow.productservice.controller;

import com.groceryflow.productservice.dto.request.CreateCategoryRequest;
import com.groceryflow.productservice.dto.request.UpdateCategoryRequest;
import com.groceryflow.productservice.dto.response.ApiResponse;
import com.groceryflow.productservice.dto.response.CategoryResponse;
import com.groceryflow.productservice.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// ═══════════════════════════════════════════════════════════
// CategoryController — HTTP layer, ánh xạ HTTP request → service method.
//
// Trách nhiệm của Controller (chỉ 3 việc):
//   1. Parse HTTP request (path variable, request body)
//   2. Gọi service method
//   3. Wrap kết quả vào ApiResponse + ResponseEntity với HTTP status phù hợp
//
// Tại sao dùng ResponseEntity thay vì trả thẳng ApiResponse?
//   - ResponseEntity cho phép set HTTP status code linh hoạt (201, 200, 404...).
//   - Nếu trả thẳng ApiResponse → luôn 200 OK → client không biết 201 Created.
//   - HTTP status code là ngôn ngữ của REST API — phải dùng đúng.
//
// @Valid — kích hoạt Bean Validation trên @RequestBody:
//   - Spring tự động validate @NotBlank, @Size... trước khi vào method.
//   - Nếu invalid → MethodArgumentNotValidException → GlobalExceptionHandler → 400.
// ═══════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * POST /api/categories
     * Tạo category mới → trả 201 Created.
     *
     * Tại sao 201 Created thay vì 200 OK?
     *   - REST convention: POST tạo resource mới → 201 Created.
     *   - 200 OK dùng cho các thao tác không tạo resource mới (GET, update không có body...).
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse response = categoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", response));
    }

    /**
     * GET /api/categories
     * Lấy danh sách tất cả categories → 200 OK.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> findAll() {
        List<CategoryResponse> categories = categoryService.findAll();
        return ResponseEntity.ok(ApiResponse.success("Categories fetched successfully", categories));
    }

    /**
     * GET /api/categories/{id}
     * Lấy 1 category theo ID → 200 OK.
     * Nếu không tìm thấy → service ném IllegalArgumentException → GlobalExceptionHandler → 400.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> findById(@PathVariable String id) {
        CategoryResponse response = categoryService.findById(id);
        return ResponseEntity.ok(ApiResponse.success("Category fetched successfully", response));
    }

    /**
     * PUT /api/categories/{id}
     * Cập nhật toàn bộ category → 200 OK.
     *
     * PUT vs PATCH:
     *   - PUT: replace toàn bộ object — client gửi đủ tất cả field.
     *   - PATCH: partial update — client chỉ gửi field muốn đổi.
     *   → Dùng PUT cho đơn giản. Nếu category có nhiều field hơn, cân nhắc PATCH.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        CategoryResponse response = categoryService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", response));
    }

    /**
     * DELETE /api/categories/{id}
     * Xóa category → 200 OK với data = null.
     *
     * Tại sao không dùng 204 No Content?
     *   - 204 No Content: body rỗng, client không biết có message gì.
     *   - 200 OK + ApiResponse: client luôn nhận được body đồng nhất → dễ xử lý hơn.
     *   - Trade-off: 204 "REST pure" hơn, nhưng 200 + body thực tế dễ dùng hơn.
     *   → Project này ưu tiên DX (developer experience) → chọn 200.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted successfully", null));
    }
}
