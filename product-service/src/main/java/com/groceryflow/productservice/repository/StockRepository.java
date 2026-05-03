package com.groceryflow.productservice.repository;

import com.groceryflow.productservice.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, String> {
    Optional<Stock> findByProductId(String productId);
}
