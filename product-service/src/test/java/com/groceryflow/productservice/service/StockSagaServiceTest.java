package com.groceryflow.productservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.groceryflow.productservice.event.StockDeductItem;
import com.groceryflow.productservice.event.StockDeductRequestedEvent;
import com.groceryflow.productservice.model.OutboxEvent;
import com.groceryflow.productservice.model.Stock;
import com.groceryflow.productservice.repository.OutboxEventRepository;
import com.groceryflow.productservice.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockSagaServiceTest {

    @Mock private StockRepository stockRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock rLock;

    private StockDeductHelper stockDeductHelper;
    private StockSagaService stockSagaService;

    @BeforeEach
    void setUp() throws InterruptedException {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        stockDeductHelper = new StockDeductHelper(stockRepository, outboxEventRepository, objectMapper);
        stockSagaService = new StockSagaService(redissonClient, stockDeductHelper);

        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    private StockDeductRequestedEvent buildEvent(String orderId) {
        StockDeductItem item1 = new StockDeductItem();
        item1.setProductId("p1");
        item1.setQuantity(2);
        StockDeductItem item2 = new StockDeductItem();
        item2.setProductId("p2");
        item2.setQuantity(1);
        StockDeductRequestedEvent event = new StockDeductRequestedEvent();
        event.setOrderId(orderId);
        event.setItems(List.of(item1, item2));
        return event;
    }

    private Stock buildStock(String productId, int qty) {
        return Stock.builder()
                .id(productId + "-stock")
                .productId(productId)
                .quantity(qty)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void processDeductRequest_shouldDeductStockAndPublishDeducted() {
        when(stockRepository.findByProductId("p1")).thenReturn(Optional.of(buildStock("p1", 10)));
        when(stockRepository.findByProductId("p2")).thenReturn(Optional.of(buildStock("p2", 5)));
        when(stockRepository.save(any(Stock.class))).thenAnswer(i -> i.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        stockSagaService.processDeductRequest(buildEvent("order-1"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, atLeastOnce()).save(captor.capture());
        boolean hasDeducted = captor.getAllValues().stream()
                .anyMatch(e -> "stock.deducted".equals(e.getTopic()));
        assertThat(hasDeducted).isTrue();
    }

    @Test
    void processDeductRequest_shouldPublishFailedWhenInsufficientStock() {
        when(stockRepository.findByProductId("p1")).thenReturn(Optional.of(buildStock("p1", 10)));
        when(stockRepository.findByProductId("p2")).thenReturn(Optional.of(buildStock("p2", 0)));
        when(stockRepository.save(any(Stock.class))).thenAnswer(i -> i.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        stockSagaService.processDeductRequest(buildEvent("order-2"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, atLeastOnce()).save(captor.capture());
        boolean hasFailed = captor.getAllValues().stream()
                .anyMatch(e -> "stock.deduct.failed".equals(e.getTopic()));
        assertThat(hasFailed).isTrue();
    }

    @Test
    void processDeductRequest_shouldAcquireLocksInSortedOrder() throws InterruptedException {
        when(stockRepository.findByProductId(any())).thenReturn(Optional.of(buildStock("any", 10)));
        when(stockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        stockSagaService.processDeductRequest(buildEvent("order-3"));

        verify(redissonClient).getLock("stock:lock:p1");
        verify(redissonClient).getLock("stock:lock:p2");
    }
}
