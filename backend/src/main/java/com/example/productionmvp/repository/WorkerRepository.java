package com.example.productionmvp.repository;

import com.example.productionmvp.model.Worker;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, UUID> {
    Optional<Worker> findByQrBadgeCode(String qrBadgeCode);
}
