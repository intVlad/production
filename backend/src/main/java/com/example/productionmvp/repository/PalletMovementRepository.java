package com.example.productionmvp.repository;

import com.example.productionmvp.model.PalletMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PalletMovementRepository extends JpaRepository<PalletMovement, UUID> {
    List<PalletMovement> findByPalletIdOrderByMovedAtDesc(UUID palletId);
}
