package com.example.productionmvp.repository;

import com.example.productionmvp.model.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MaterialRepository extends JpaRepository<Material, UUID> {
}
