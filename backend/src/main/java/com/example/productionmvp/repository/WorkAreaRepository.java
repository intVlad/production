package com.example.productionmvp.repository;

import com.example.productionmvp.model.WorkArea;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WorkAreaRepository extends JpaRepository<WorkArea, UUID> {
}
