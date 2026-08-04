package com.example.productionmvp.repository;

import com.example.productionmvp.model.Stage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

import java.util.Optional;
import com.example.productionmvp.model.ProductModel;

public interface StageRepository extends JpaRepository<Stage, UUID> {
    Optional<Stage> findByProductModelAndOrderIndex(ProductModel productModel, Integer orderIndex);
    
    Optional<Stage> findFirstByProductModelAndOrderIndexGreaterThanOrderByOrderIndexAsc(ProductModel productModel, Integer orderIndex);
    
    Optional<Stage> findFirstByDependsOnStage(Stage dependsOnStage);
}
