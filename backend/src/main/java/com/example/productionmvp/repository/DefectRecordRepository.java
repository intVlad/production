package com.example.productionmvp.repository;

import com.example.productionmvp.model.DefectRecord;
import com.example.productionmvp.model.DefectResolution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DefectRecordRepository extends JpaRepository<DefectRecord, UUID> {
    List<DefectRecord> findByAssemblyInstanceId(UUID assemblyInstanceId);
    List<DefectRecord> findByResolution(DefectResolution resolution);
    
    @Query("SELECT d FROM DefectRecord d WHERE d.createdAt >= :since ORDER BY d.createdAt DESC")
    List<DefectRecord> findDefectsSince(@Param("since") LocalDateTime since);
    
    Long countByCreatedAtAfter(LocalDateTime since);
}
