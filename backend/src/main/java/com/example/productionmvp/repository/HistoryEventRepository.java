package com.example.productionmvp.repository;

import com.example.productionmvp.model.HistoryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface HistoryEventRepository extends JpaRepository<HistoryEvent, UUID> {
    List<HistoryEvent> findTop10ByOrderByTimestampDesc();
    
    @org.springframework.transaction.annotation.Transactional
    void deleteByTask(com.example.productionmvp.model.Task task);
}
