package com.example.productionmvp.repository;

import com.example.productionmvp.model.HistoryEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface HistoryEventRepository extends JpaRepository<HistoryEvent, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<HistoryEvent> {
    @Query("SELECT h FROM HistoryEvent h LEFT JOIN FETCH h.productInstance LEFT JOIN FETCH h.stage LEFT JOIN FETCH h.worker ORDER BY h.timestamp DESC LIMIT 10")
    List<HistoryEvent> findTop10ByOrderByTimestampDesc();

    // Was one JPQL string guarded with "(:param IS NULL OR ...)", which PostgreSQL rejects
    // outright when the argument is null - see HistoryEventSpecifications for the detail. The
    // filters are assembled as a specification instead, so an unused filter contributes no
    // parameter to the statement at all.
    default List<HistoryEvent> findFiltered(UUID workerId,
                                            LocalDateTime since,
                                            LocalDateTime until,
                                            Pageable pageable) {
        return findAll(HistoryEventSpecifications.filtered(workerId, since, until), pageable).getContent();
    }

    @org.springframework.transaction.annotation.Transactional
    void deleteByTask(com.example.productionmvp.model.Task task);
}
