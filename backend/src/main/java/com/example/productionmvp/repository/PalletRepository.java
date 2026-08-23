package com.example.productionmvp.repository;

import com.example.productionmvp.model.Pallet;
import com.example.productionmvp.model.PalletStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PalletRepository extends JpaRepository<Pallet, UUID> {
    Optional<Pallet> findByQrCode(String qrCode);
    List<Pallet> findByOwnerProductId(UUID productId);
    List<Pallet> findByCurrentPostId(UUID postId);
    List<Pallet> findByStatus(PalletStatus status);
}
