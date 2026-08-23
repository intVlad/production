package com.example.productionmvp.repository;

import com.example.productionmvp.model.ProductInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface ProductInstanceRepository extends JpaRepository<ProductInstance, UUID> {
    List<ProductInstance> findByStatusNot(com.example.productionmvp.model.InstanceStatus status);
    List<ProductInstance> findBySeriesId(UUID seriesId);
    long countByStatus(com.example.productionmvp.model.InstanceStatus status);
}
