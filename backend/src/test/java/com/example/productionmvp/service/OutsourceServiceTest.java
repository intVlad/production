package com.example.productionmvp.service;

import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.model.AssemblyInstance;
import com.example.productionmvp.model.AssemblyInstanceStatus;
import com.example.productionmvp.model.OutsourceRecord;
import com.example.productionmvp.model.OutsourceStatus;
import com.example.productionmvp.model.Worker;
import com.example.productionmvp.repository.AssemblyInstanceRepository;
import com.example.productionmvp.repository.OutsourceRecordRepository;
import com.example.productionmvp.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutsourceServiceTest {

    @Mock
    private OutsourceRecordRepository outsourceRecordRepository;

    @Mock
    private AssemblyInstanceRepository assemblyInstanceRepository;

    @Mock
    private WorkerRepository workerRepository;

    @InjectMocks
    private OutsourceService outsourceService;

    private UUID recordId;
    private UUID assemblyInstanceId;
    private UUID workerId;

    @BeforeEach
    void setUp() {
        recordId = UUID.randomUUID();
        assemblyInstanceId = UUID.randomUUID();
        workerId = UUID.randomUUID();
    }

    @Test
    void createRecord_Success() {
        // Arrange
        String partner = "Partner A";
        String workType = "Painting";
        LocalDateTime expectedReturn = LocalDateTime.now().plusDays(5);
        List<UUID> assemblyInstanceIds = List.of(assemblyInstanceId);

        AssemblyInstance assemblyInstance = new AssemblyInstance();
        assemblyInstance.setId(assemblyInstanceId);

        when(assemblyInstanceRepository.findById(assemblyInstanceId)).thenReturn(Optional.of(assemblyInstance));
        when(outsourceRecordRepository.save(any(OutsourceRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        OutsourceRecord result = outsourceService.createRecord(partner, workType, assemblyInstanceIds, expectedReturn);

        // Assert
        assertNotNull(result);
        assertEquals(partner, result.getPartner());
        assertEquals(workType, result.getWorkType());
        assertEquals(expectedReturn, result.getExpectedReturnDate());
        assertEquals(OutsourceStatus.PLANNED, result.getStatus());
        assertNotNull(result.getCreatedAt());
        assertEquals(1, result.getItems().size());
        
        AssemblyInstance modifiedInstance = result.getItems().get(0);
        assertEquals(AssemblyInstanceStatus.IN_OUTSOURCE, modifiedInstance.getStatus());
        
        verify(assemblyInstanceRepository).findById(assemblyInstanceId);
        verify(outsourceRecordRepository).save(any(OutsourceRecord.class));
    }

    @Test
    void createRecord_AssemblyInstanceNotFound_ThrowsException() {
        // Arrange
        List<UUID> assemblyInstanceIds = List.of(assemblyInstanceId);
        when(assemblyInstanceRepository.findById(assemblyInstanceId)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> 
            outsourceService.createRecord("Partner A", "Painting", assemblyInstanceIds, LocalDateTime.now())
        );
        assertTrue(exception.getMessage().contains("AssemblyInstance not found"));
        
        verify(assemblyInstanceRepository).findById(assemblyInstanceId);
        verify(outsourceRecordRepository, never()).save(any());
    }

    @Test
    void sendOut_Success() {
        // Arrange
        OutsourceRecord record = new OutsourceRecord();
        record.setId(recordId);
        record.setStatus(OutsourceStatus.PLANNED);

        when(outsourceRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(outsourceRecordRepository.save(any(OutsourceRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        OutsourceRecord result = outsourceService.sendOut(recordId);

        // Assert
        assertNotNull(result);
        assertEquals(OutsourceStatus.IN_TRANSIT, result.getStatus());
        assertNotNull(result.getSentDate());
        
        verify(outsourceRecordRepository).findById(recordId);
        verify(outsourceRecordRepository).save(record);
    }

    @Test
    void sendOut_RecordNotFound_ThrowsException() {
        // Arrange
        when(outsourceRecordRepository.findById(recordId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> outsourceService.sendOut(recordId));
        
        verify(outsourceRecordRepository).findById(recordId);
        verify(outsourceRecordRepository, never()).save(any());
    }

    @Test
    void receiveBack_Success() {
        // Arrange
        AssemblyInstance instance = new AssemblyInstance();
        instance.setId(assemblyInstanceId);
        instance.setStatus(AssemblyInstanceStatus.IN_OUTSOURCE);

        OutsourceRecord record = new OutsourceRecord();
        record.setId(recordId);
        record.setStatus(OutsourceStatus.IN_TRANSIT);
        record.setItems(List.of(instance));

        Worker worker = new Worker();
        worker.setId(workerId);

        when(outsourceRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(outsourceRecordRepository.save(any(OutsourceRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        OutsourceRecord result = outsourceService.receiveBack(recordId, workerId);

        // Assert
        assertNotNull(result);
        assertEquals(OutsourceStatus.RECEIVED, result.getStatus());
        assertNotNull(result.getActualReturnDate());
        assertEquals(worker, result.getReceivedBy());
        assertEquals(AssemblyInstanceStatus.PLANNED, instance.getStatus());
        
        verify(outsourceRecordRepository).findById(recordId);
        verify(workerRepository).findById(workerId);
        verify(outsourceRecordRepository).save(record);
    }

    @Test
    void receiveBack_RecordNotFound_ThrowsException() {
        // Arrange
        when(outsourceRecordRepository.findById(recordId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> outsourceService.receiveBack(recordId, workerId));
        
        verify(outsourceRecordRepository).findById(recordId);
        verify(workerRepository, never()).findById(any());
        verify(outsourceRecordRepository, never()).save(any());
    }

    @Test
    void receiveBack_WorkerNotFound_ThrowsException() {
        // Arrange
        OutsourceRecord record = new OutsourceRecord();
        when(outsourceRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(workerRepository.findById(workerId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> outsourceService.receiveBack(recordId, workerId));
        
        verify(outsourceRecordRepository).findById(recordId);
        verify(workerRepository).findById(workerId);
        verify(outsourceRecordRepository, never()).save(any());
    }

    @Test
    void findOverdue_Success() {
        // Arrange
        OutsourceRecord overdue1 = new OutsourceRecord();
        overdue1.setStatus(OutsourceStatus.IN_TRANSIT);
        overdue1.setExpectedReturnDate(LocalDateTime.now().minusDays(1));

        OutsourceRecord notOverdue = new OutsourceRecord();
        notOverdue.setStatus(OutsourceStatus.PLANNED);
        notOverdue.setExpectedReturnDate(LocalDateTime.now().plusDays(1));

        OutsourceRecord received = new OutsourceRecord();
        received.setStatus(OutsourceStatus.RECEIVED);
        received.setExpectedReturnDate(LocalDateTime.now().minusDays(2)); // Already received, not overdue

        OutsourceRecord noExpectedDate = new OutsourceRecord();
        noExpectedDate.setStatus(OutsourceStatus.IN_TRANSIT); // Should be filtered out due to null date

        // findOverdueRecords() applies the status/date filtering at the query level.
        when(outsourceRecordRepository.findOverdueRecords()).thenReturn(List.of(overdue1));

        // Act
        List<OutsourceRecord> result = outsourceService.findOverdue();

        // Assert
        assertEquals(1, result.size());
        assertTrue(result.contains(overdue1));
    }

    @Test
    void findActiveRecords_Success() {
        // Arrange
        OutsourceRecord planned = new OutsourceRecord();
        planned.setStatus(OutsourceStatus.PLANNED);

        OutsourceRecord inTransit = new OutsourceRecord();
        inTransit.setStatus(OutsourceStatus.IN_TRANSIT);

        OutsourceRecord received = new OutsourceRecord();
        received.setStatus(OutsourceStatus.RECEIVED);

        when(outsourceRecordRepository.findByStatusNot(OutsourceStatus.RECEIVED))
                .thenReturn(Arrays.asList(planned, inTransit));

        // Act
        List<OutsourceRecord> result = outsourceService.findActiveRecords();

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.contains(planned));
        assertTrue(result.contains(inTransit));
        assertFalse(result.contains(received));
    }
}
