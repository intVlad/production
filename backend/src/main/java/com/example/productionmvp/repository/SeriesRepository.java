package com.example.productionmvp.repository;

import com.example.productionmvp.model.Series;
import com.example.productionmvp.model.SeriesStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeriesRepository extends JpaRepository<Series, UUID> {
    Optional<Series> findByNumber(String number);
    List<Series> findByStatus(SeriesStatus status);
    List<Series> findByProductModelId(UUID productModelId);
    
    @Query("SELECT s FROM Series s WHERE s.status != com.example.productionmvp.model.SeriesStatus.COMPLETED AND s.status != com.example.productionmvp.model.SeriesStatus.CANCELLED ORDER BY s.priority ASC, s.plannedEndDate ASC")
    List<Series> findActiveSeries();
}
