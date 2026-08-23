package com.example.productionmvp.repository;

import com.example.productionmvp.model.HistoryEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface HistoryEventRepository extends JpaRepository<HistoryEvent, UUID> {
    @Query("SELECT h FROM HistoryEvent h LEFT JOIN FETCH h.productInstance LEFT JOIN FETCH h.stage LEFT JOIN FETCH h.worker ORDER BY h.timestamp DESC LIMIT 10")
    List<HistoryEvent> findTop10ByOrderByTimestampDesc();

    // LEFT JOIN, not implicit path navigation: a worker-less (system-generated) event must
    // still be returned when no workerId filter is applied - an inner join on a null relation
    // would silently drop it regardless of the filter, the same bug class already fixed on
    // TaskRepository.findByDashboardFilters.
    @Query("SELECT h FROM HistoryEvent h " +
           "LEFT JOIN FETCH h.worker w " +
           "LEFT JOIN FETCH h.task " +
           "LEFT JOIN FETCH h.productInstance " +
           "LEFT JOIN FETCH h.stage " +
           "LEFT JOIN FETCH h.operation " +
           "LEFT JOIN FETCH h.series " +
           "LEFT JOIN FETCH h.batch " +
           "WHERE (:workerId IS NULL OR w.id = :workerId) " +
           "AND (:since IS NULL OR h.timestamp >= :since) " +
           "AND (:until IS NULL OR h.timestamp <= :until) " +
           "ORDER BY h.timestamp DESC")
    List<HistoryEvent> findFiltered(@Param("workerId") UUID workerId,
                                     @Param("since") LocalDateTime since,
                                     @Param("until") LocalDateTime until,
                                     Pageable pageable);

    @org.springframework.transaction.annotation.Transactional
    void deleteByTask(com.example.productionmvp.model.Task task);
}
