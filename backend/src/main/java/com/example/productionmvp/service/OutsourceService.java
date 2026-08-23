package com.example.productionmvp.service;

import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.model.AssemblyInstance;
import com.example.productionmvp.model.InstanceStatus;
import com.example.productionmvp.model.OutsourceRecord;
import com.example.productionmvp.model.OutsourceStatus;
import com.example.productionmvp.model.Worker;
import com.example.productionmvp.model.AssemblyInstanceStatus;
import com.example.productionmvp.repository.AssemblyInstanceRepository;
import com.example.productionmvp.repository.OutsourceRecordRepository;
import com.example.productionmvp.repository.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OutsourceService {

    private final OutsourceRecordRepository outsourceRecordRepository;
    private final AssemblyInstanceRepository assemblyInstanceRepository;
    private final WorkerRepository workerRepository;

    public OutsourceService(OutsourceRecordRepository outsourceRecordRepository,
                            AssemblyInstanceRepository assemblyInstanceRepository,
                            WorkerRepository workerRepository) {
        this.outsourceRecordRepository = outsourceRecordRepository;
        this.assemblyInstanceRepository = assemblyInstanceRepository;
        this.workerRepository = workerRepository;
    }

    @Transactional
    public OutsourceRecord createRecord(String partner, String workType, List<UUID> assemblyInstanceIds, LocalDateTime expectedReturn) {
        OutsourceRecord record = new OutsourceRecord();
        record.setPartner(partner);
        record.setWorkType(workType);
        record.setExpectedReturnDate(expectedReturn);
        record.setStatus(OutsourceStatus.PLANNED);
        record.setCreatedAt(LocalDateTime.now());
        
        List<AssemblyInstance> instances = new ArrayList<>();
        for (UUID id : assemblyInstanceIds) {
            AssemblyInstance instance = assemblyInstanceRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("AssemblyInstance not found: " + id));
            instance.setStatus(AssemblyInstanceStatus.IN_OUTSOURCE);
            instances.add(instance);
        }
        record.setItems(instances);
        
        return outsourceRecordRepository.save(record);
    }

    @Transactional
    public OutsourceRecord sendOut(UUID recordId) {
        OutsourceRecord record = outsourceRecordRepository.findById(recordId)
                .orElseThrow(() -> new EntityNotFoundException("OutsourceRecord not found: " + recordId));
        
        record.setStatus(OutsourceStatus.IN_TRANSIT);
        record.setSentDate(LocalDateTime.now());
        
        return outsourceRecordRepository.save(record);
    }

    @Transactional
    public OutsourceRecord receiveBack(UUID recordId, UUID receivedByWorkerId) {
        OutsourceRecord record = outsourceRecordRepository.findById(recordId)
                .orElseThrow(() -> new EntityNotFoundException("OutsourceRecord not found: " + recordId));
        if (receivedByWorkerId != null) {
            Worker worker = workerRepository.findById(receivedByWorkerId)
                    .orElseThrow(() -> new EntityNotFoundException("Worker not found: " + receivedByWorkerId));
            record.setReceivedBy(worker);
        }
        
        record.setStatus(OutsourceStatus.RECEIVED);
        record.setActualReturnDate(LocalDateTime.now());
        
        for (AssemblyInstance instance : record.getItems()) {
            instance.setStatus(AssemblyInstanceStatus.PLANNED);
        }
        
        return outsourceRecordRepository.save(record);
    }

    public List<OutsourceRecord> findOverdue() {
        return outsourceRecordRepository.findOverdueRecords();
    }

    public List<OutsourceRecord> findActiveRecords() {
        return outsourceRecordRepository.findByStatusNot(OutsourceStatus.RECEIVED);
    }
}
