package com.example.productionmvp.repository;

import com.example.productionmvp.model.Assembly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssemblyRepository extends JpaRepository<Assembly, UUID> {
    List<Assembly> findByProductModelId(UUID productModelId);
    Optional<Assembly> findByCode(String code);
    List<Assembly> findByCategory(String category);
}
