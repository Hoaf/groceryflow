package com.groceryflow.productservice.service;

import com.groceryflow.productservice.dto.request.CreateProductRequest;
import com.groceryflow.productservice.dto.request.UpdateProductRequest;
import com.groceryflow.productservice.dto.response.ProductResponse;
import com.groceryflow.productservice.model.Product;
import com.groceryflow.productservice.model.Stock;
import com.groceryflow.productservice.repository.CategoryRepository;
import com.groceryflow.productservice.repository.ProductRepository;
import com.groceryflow.productservice.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// ═══════════════════════════════════════════════════════════
// ProductService — business logic layer cho Product CRUD.
//
// Các business rule quan trọng:
//   1. categoryId phải tồn tại trước khi lưu product.
//   2. barcode phải unique nếu được cung cấp (optional).
//   3. Khi tạo product mới → tự động tạo Stock với quantity=0.
//   4. Xóa product = soft delete (chỉ set active=false, không xóa DB row).
//
// Tại sao auto-create Stock khi tạo Product?
//   - Business rule: mỗi product luôn có 1 stock record (quan hệ 1-1).
//   - Nếu để caller tự tạo stock → dễ quên → product không có stock → lỗi runtime.
//   - Service encapsulate rule này → caller không cần biết chi tiết.
//   - Pattern này gọi là "Aggregate" trong Domain-Driven Design:
//     Product + Stock là 1 aggregate → create cùng nhau.
//
// @Transactional — bắt buộc cho mọi method write:
//   - create(): save product + save stock trong 1 transaction.
//     Nếu save stock lỗi → rollback save product → không có product thiếu stock.
//   - Đây là ACID trong action: Atomicity đảm bảo 2 INSERT hoặc không cái nào.
// ═══════════════════════════════════════════════════════════
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StockRepository stockRepository;

    /**
     * Tạo Product mới.
     *
     * Flow:
     *   1. Validate categoryId tồn tại.
     *   2. Nếu barcode non-null/non-blank: check duplicate.
     *   3. Save Product.
     *   4. Auto-create Stock với quantity=0 (cùng transaction).
     *   5. Return ProductResponse.
     *
     * Tại sao validate categoryId ở service thay vì chỉ dựa FK constraint?
     *   - FK constraint (nếu có) ném DataIntegrityViolationException → khó parse.
     *   - Validate trước → ném IllegalArgumentException → GlobalExceptionHandler → 400
     *     với message rõ ràng cho client.
     */
    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        log.info("Creating product: name={}, barcode={}, categoryId={}",
                request.getName(), request.getBarcode(), request.getCategoryId());

        // Step 1: Validate categoryId exists
        categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Category not found: " + request.getCategoryId()));

        // Step 2: Check duplicate barcode (only if barcode provided)
        // Tại sao check "non-null AND non-blank"?
        //   - null: sản phẩm không có mã vạch → skip check.
        //   - blank ("" hoặc "   "): client gửi rỗng → treat as null → skip check.
        //   - Nếu không làm vậy: 2 sản phẩm không có barcode sẽ xung đột nhau.
        String barcode = request.getBarcode();
        if (barcode != null && !barcode.isBlank()) {
            productRepository.findByBarcode(barcode).ifPresent(existing -> {
                throw new IllegalArgumentException(
                        "Product with barcode '" + barcode + "' already exists");
            });
        } else {
            // Normalize: blank string → null để tránh lưu "" vào DB
            barcode = null;
        }

        // Step 3: Build và save Product
        Product product = Product.builder()
                .name(request.getName())
                .barcode(barcode)
                .price(request.getPrice())
                .unit(request.getUnit())
                .categoryId(request.getCategoryId())
                .active(true)
                .build();

        Product saved = productRepository.save(product);
        log.info("Product saved: id={}", saved.getId());

        // Step 4: Auto-create Stock với quantity=0
        // Cùng transaction → nếu save stock lỗi → rollback cả product.
        Stock stock = Stock.builder()
                .productId(saved.getId())
                .quantity(0)
                .build();
        stockRepository.save(stock);
        log.info("Stock initialized for product: productId={}, quantity=0", saved.getId());

        return ProductResponse.from(saved);
    }

    /**
     * Lấy tất cả sản phẩm đang active (soft delete ẩn inactive).
     *
     * Tại sao chỉ trả active=true?
     *   - Sản phẩm bị xóa (active=false) không nên hiển thị trong danh sách.
     *   - Nhân viên scan barcode → chỉ thấy sản phẩm đang bán.
     *   - Nếu cần xem cả inactive (cho admin) → thêm endpoint riêng hoặc query param.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        log.debug("Fetching all active products");
        return productRepository.findByActiveTrue()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    /**
     * Lấy product theo ID.
     * Ném IllegalArgumentException nếu không tìm thấy.
     */
    @Transactional(readOnly = true)
    public ProductResponse findById(String id) {
        log.debug("Fetching product by id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        return ProductResponse.from(product);
    }

    /**
     * Tìm sản phẩm theo barcode — dùng cho mobile app scan barcode.
     *
     * Đây là feature quan trọng nhất cho nhân viên:
     *   - Scan barcode sản phẩm → gọi GET /api/products/barcode/{barcode}
     *   - Nhận thông tin sản phẩm (tên, giá, đơn vị) → thêm vào giỏ hàng.
     *   - Phải nhanh → trong Step 2.3 sẽ add Redis cache cho endpoint này.
     */
    @Transactional(readOnly = true)
    public ProductResponse findByBarcode(String barcode) {
        log.debug("Fetching product by barcode: {}", barcode);
        Product product = productRepository.findByBarcode(barcode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Product not found for barcode: " + barcode));
        return ProductResponse.from(product);
    }

    /**
     * Lấy danh sách sản phẩm theo category — chỉ trả active product.
     *
     * Tại sao filter active ở Java thay vì query DB?
     *   - Repository findByCategoryId() không filter active → trả cả inactive.
     *   - Có thể thêm findByCategoryIdAndActiveTrue() vào repo → query DB filter.
     *   - Hiện tại: filter ở Java stream → đơn giản hơn, đủ dùng cho MVP.
     *   - Với data lớn hơn: nên filter ở DB level để tránh load data thừa.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> findByCategory(String categoryId) {
        log.debug("Fetching products by categoryId: {}", categoryId);
        return productRepository.findByCategoryId(categoryId)
                .stream()
                .filter(Product::isActive)
                .map(ProductResponse::from)
                .toList();
    }

    /**
     * Cập nhật Product.
     *
     * Flow:
     *   1. Find product → throw nếu không tìm thấy.
     *   2. Validate categoryId mới tồn tại.
     *   3. Nếu barcode thay đổi và non-blank: check duplicate (exclude self).
     *   4. Update fields: name, barcode, price, unit, categoryId.
     *   5. Nếu request.active != null: update trạng thái active.
     *   6. Save và return.
     *
     * "Exclude self" trong check barcode duplicate:
     *   - findByBarcode() có thể trả về chính product đang update (nếu barcode không đổi).
     *   - Nếu không exclude self → báo "barcode đã tồn tại" sai.
     *   - Fix: chỉ throw nếu tìm thấy product khác (id khác) có cùng barcode.
     */
    @Transactional
    public ProductResponse update(String id, UpdateProductRequest request) {
        log.info("Updating product: id={}, name={}", id, request.getName());

        // Step 1: Find product
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));

        // Step 2: Validate categoryId exists
        categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Category not found: " + request.getCategoryId()));

        // Step 3: Check barcode duplicate (exclude self)
        String newBarcode = request.getBarcode();
        if (newBarcode != null && !newBarcode.isBlank()) {
            productRepository.findByBarcode(newBarcode)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException(
                                "Product with barcode '" + newBarcode + "' already exists");
                    });
        } else {
            // Normalize blank → null
            newBarcode = null;
        }

        // Step 4: Update fields
        product.setName(request.getName());
        product.setBarcode(newBarcode);
        product.setPrice(request.getPrice());
        product.setUnit(request.getUnit());
        product.setCategoryId(request.getCategoryId());

        // Step 5: Update active nếu client truyền vào
        // null → không thay đổi (giữ nguyên giá trị cũ)
        if (request.getActive() != null) {
            product.setActive(request.getActive());
            log.info("Product active status updated: id={}, active={}", id, request.getActive());
        }

        // Step 6: Save
        Product saved = productRepository.save(product);
        log.info("Product updated: id={}", saved.getId());

        return ProductResponse.from(saved);
    }

    /**
     * Soft delete Product — chỉ set active=false, không xóa DB row.
     *
     * Tại sao soft delete?
     *   - Order history tham chiếu đến product (order_items.product_id).
     *   - Nếu hard delete → FK violation (nếu có FK) hoặc orphan data.
     *   - Lịch sử bán hàng vẫn hiển thị tên sản phẩm cũ → truy xuất sau.
     *   - Có thể restore sản phẩm nếu xóa nhầm (set active=true lại).
     *
     * Alternatives:
     *   Cách 1: Hard delete → đơn giản hơn, nhưng mất lịch sử.
     *   Cách 2: Soft delete (đây) → giữ lịch sử, restore được.
     *   Cách 3: Archive to another table → phức tạp hơn, hiếm dùng ở scale này.
     *   → Chọn Cách 2 vì: tiệm tạp hóa cần xem lại lịch sử bán hàng.
     */
    @Transactional
    public void delete(String id) {
        log.info("Soft-deleting product: id={}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));

        product.setActive(false);
        productRepository.save(product);

        log.info("Product soft-deleted: id={}", id);
    }
}
