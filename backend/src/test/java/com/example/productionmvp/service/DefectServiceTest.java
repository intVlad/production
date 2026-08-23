package com.example.productionmvp.service;

import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.model.AssemblyInstance;
import com.example.productionmvp.model.AssemblyInstanceStatus;
import com.example.productionmvp.model.Batch;
import com.example.productionmvp.model.BatchStatus;
import com.example.productionmvp.model.DefectRecord;
import com.example.productionmvp.model.DefectResolution;
import com.example.productionmvp.model.Task;
import com.example.productionmvp.model.TaskStatus;
import com.example.productionmvp.model.OperationType;
import com.example.productionmvp.model.Worker;
import com.example.productionmvp.model.Operation;
import com.example.productionmvp.model.Series;
import com.example.productionmvp.repository.AssemblyInstanceRepository;
import com.example.productionmvp.repository.BatchRepository;
import com.example.productionmvp.repository.DefectRecordRepository;
import com.example.productionmvp.repository.TaskRepository;
import com.example.productionmvp.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefectServiceTest {

    @Mock
    private DefectRecordRepository defectRecordRepository;

    @Mock
    private AssemblyInstanceRepository assemblyInstanceRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private BatchRepository batchRepository;

    @InjectMocks
    private DefectService defectService;

    @Captor
    private ArgumentCaptor<DefectRecord> defectRecordCaptor;

    @Captor
    private ArgumentCaptor<Task> taskCaptor;

    @Captor
    private ArgumentCaptor<Batch> batchCaptor;

    @Captor
    private ArgumentCaptor<AssemblyInstance> assemblyInstanceCaptor;

    private UUID assemblyInstanceId;
    private UUID taskId;
    private UUID workerId;

    private AssemblyInstance assemblyInstance;
    private Task task;
    private Worker worker;
    private Operation operation;
    private Series series;

    @BeforeEach
    void setUp() {
        assemblyInstanceId = UUID.randomUUID();
        taskId = UUID.randomUUID();
        workerId = UUID.randomUUID();

        assemblyInstance = new AssemblyInstance();
        assemblyInstance.setId(assemblyInstanceId);
        assemblyInstance.setStatus(AssemblyInstanceStatus.IN_PROGRESS);

        operation = new Operation();
        series = new Series();

        task = new Task();
        task.setId(taskId);
        task.setOperation(operation);
        task.setSeries(series);

        worker = new Worker();
        worker.setId(workerId);
    }

    @Test
    void reportDefect_WhenResolutionIsRework_CreatesReworkTaskAndSavesDefect() {
        // Arrange
        when(assemblyInstanceRepository.findById(assemblyInstanceId)).thenReturn(Optional.of(assemblyInstance));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        
        DefectRecord savedDefect = new DefectRecord();
        savedDefect.setId(UUID.randomUUID());
        when(defectRecordRepository.save(any(DefectRecord.class))).thenReturn(savedDefect);

        // Act
        DefectRecord result = defectService.reportDefect(assemblyInstanceId, taskId, "Scratch", workerId, DefectResolution.REWORK);

        // Assert
        assertNotNull(result);
        
        verify(assemblyInstanceRepository).save(assemblyInstanceCaptor.capture());
        assertEquals(AssemblyInstanceStatus.DAMAGED, assemblyInstanceCaptor.getValue().getStatus());

        verify(taskRepository).save(taskCaptor.capture());
        Task reworkTask = taskCaptor.getValue();
        assertEquals(assemblyInstance, reworkTask.getAssemblyInstance());
        assertEquals(operation, reworkTask.getOperation());
        assertEquals(series, reworkTask.getSeries());
        assertEquals(TaskStatus.READY, reworkTask.getStatus());
        assertEquals(OperationType.INDIVIDUAL, reworkTask.getTaskType());
        assertNotNull(reworkTask.getCreatedAt());

        verify(defectRecordRepository).save(defectRecordCaptor.capture());
        DefectRecord defectRecord = defectRecordCaptor.getValue();
        assertEquals(assemblyInstance, defectRecord.getAssemblyInstance());
        assertEquals(task, defectRecord.getTask());
        assertEquals("Scratch", defectRecord.getReason());
        assertEquals(worker, defectRecord.getConfirmedBy());
        assertEquals(DefectResolution.REWORK, defectRecord.getResolution());
        assertNotNull(defectRecord.getCreatedAt());
        assertNull(defectRecord.getReplacementBatch());
    }

    @Test
    void reportDefect_WhenResolutionIsReplace_CreatesReplaceTaskAndBatchAndSavesDefect() {
        // Arrange
        when(assemblyInstanceRepository.findById(assemblyInstanceId)).thenReturn(Optional.of(assemblyInstance));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        
        DefectRecord savedDefect = new DefectRecord();
        savedDefect.setId(UUID.randomUUID());
        when(defectRecordRepository.save(any(DefectRecord.class))).thenReturn(savedDefect);

        // Act
        DefectRecord result = defectService.reportDefect(assemblyInstanceId, taskId, "Broken part", workerId, DefectResolution.REPLACE);

        // Assert
        assertNotNull(result);

        verify(assemblyInstanceRepository).save(assemblyInstanceCaptor.capture());
        assertEquals(AssemblyInstanceStatus.DAMAGED, assemblyInstanceCaptor.getValue().getStatus());

        verify(batchRepository).save(batchCaptor.capture());
        Batch replacementBatch = batchCaptor.getValue();
        assertEquals(1, replacementBatch.getPlannedQuantity());
        assertEquals(BatchStatus.CREATED, replacementBatch.getStatus());

        verify(taskRepository).save(taskCaptor.capture());
        Task replaceTask = taskCaptor.getValue();
        assertEquals(replacementBatch, replaceTask.getBatch());
        assertEquals(operation, replaceTask.getOperation());
        assertEquals(series, replaceTask.getSeries());
        assertEquals(TaskStatus.READY, replaceTask.getStatus());
        assertEquals(OperationType.BATCH, replaceTask.getTaskType());
        assertNotNull(replaceTask.getCreatedAt());

        verify(defectRecordRepository).save(defectRecordCaptor.capture());
        DefectRecord defectRecord = defectRecordCaptor.getValue();
        assertEquals(assemblyInstance, defectRecord.getAssemblyInstance());
        assertEquals(task, defectRecord.getTask());
        assertEquals("Broken part", defectRecord.getReason());
        assertEquals(worker, defectRecord.getConfirmedBy());
        assertEquals(DefectResolution.REPLACE, defectRecord.getResolution());
        assertEquals(replacementBatch, defectRecord.getReplacementBatch());
    }

    @Test
    void reportDefect_WhenResolutionIsWriteOff_RegeneratesBatchForWholeAssembly() {
        // Arrange
        com.example.productionmvp.model.Operation firstOp = new com.example.productionmvp.model.Operation();
        firstOp.setId(UUID.randomUUID());
        com.example.productionmvp.model.Operation secondOp = new com.example.productionmvp.model.Operation();
        secondOp.setId(UUID.randomUUID());
        secondOp.setDependsOnOperation(firstOp);

        com.example.productionmvp.model.Assembly assembly = new com.example.productionmvp.model.Assembly();
        assembly.setOperations(List.of(secondOp, firstOp));
        assemblyInstance.setAssembly(assembly);
        assemblyInstance.setCurrentOperationIndex(3);

        when(assemblyInstanceRepository.findById(assemblyInstanceId)).thenReturn(Optional.of(assemblyInstance));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));

        DefectRecord savedDefect = new DefectRecord();
        savedDefect.setId(UUID.randomUUID());
        when(defectRecordRepository.save(any(DefectRecord.class))).thenReturn(savedDefect);

        // Act
        DefectRecord result = defectService.reportDefect(assemblyInstanceId, taskId, "Bent frame", workerId, DefectResolution.WRITE_OFF);

        // Assert
        assertNotNull(result);

        verify(assemblyInstanceRepository, times(2)).save(assemblyInstanceCaptor.capture());
        AssemblyInstance savedInstance = assemblyInstanceCaptor.getValue();
        assertEquals(0, savedInstance.getCurrentOperationIndex());

        verify(batchRepository).save(batchCaptor.capture());
        Batch rebuildBatch = batchCaptor.getValue();
        assertEquals(1, rebuildBatch.getPlannedQuantity());
        assertEquals(firstOp, rebuildBatch.getOperation());

        verify(taskRepository).save(taskCaptor.capture());
        Task rebuildTask = taskCaptor.getValue();
        assertEquals(assemblyInstance, rebuildTask.getAssemblyInstance());
        assertEquals(firstOp, rebuildTask.getOperation());
        assertEquals(rebuildBatch, rebuildTask.getBatch());
        assertEquals(TaskStatus.READY, rebuildTask.getStatus());
        assertEquals(OperationType.BATCH, rebuildTask.getTaskType());

        verify(defectRecordRepository).save(defectRecordCaptor.capture());
        assertEquals(DefectResolution.WRITE_OFF, defectRecordCaptor.getValue().getResolution());
        assertEquals(rebuildBatch, defectRecordCaptor.getValue().getReplacementBatch());
    }

    @Test
    void reportDefect_WhenAssemblyInstanceNotFound_ThrowsException() {
        // Arrange
        when(assemblyInstanceRepository.findById(assemblyInstanceId)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, 
            () -> defectService.reportDefect(assemblyInstanceId, taskId, "Reason", workerId, DefectResolution.REWORK));
        
        assertEquals("AssemblyInstance not found: " + assemblyInstanceId, exception.getMessage());
        verify(assemblyInstanceRepository, never()).save(any());
        verify(taskRepository, never()).save(any());
        verify(defectRecordRepository, never()).save(any());
    }

    @Test
    void reportDefect_WhenTaskNotFound_ThrowsException() {
        // Arrange
        when(assemblyInstanceRepository.findById(assemblyInstanceId)).thenReturn(Optional.of(assemblyInstance));
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, 
            () -> defectService.reportDefect(assemblyInstanceId, taskId, "Reason", workerId, DefectResolution.REWORK));
        
        assertEquals("Task not found: " + taskId, exception.getMessage());
        verify(assemblyInstanceRepository, never()).save(any());
        verify(taskRepository, never()).save(any());
        verify(defectRecordRepository, never()).save(any());
    }

    @Test
    void reportDefect_WhenWorkerNotFound_ThrowsException() {
        // Arrange
        when(assemblyInstanceRepository.findById(assemblyInstanceId)).thenReturn(Optional.of(assemblyInstance));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(workerRepository.findById(workerId)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, 
            () -> defectService.reportDefect(assemblyInstanceId, taskId, "Reason", workerId, DefectResolution.REWORK));
        
        assertEquals("Worker not found: " + workerId, exception.getMessage());
        verify(assemblyInstanceRepository, never()).save(any());
        verify(taskRepository, never()).save(any());
        verify(defectRecordRepository, never()).save(any());
    }

    @Test
    void findDefectsSince_ReturnsFilteredList() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime filterTime = now.minusDays(2);
        
        DefectRecord oldDefect = new DefectRecord();
        oldDefect.setCreatedAt(now.minusDays(5));
        
        DefectRecord newDefect1 = new DefectRecord();
        newDefect1.setCreatedAt(now.minusDays(1));
        
        DefectRecord newDefect2 = new DefectRecord();
        newDefect2.setCreatedAt(now);
        
        when(defectRecordRepository.findAll()).thenReturn(List.of(oldDefect, newDefect1, newDefect2));

        // Act
        List<DefectRecord> result = defectService.findDefectsSince(filterTime);

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.contains(newDefect1));
        assertTrue(result.contains(newDefect2));
        assertFalse(result.contains(oldDefect));
    }

    @Test
    void getDefectStats_ReturnsCorrectGroupedCounts() {
        // Arrange
        DefectRecord reworkDefect1 = new DefectRecord();
        reworkDefect1.setResolution(DefectResolution.REWORK);
        
        DefectRecord reworkDefect2 = new DefectRecord();
        reworkDefect2.setResolution(DefectResolution.REWORK);
        
        DefectRecord replaceDefect = new DefectRecord();
        replaceDefect.setResolution(DefectResolution.REPLACE);
        
        when(defectRecordRepository.findAll()).thenReturn(List.of(reworkDefect1, reworkDefect2, replaceDefect));

        // Act
        Map<String, Long> stats = defectService.getDefectStats();

        // Assert
        assertEquals(2, stats.size());
        assertEquals(2L, stats.get(DefectResolution.REWORK.name()));
        assertEquals(1L, stats.get(DefectResolution.REPLACE.name()));
    }

    // A defect can be recorded before anyone decides what to do with the part, and grouping by
    // a null resolution used to throw - one such row took the whole statistics endpoint down
    // for everyone until it was edited.
    @Test
    void getDefectStats_CountsUnresolvedInsteadOfThrowing() {
        DefectRecord resolved = new DefectRecord();
        resolved.setResolution(DefectResolution.REWORK);
        DefectRecord undecided = new DefectRecord();
        undecided.setResolution(null);

        when(defectRecordRepository.findAll()).thenReturn(List.of(resolved, undecided));

        Map<String, Long> stats = defectService.getDefectStats();

        assertEquals(1L, stats.get(DefectResolution.REWORK.name()));
        assertEquals(1L, stats.get(DefectService.UNRESOLVED_KEY));
        // Every record is accounted for, so the figures still add up to what exists.
        assertEquals(2L, stats.values().stream().mapToLong(Long::longValue).sum());
    }
}
