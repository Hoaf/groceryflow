package com.groceryflow.productservice.repository;

import com.groceryflow.productservice.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {
    Optional<Product> findByBarcode(String barcode);
    List<Product> findByCategoryId(String categoryId);
    List<Product> findByIsActiveTrue();
}
