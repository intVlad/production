package com.example.productionmvp.repository;

import com.example.productionmvp.model.Worker;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WorkerRepository extends JpaRepository<Worker, UUID> {
}
