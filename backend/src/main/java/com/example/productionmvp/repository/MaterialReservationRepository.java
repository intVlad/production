package com.example.productionmvp.repository;

import com.example.productionmvp.model.MaterialReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MaterialReservationRepository extends JpaRepository<MaterialReservation, UUID> {
    List<MaterialReservation> findByTaskId(UUID taskId);
    List<MaterialReservation> findByMaterialId(UUID materialId);
    
    @Query("SELECT SUM(mr.reservedQuantity) FROM MaterialReservation mr WHERE mr.material.id = :materialId")
    Double sumReservedByMaterialId(@Param("materialId") UUID materialId);
}
