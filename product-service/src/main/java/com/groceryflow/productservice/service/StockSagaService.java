package com.groceryflow.productservice.service;

import com.groceryflow.productservice.event.StockDeductRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockSagaService {

    private final RedissonClient redissonClient;
    private final StockDeductHelper stockDeductHelper;

    private static final long LOCK_WAIT_SECONDS = 5;
    private static final long LOCK_LEASE_SECONDS = 10;

    // processDeductRequest NEVER throws — all failures result in stock.deduct.failed event
    public void processDeductRequest(StockDeductRequestedEvent event) {
        // Sort productIds for consistent lock ordering → prevents deadlock
        List<String> sortedProductIds = event.getItems().stream()
                .map(item -> item.getProductId())
                .sorted()
                .toList();

        List<RLock> locks = sortedProductIds.stream()
                .map(id -> redissonClient.getLock("stock:lock:" + id))
                .toList();

        boolean allLocked = false;
        try {
            for (RLock lock : locks) {
                if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Could not acquire stock lock, orderId=" + event.getOrderId());
                }
            }
            allLocked = true;
            stockDeductHelper.deductAllInTransaction(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stockDeductHelper.saveDeductFailedEvent(event.getOrderId(), "Interrupted while acquiring lock");
        } catch (Exception e) {
            log.warn("Stock deduct failed: orderId={}, reason={}", event.getOrderId(), e.getMessage());
            stockDeductHelper.saveDeductFailedEvent(event.getOrderId(), e.getMessage());
        } finally {
            if (allLocked) {
                locks.forEach(lock -> {
                    if (lock.isHeldByCurrentThread()) lock.unlock();
                });
            }
        }
    }
}
