package com.groceryflow.productservice.service;

import com.groceryflow.productservice.dto.request.CreateCategoryRequest;
import com.groceryflow.productservice.dto.request.UpdateCategoryRequest;
import com.groceryflow.productservice.dto.response.CategoryResponse;
import com.groceryflow.productservice.model.Category;
import com.groceryflow.productservice.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// ═══════════════════════════════════════════════════════════
// CategoryService — business logic layer cho Category CRUD.
//
// Tại sao tách Service layer riêng khỏi Controller?
//   - Controller chỉ làm: parse HTTP request → gọi service → trả HTTP response.
//   - Service làm: validate business rule, gọi repository, xử lý exception.
//   - Lợi ích: Service có thể được test độc lập (unit test không cần HTTP).
//     Nhiều controller có thể dùng chung 1 service.
//
// @Transactional — quan trọng!
//   - Đảm bảo mỗi method chạy trong 1 DB transaction.
//   - Nếu có exception → rollback toàn bộ thay đổi → không bị data half-written.
//   - readOnly = true cho query → Spring tối ưu: không tạo write lock → nhanh hơn.
// ═══════════════════════════════════════════════════════════
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * Tạo Category mới.
     *
     * Business rule: tên category phải unique.
     * Tại sao check unique ở service thay vì chỉ dựa vào DB constraint?
     *   - DB constraint (UNIQUE) sẽ ném DataIntegrityViolationException — exception kỹ thuật,
     *     khó parse thành message thân thiện cho client.
     *   - Check trước ở service → ném IllegalArgumentException với message rõ ràng
     *     → GlobalExceptionHandler map thành 400 Bad Request dễ đọc.
     *   - Trade-off: có race condition nhỏ (2 request cùng lúc có thể pass check,
     *     nhưng DB constraint vẫn chặn được) → acceptable cho use case này.
     */
    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        log.info("Creating category with name: {}", request.getName());

        // Check duplicate name
        categoryRepository.findByName(request.getName()).ifPresent(existing -> {
            throw new IllegalArgumentException(
                    "Category with name '" + request.getName() + "' already exists");
        });

        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();

        Category saved = categoryRepository.save(category);
        log.info("Created category: id={}, name={}", saved.getId(), saved.getName());

        return CategoryResponse.from(saved);
    }

    /**
     * Lấy tất cả category.
     * readOnly = true → Spring không tạo write lock → query nhanh hơn.
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        log.debug("Fetching all categories");
        return categoryRepository.findAll()
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    /**
     * Lấy category theo ID.
     * Ném IllegalArgumentException nếu không tìm thấy → GlobalExceptionHandler → 400.
     *
     * Tại sao 400 (Bad Request) thay vì 404 (Not Found)?
     *   - Convention trong project: IllegalArgumentException → 400.
     *   - Một số team dùng NotFoundException riêng → GlobalExceptionHandler map → 404.
     *   - Cả hai đều valid. Quan trọng là nhất quán trong toàn project.
     */
    @Transactional(readOnly = true)
    public CategoryResponse findById(String id) {
        log.debug("Fetching category by id: {}", id);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
        return CategoryResponse.from(category);
    }

    /**
     * Cập nhật category.
     * Check duplicate name nhưng bỏ qua chính nó (exclude self).
     *
     * Tại sao phải "exclude self"?
     *   - Nếu user gửi PUT với name giống tên hiện tại → không phải duplicate.
     *   - findByName trả về chính entity đang update → sẽ báo lỗi sai.
     *   - Fix: chỉ báo lỗi nếu tìm thấy entity khác (id khác) có cùng name.
     */
    @Transactional
    public CategoryResponse update(String id, UpdateCategoryRequest request) {
        log.info("Updating category: id={}, newName={}", id, request.getName());

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));

        // Check duplicate name, excluding self
        categoryRepository.findByName(request.getName())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Category with name '" + request.getName() + "' already exists");
                });

        category.setName(request.getName());
        category.setDescription(request.getDescription());

        Category saved = categoryRepository.save(category);
        log.info("Updated category: id={}", saved.getId());

        return CategoryResponse.from(saved);
    }

    /**
     * Xóa category theo ID.
     *
     * Lưu ý production: nên check xem có Product nào dùng category này không
     * trước khi xóa. Nếu có → báo lỗi hoặc soft-delete (set deleted=true).
     * Hiện tại: hard delete đơn giản cho MVP.
     */
    @Transactional
    public void delete(String id) {
        log.info("Deleting category: id={}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));

        categoryRepository.delete(category);
        log.info("Deleted category: id={}", id);
    }
}
