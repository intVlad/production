package com.example.productionmvp.repository;

import com.example.productionmvp.model.OutsourceRecord;
import com.example.productionmvp.model.OutsourceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutsourceRecordRepository extends JpaRepository<OutsourceRecord, UUID> {
    List<OutsourceRecord> findByStatus(OutsourceStatus status);
    List<OutsourceRecord> findByStatusNot(OutsourceStatus status);
    
    @Query("SELECT o FROM OutsourceRecord o WHERE o.status != 'RECEIVED' AND o.status != 'CANCELLED' AND o.expectedReturnDate < CURRENT_TIMESTAMP")
    List<OutsourceRecord> findOverdueRecords();
    
    List<OutsourceRecord> findByPartner(String partner);
}
