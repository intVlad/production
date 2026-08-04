package com.example.productionmvp.repository;

import com.example.productionmvp.model.ProductModel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ProductModelRepository extends JpaRepository<ProductModel, UUID> {
}
