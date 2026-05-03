package com.groceryflow.productservice.service;

import com.groceryflow.productservice.dto.request.DeductStockRequest;
import com.groceryflow.productservice.dto.response.StockResponse;
import com.groceryflow.productservice.model.Stock;
import com.groceryflow.productservice.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StockRepository stockRepository;
    private final RedissonClient redissonClient;

    // Lock key prefix: "stock:lock:{productId}"
    // Namespace rõ ràng → tránh collision với các Redis key khác (product:cache:...)
    private static final String LOCK_PREFIX = "stock:lock:";

    // Nếu không acquire được lock sau 5 giây → thất bại nhanh (fail-fast)
    // Lý do: không nên để request treo quá lâu → UX kém, thread pool bị giữ
    private static final long LOCK_WAIT_SECONDS = 5;

    // Auto-release lock sau 10 giây dù process có crash/treo
    // Lý do: tránh deadlock khi service chết giữa chừng mà không unlock
    // Lưu ý: nếu dùng leaseTime = -1 → Redisson Watchdog tự gia hạn (không deadlock)
    //   nhưng cần explicit unlock → nguy hiểm hơn nếu developer quên
    //   → Chọn leaseTime = 10s: đơn giản, predictable, đủ cho deduct operation
    private static final long LOCK_LEASE_SECONDS = 10;

    /**
     * Query tồn kho hiện tại của sản phẩm.
     *
     * Không cần Distributed Lock ở đây vì:
     *   - Read-only operation → không thay đổi state
     *   - Eventual consistency cho read là chấp nhận được
     *   - Lock chỉ cần cho write operations để tránh race condition
     *
     * QUAN TRỌNG: Stock KHÔNG được cache (Redis cache chỉ dùng cho Product).
     *   Lý do: stock thay đổi liên tục khi bán hàng →
     *   cache sẽ stale ngay lập tức → misleading data.
     *   → Luôn đọc từ DB để đảm bảo accuracy.
     */
    @Transactional(readOnly = true)
    public StockResponse getStock(String productId) {
        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Stock not found for product: " + productId));
        return StockResponse.from(stock);
    }

    // ═══════════════════════════════════════════════════════════
    // Tại sao cần Distributed Lock?
    // Vấn đề: 2 nhân viên bán hàng cùng lúc, cùng sản phẩm, còn 1 cái
    //   Thread A: đọc stock=1, check OK (1>=1)
    //   Thread B: đọc stock=1, check OK (1>=1)  ← chưa thread A ghi
    //   Thread A: ghi stock=0
    //   Thread B: ghi stock=0  ← sold -1 !
    // → Race condition → oversell
    //
    // Cách giải quyết:
    // Cách 1: DB row lock (SELECT FOR UPDATE) → chỉ work 1 instance, không distributed
    //   Pros: không cần Redis, đơn giản.
    //   Cons: khi scale nhiều pod → vẫn đảm bảo vì DB lock, nhưng lock nằm ở DB
    //         → DB trở thành bottleneck, connection pool bị giữ lâu.
    // Cách 2: Optimistic locking (@Version) → retry loop, phức tạp
    //   Pros: không block thread, throughput cao hơn khi ít conflict.
    //   Cons: cần retry logic → phức tạp, nhiều conflict → retry nhiều → latency tăng.
    // Cách 3: Distributed Lock (Redis/Redisson) → lock across all instances
    //   Pros: distributed (nhiều pod), explicit control, easy timeout, không giữ DB connection.
    //   Cons: thêm dependency (Redis), network round-trip để acquire lock.
    // → Chọn Cách 3 vì: distributed (nhiều pod), explicit control, easy timeout
    //
    // Redisson RLock:
    //   - tryLock(waitTime, leaseTime): không block vô hạn
    //   - leaseTime: auto-release nếu process chết → không deadlock
    //   - Watchdog: tự gia hạn lock nếu còn xử lý (chỉ khi leaseTime = -1)
    // ═══════════════════════════════════════════════════════════

    /**
     * Trừ tồn kho với Distributed Lock để tránh race condition / oversell.
     *
     * Flow:
     *   1. Acquire Redis lock với key "stock:lock:{productId}"
     *   2. Đọc stock từ DB (LUÔN từ DB, không từ cache)
     *   3. Kiểm tra đủ hàng
     *   4. Trừ quantity, lưu DB
     *   5. Release lock (trong finally)
     *
     * @param productId  sản phẩm cần trừ kho
     * @param request    số lượng cần trừ + orderId để trace
     * @return StockResponse với quantity còn lại sau khi trừ
     * @throws IllegalArgumentException nếu không acquire được lock, stock không tồn tại,
     *                                  hoặc không đủ hàng
     *
     * LƯU Ý về @Transactional + Distributed Lock:
     *   Không đặt @Transactional ở đây vì:
     *   - Spring @Transactional proxy commit SAU khi method return
     *   - finally (unlock) chạy TRƯỚC khi transaction commit
     *   - → Thread B có thể acquire lock trước khi Thread A commit DB
     *   - → Thread B đọc được data cũ của Thread A (dirty read scenario)
     *
     *   Giải pháp đúng: lock bao ngoài transaction → lock outlives transaction
     *   Ở đây: stockRepository.save() dùng transaction riêng của Spring Data JPA
     *   (mỗi repository method có @Transactional mặc định) → đủ atomic cho 1 save.
     */
    public StockResponse deductStock(String productId, DeductStockRequest request) {
        String lockKey = LOCK_PREFIX + productId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            // tryLock: cố acquire trong LOCK_WAIT_SECONDS giây
            // Nếu sau 5s vẫn không acquire được → có thread khác đang giữ lock
            // → throw exception ngay (fail-fast) thay vì chờ vô hạn
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);

            if (!locked) {
                throw new IllegalArgumentException(
                        "Could not acquire stock lock for product: " + productId);
            }

            // a. Đọc stock từ DB — LUÔN từ DB, không từ cache
            //    Lý do: sau khi acquire lock, thread khác có thể đã thay đổi stock
            //    → phải đọc lại từ DB để có giá trị mới nhất
            Stock stock = stockRepository.findByProductId(productId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Stock not found for product: " + productId));

            // b. Kiểm tra đủ hàng
            if (stock.getQuantity() < request.getQuantity()) {
                throw new IllegalArgumentException(
                        "Insufficient stock for product: " + productId
                        + " (available: " + stock.getQuantity()
                        + ", requested: " + request.getQuantity() + ")");
            }

            // c. Trừ số lượng
            stock.setQuantity(stock.getQuantity() - request.getQuantity());

            // d. Lưu vào DB — @Transactional đảm bảo atomic với DB
            Stock saved = stockRepository.save(stock);

            // e. Log để trace: biết ai trừ bao nhiêu, còn bao nhiêu, cho đơn hàng nào
            log.info("Stock deducted: productId={}, deducted={}, remaining={}, orderId={}",
                    productId, request.getQuantity(), saved.getQuantity(), request.getOrderId());

            // f. Trả về stock sau khi trừ
            return StockResponse.from(saved);

        } catch (InterruptedException e) {
            // InterruptedException khi thread bị interrupt trong lúc tryLock
            // Restore interrupt flag → caller có thể xử lý
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException(
                    "Interrupted while acquiring stock lock for product: " + productId);
        } finally {
            // LUÔN release lock dù có exception hay không
            // Nếu không unlock → lock sẽ held cho đến khi hết leaseTime (10s)
            // → tránh deadlock nhờ leaseTime, nhưng tốt hơn là unlock ngay
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Cộng thêm tồn kho (dùng cho import-service ở Step 3.2).
     *
     * Tại sao addStock KHÔNG cần Distributed Lock?
     *   - Cộng kho thường xảy ra khi nhập hàng → ít concurrent hơn bán hàng.
     *   - Race condition ở đây: 2 phiếu nhập cùng lúc → over-add (thêm nhiều hơn thực tế).
     *     Nhưng over-add ít nguy hiểm hơn oversell (tệ nhất: kho dư, có thể điều chỉnh).
     *   - Nếu muốn strict: thêm lock giống deductStock.
     *   - Hiện tại: simple implementation đủ cho learning purpose.
     *
     * Trong production: nên có lock hoặc optimistic locking cho addStock cũng.
     *
     * @param productId sản phẩm cần cộng kho
     * @param quantity  số lượng cần cộng thêm
     * @return StockResponse với quantity mới
     */
    @Transactional
    public StockResponse addStock(String productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to add must be positive");
        }

        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Stock not found for product: " + productId));

        stock.setQuantity(stock.getQuantity() + quantity);
        Stock saved = stockRepository.save(stock);

        log.info("Stock added: productId={}, added={}, newTotal={}", productId, quantity, saved.getQuantity());

        return StockResponse.from(saved);
    }
}
