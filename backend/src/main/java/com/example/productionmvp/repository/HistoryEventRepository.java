package com.example.productionmvp.repository;

import com.example.productionmvp.model.HistoryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface HistoryEventRepository extends JpaRepository<HistoryEvent, UUID> {
    @Query("SELECT h FROM HistoryEvent h LEFT JOIN FETCH h.productInstance LEFT JOIN FETCH h.stage LEFT JOIN FETCH h.worker ORDER BY h.timestamp DESC LIMIT 10")
    List<HistoryEvent> findTop10ByOrderByTimestampDesc();
    
    @org.springframework.transaction.annotation.Transactional
    void deleteByTask(com.example.productionmvp.model.Task task);
}
