package com.example.productionmvp.repository;

import com.example.productionmvp.model.Batch;
import com.example.productionmvp.model.BatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BatchRepository extends JpaRepository<Batch, UUID> {
    Optional<Batch> findByNumber(String number);
    List<Batch> findByStatus(BatchStatus status);
    List<Batch> findByWorkerId(UUID workerId);
    
    @Query("SELECT b FROM Batch b WHERE b.status != 'COMPLETED' AND b.status != 'CANCELLED'")
    List<Batch> findActiveBatches();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Batch b WHERE b.id = :id")
    Optional<Batch> findByIdLocked(@org.springframework.data.repository.query.Param("id") UUID id);
}
