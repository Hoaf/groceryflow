package com.groceryflow.productservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
//
// ═══════════════════════════════════════════════════════════
// ## Step 2.3: Cache-Aside Pattern cho Product Info
//
// Vấn đề: Mobile app nhân viên gọi GET /products/{id} mỗi lần bán hàng → DB bị hit nhiều.
//
// Cách 1: Cache-Through
//   - Cache đứng trước DB, mọi read/write đều qua cache.
//   - Pros: app code đơn giản (không cần biết cache).
//   - Cons: infrastructure phức tạp, ít framework support, khó debug.
//
// Cách 2: Write-Through
//   - Mọi write → update cache ngay, rồi write DB.
//   - Pros: cache luôn có data mới nhất sau write.
//   - Cons: write chậm hơn (2 operations: cache + DB), overhead kể cả khi data ít được đọc.
//
// Cách 3: Cache-Aside (Lazy Loading) — ĐÃ CHỌN
//   - App tự quản lý cache: read miss → load DB → populate cache → return.
//   - Write/Delete → app tự evict (xóa) cache entry cũ.
//   - Pros:
//     * App code kiểm soát hoàn toàn logic cache.
//     * Cache failure không crash app (graceful degradation — catch exception, vẫn dùng DB).
//     * Chỉ cache data thực sự được đọc (lazy) → tiết kiệm Redis memory.
//   - Cons:
//     * First request luôn là cache miss → load từ DB → hơi chậm hơn lần đầu.
//     * Có thể có khoảng window ngắn inconsistent sau update (evict → cache miss →
//       request khác load DB cũ) nhưng với TTL 5 phút → acceptable.
//
// → Chọn Cache-Aside vì: đơn giản, linh hoạt, phổ biến nhất trong microservices.
//   Phù hợp để HỌC vì logic rõ ràng, dễ trace.
//
// QUAN TRỌNG: Chỉ cache Product INFO, không cache Stock.
//   - Stock thay đổi liên tục (trừ kho, cộng kho) → stale cache → bán hàng sai số lượng.
//   - Product info (tên, giá, barcode) ít thay đổi → cache an toàn hơn.
// ═══════════════════════════════════════════════════════════
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StockRepository stockRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // Cache key pattern: "product:{id}" — dễ tìm kiếm, debug bằng redis-cli KEYS product:*
    private static final String CACHE_PREFIX = "product:";
    // TTL 5 phút: đủ để giảm DB load, đủ ngắn để stale data không ảnh hưởng nhiều.
    // Trade-off: TTL dài → ít DB hit hơn, nhưng update product cần chờ lâu hơn để thấy.
    private static final long CACHE_TTL_SECONDS = 300;

    // ─── Private cache helpers ───────────────────────────────

    /**
     * Tạo cache key từ productId.
     * Pattern: "product:abc-123-def"
     */
    private String cacheKey(String productId) {
        return CACHE_PREFIX + productId;
    }

    /**
     * Ghi ProductResponse vào Redis dưới dạng JSON string.
     *
     * Dùng try/catch để graceful degrade: nếu Redis down → log warn, không throw exception.
     * Tại sao quan trọng?
     *   - Cache là optimization, không phải requirement.
     *   - Nếu Redis fail → app vẫn chạy bình thường, chỉ chậm hơn (đọc DB trực tiếp).
     *   - Throw exception khi cache fail → break business flow → không acceptable.
     */
    private void cacheProduct(ProductResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey(response.getId()), json, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("Cached product: id={}, ttl={}s", response.getId(), CACHE_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("Failed to cache product {}: {}", response.getId(), e.getMessage());
        }
    }

    /**
     * Xóa cache entry của product khi data thay đổi (update hoặc delete).
     *
     * Evict strategy:
     *   - Sau update: evict → next read sẽ load DB mới → populate cache lại.
     *   - Không update cache trực tiếp sau write (vì có thể race condition trong
     *     distributed environment: 2 concurrent updates → cuối cùng cache có data cũ).
     *   - Pattern này gọi là "evict-on-write" — safe hơn "update-on-write".
     */
    private void evictCache(String productId) {
        redisTemplate.delete(cacheKey(productId));
        log.debug("Evicted cache for product: id={}", productId);
    }

    /**
     * Đọc ProductResponse từ Redis cache.
     *
     * Return Optional.empty() trong 2 trường hợp:
     *   1. Cache miss (key không tồn tại / đã hết TTL).
     *   2. Cache error (Redis down, JSON parse fail) → graceful degrade.
     */
    private Optional<ProductResponse> getFromCache(String productId) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey(productId));
            if (json != null) {
                log.debug("Cache HIT for product: id={}", productId);
                return Optional.of(objectMapper.readValue(json, ProductResponse.class));
            }
        } catch (Exception e) {
            log.warn("Cache read failed for product {}: {}", productId, e.getMessage());
        }
        log.debug("Cache MISS for product: id={}", productId);
        return Optional.empty();
    }

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
        String rawBarcode = request.getBarcode();
        final String barcode = (rawBarcode != null && !rawBarcode.isBlank()) ? rawBarcode : null;
        if (barcode != null) {
            productRepository.findByBarcode(barcode).ifPresent(existing -> {
                throw new IllegalArgumentException(
                        "Product with barcode '" + barcode + "' already exists");
            });
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
     * Lấy product theo ID — Cache-Aside pattern.
     *
     * Flow:
     *   1. Check Redis cache → cache HIT: trả về ngay (không cần DB).
     *   2. Cache MISS (hoặc Redis down): load từ DB.
     *   3. Sau khi load từ DB: populate cache để request tiếp theo hit cache.
     *   4. Return ProductResponse.
     *
     * Tại sao Cache-Aside phù hợp hơn @Cacheable ở đây?
     *   - @Cacheable (Spring Cache) cũng implement Cache-Aside, nhưng ẩn logic.
     *   - Viết explicit giúp hiểu rõ flow: check cache → DB → populate → return.
     *   - Dễ customize: log cache HIT/MISS, set TTL per-entry, handle error riêng.
     *
     * Tại sao dùng @Transactional(readOnly = true) dù đã có cache?
     *   - Khi cache miss → vẫn cần đọc DB → cần transaction.
     *   - readOnly=true: Spring tối ưu transaction (không lock, dùng read replica nếu có).
     */
    @Transactional(readOnly = true)
    public ProductResponse findById(String id) {
        log.debug("Fetching product by id: {}", id);

        // Step 1: Check Redis cache
        Optional<ProductResponse> cached = getFromCache(id);
        if (cached.isPresent()) {
            return cached.get();
        }

        // Step 2: Cache miss — load from DB
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));

        ProductResponse response = ProductResponse.from(product);

        // Step 3: Populate cache for next request
        cacheProduct(response);

        return response;
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
        String rawNewBarcode = request.getBarcode();
        final String newBarcode = (rawNewBarcode != null && !rawNewBarcode.isBlank()) ? rawNewBarcode : null;
        if (newBarcode != null) {
            productRepository.findByBarcode(newBarcode)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException(
                                "Product with barcode '" + newBarcode + "' already exists");
                    });
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

        // Step 7: Evict old cache → re-cache fresh data.
        // Tại sao evict trước rồi cache lại, không chỉ update cache trực tiếp?
        //   - Evict + re-cache: nếu cacheProduct() fail → cache miss next time → load DB → consistent.
        //   - Chỉ update cache: nếu fail halfway → stale data trong cache → inconsistent.
        //   - Với single-instance service, update trực tiếp cũng safe,
        //     nhưng evict-first là pattern an toàn hơn cho distributed.
        ProductResponse response = ProductResponse.from(saved);
        evictCache(id);
        cacheProduct(response);
        return response;
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

        // Evict cache sau soft delete: product đã inactive → không nên cache nữa.
        // Nếu client gọi findById() với id này → cache miss → load DB → thấy active=false
        // → GlobalExceptionHandler trả 404 (hoặc service có thể check active=false → throw).
        // Hiện tại findById() không check active → vẫn trả về product với active=false.
        // Việc evict cache đảm bảo client thấy trạng thái mới nhất sau next request.
        evictCache(id);

        log.info("Product soft-deleted: id={}", id);
    }
}
