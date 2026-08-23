package com.example.productionmvp.service;

import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.model.*;
import com.example.productionmvp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchServiceTest {

    @Mock private BatchRepository batchRepository;
    @Mock private OperationRepository operationRepository;
    @Mock private WorkerRepository workerRepository;
    @Mock private SectionRepository sectionRepository;
    @Mock private PostRepository postRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private TimeLogRepository timeLogRepository;
    @Mock private PalletRepository palletRepository;
    @Mock private AssemblyInstanceRepository assemblyInstanceRepository;
    @Mock private MaterialService materialService;
    @Mock private SseService sseService;

    @InjectMocks
    private BatchService batchService;

    private UUID batchId;
    private UUID operationId;
    private UUID workerId;
    private UUID sectionId;
    private UUID postId;
    private UUID palletId;

    private Batch batch;
    private Operation operation;
    private Worker worker;
    private Section section;
    private Post post;
    private Pallet pallet;

    @BeforeEach
    void setUp() {
        batchId = UUID.randomUUID();
        operationId = UUID.randomUUID();
        workerId = UUID.randomUUID();
        sectionId = UUID.randomUUID();
        postId = UUID.randomUUID();
        palletId = UUID.randomUUID();

        operation = new Operation();
        operation.setId(operationId);
        operation.setName("Cutting");

        worker = new Worker();
        worker.setId(workerId);

        section = new Section();
        section.setId(sectionId);

        post = new Post();
        post.setId(postId);
        post.setOperationTypes("Cutting,Welding");

        batch = new Batch();
        batch.setId(batchId);
        batch.setOperation(operation);
        // Default integer to avoid NPE if it's unboxed in completeBatch
        batch.setDistributedQuantity(0);

        pallet = new Pallet();
        pallet.setId(palletId);
    }

    // ==========================================
    // createBatch tests
    // ==========================================

    @Test
    void createBatch_Success_WithAllRelations() {
        when(operationRepository.findById(operationId)).thenReturn(Optional.of(operation));
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(sectionRepository.findById(sectionId)).thenReturn(Optional.of(section));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        Batch result = batchService.createBatch("B-001", operationId, workerId, sectionId, postId, "Metal Sheet", 100);

        assertNotNull(result);
        assertEquals("B-001", result.getNumber());
        assertEquals(operation, result.getOperation());
        assertEquals(worker, result.getWorker());
        assertEquals(section, result.getSection());
        assertEquals(post, result.getPost());
        assertEquals("Metal Sheet", result.getMaterialDetail());
        assertEquals(100, result.getPlannedQuantity());
        assertEquals(BatchStatus.CREATED, result.getStatus());

        verify(batchRepository).save(any(Batch.class));
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void createBatch_Success_WithNullRelations() {
        when(operationRepository.findById(operationId)).thenReturn(Optional.of(operation));

        Batch result = batchService.createBatch("B-002", operationId, null, null, null, "Wood", 50);

        assertNotNull(result);
        assertNull(result.getWorker());
        assertNull(result.getSection());
        assertNull(result.getPost());

        verify(batchRepository).save(any(Batch.class));
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void createBatch_ThrowsException_WhenOperationNotFound() {
        when(operationRepository.findById(operationId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> 
            batchService.createBatch("B-001", operationId, workerId, sectionId, postId, "Metal", 100));
        
        verify(batchRepository, never()).save(any());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void createBatch_ThrowsException_WhenPostDoesNotSupportOperation() {
        operation.setName("Painting");
        when(operationRepository.findById(operationId)).thenReturn(Optional.of(operation));
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(sectionRepository.findById(sectionId)).thenReturn(Optional.of(section));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        assertThrows(IllegalArgumentException.class, () -> 
            batchService.createBatch("B-001", operationId, workerId, sectionId, postId, "Metal", 100));

        verify(batchRepository, never()).save(any());
        verify(taskRepository, never()).save(any());
    }

    // ==========================================
    // startBatch tests
    // ==========================================

    @Test
    void startBatch_Success() {
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));

        Batch result = batchService.startBatch(batchId, workerId);

        assertEquals(BatchStatus.IN_PROGRESS, result.getStatus());
        assertEquals(worker, result.getWorker());

        verify(batchRepository).save(batch);
        ArgumentCaptor<TimeLog> logCaptor = ArgumentCaptor.forClass(TimeLog.class);
        verify(timeLogRepository).save(logCaptor.capture());
        
        TimeLog savedLog = logCaptor.getValue();
        assertEquals(worker, savedLog.getWorker());
        assertNotNull(savedLog.getStartTime());
    }

    @Test
    void startBatch_ThrowsException_WhenBatchNotFound() {
        when(batchRepository.findById(batchId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> batchService.startBatch(batchId, workerId));
        
        verify(batchRepository, never()).save(any());
        verify(timeLogRepository, never()).save(any());
    }

    @Test
    void startBatch_ThrowsException_WhenWorkerNotFound() {
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(workerRepository.findById(workerId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> batchService.startBatch(batchId, workerId));
        
        verify(batchRepository, never()).save(any());
        verify(timeLogRepository, never()).save(any());
    }

    // ==========================================
    // completeBatch tests
    // ==========================================

    @Test
    void completeBatch_AwaitingDistribution() {
        batch.setDistributedQuantity(50);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(batchRepository.save(batch)).thenReturn(batch);

        Batch result = batchService.completeBatch(batchId, 100);

        assertEquals(100, result.getActualQuantity());
        assertEquals(BatchStatus.AWAITING_DISTRIBUTION, result.getStatus());
        assertFalse(result.getDistributionComplete());
        assertNull(result.getCompletedAt());

        verify(batchRepository).save(batch);
    }

    @Test
    void completeBatch_Completed() {
        batch.setDistributedQuantity(100);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(batchRepository.save(batch)).thenReturn(batch);

        Batch result = batchService.completeBatch(batchId, 100);

        assertEquals(100, result.getActualQuantity());
        assertEquals(BatchStatus.COMPLETED, result.getStatus());
        assertTrue(result.getDistributionComplete());
        assertNotNull(result.getCompletedAt());

        verify(batchRepository).save(batch);
    }

    @Test
    void completeBatch_ThrowsException_WhenBatchNotFound() {
        when(batchRepository.findById(batchId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> batchService.completeBatch(batchId, 100));

        verify(batchRepository, never()).save(any());
    }

    // ==========================================
    // distributeBatch tests
    // ==========================================

    @Test
    void distributeBatch_Success_PartialDistribution() {
        batch.setDistributedQuantity(20);
        batch.setActualQuantity(100);
        when(batchRepository.findByIdLocked(batchId)).thenReturn(Optional.of(batch));
        when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));
        when(batchRepository.save(batch)).thenReturn(batch);

        Map<UUID, Integer> distribution = new HashMap<>();
        distribution.put(palletId, 30);

        Batch result = batchService.distributeBatch(batchId, distribution);

        assertEquals(50, result.getDistributedQuantity());
        assertNotEquals(BatchStatus.COMPLETED, result.getStatus());

        verify(batchRepository).save(batch);
    }

    @Test
    void distributeBatch_Success_FullDistribution() {
        batch.setDistributedQuantity(50);
        batch.setActualQuantity(100);
        when(batchRepository.findByIdLocked(batchId)).thenReturn(Optional.of(batch));
        when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));
        when(batchRepository.save(batch)).thenReturn(batch);

        Map<UUID, Integer> distribution = new HashMap<>();
        distribution.put(palletId, 50);

        Batch result = batchService.distributeBatch(batchId, distribution);

        assertEquals(100, result.getDistributedQuantity());
        assertEquals(BatchStatus.COMPLETED, result.getStatus());
        assertTrue(result.getDistributionComplete());
        assertNotNull(result.getCompletedAt());

        verify(batchRepository).save(batch);
    }

    @Test
    void distributeBatch_LinksProducedAssemblyInstanceToPallet() {
        UUID productInstanceId = UUID.randomUUID();
        UUID assemblyId = UUID.randomUUID();

        Assembly assembly = new Assembly();
        assembly.setId(assemblyId);
        operation.setAssembly(assembly);

        ProductInstance product = new ProductInstance();
        product.setId(productInstanceId);
        pallet.setOwnerProduct(product);

        AssemblyInstance assemblyInstance = new AssemblyInstance();
        assemblyInstance.setId(UUID.randomUUID());
        assemblyInstance.setStatus(AssemblyInstanceStatus.PLANNED);

        batch.setDistributedQuantity(0);
        batch.setActualQuantity(100);
        when(batchRepository.findByIdLocked(batchId)).thenReturn(Optional.of(batch));
        when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));
        when(assemblyInstanceRepository.findByAssemblyIdAndProductInstanceId(assemblyId, productInstanceId))
                .thenReturn(Optional.of(assemblyInstance));
        when(batchRepository.save(batch)).thenReturn(batch);

        Map<UUID, Integer> distribution = new HashMap<>();
        distribution.put(palletId, 40);

        batchService.distributeBatch(batchId, distribution);

        assertTrue(pallet.getAssemblyInstances().contains(assemblyInstance));
        assertEquals(AssemblyInstanceStatus.IN_PROGRESS, assemblyInstance.getStatus());
        assertEquals(PalletStatus.ASSEMBLING, pallet.getStatus());
        verify(palletRepository).save(pallet);
    }

    @Test
    void distributeBatch_ThrowsException_WhenPalletNotFound() {
        when(batchRepository.findByIdLocked(batchId)).thenReturn(Optional.of(batch));
        when(palletRepository.findById(palletId)).thenReturn(Optional.empty());

        Map<UUID, Integer> distribution = new HashMap<>();
        distribution.put(palletId, 50);

        assertThrows(EntityNotFoundException.class, () -> batchService.distributeBatch(batchId, distribution));

        verify(batchRepository, never()).save(any());
    }

    @Test
    void distributeBatch_ThrowsException_WhenExceedsActualQuantity() {
        batch.setDistributedQuantity(80);
        batch.setActualQuantity(100);
        when(batchRepository.findByIdLocked(batchId)).thenReturn(Optional.of(batch));
        when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));

        Map<UUID, Integer> distribution = new HashMap<>();
        distribution.put(palletId, 30);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> batchService.distributeBatch(batchId, distribution));
        assertEquals("Cannot distribute more than actual produced quantity", exception.getMessage());

        verify(batchRepository, never()).save(any());
    }

    @Test
    void distributeBatch_ThrowsException_WhenBatchNotFound() {
        when(batchRepository.findByIdLocked(batchId)).thenReturn(Optional.empty());

        Map<UUID, Integer> distribution = new HashMap<>();
        distribution.put(palletId, 50);

        assertThrows(EntityNotFoundException.class, () -> batchService.distributeBatch(batchId, distribution));

        verify(batchRepository, never()).save(any());
    }

    @Test
    void distributeBatch_NullDistributedQuantity_Handled() {
        batch.setDistributedQuantity(null);
        batch.setActualQuantity(100);
        when(batchRepository.findByIdLocked(batchId)).thenReturn(Optional.of(batch));
        when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));
        when(batchRepository.save(batch)).thenReturn(batch);

        Map<UUID, Integer> distribution = new HashMap<>();
        distribution.put(palletId, 40);

        Batch result = batchService.distributeBatch(batchId, distribution);

        assertEquals(40, result.getDistributedQuantity());

        verify(batchRepository).save(batch);
    }

    // ==========================================
    // findActiveBatches test
    // ==========================================

    @Test
    void findActiveBatches_Success() {
        List<Batch> activeBatches = Arrays.asList(batch, new Batch());
        when(batchRepository.findActiveBatches()).thenReturn(activeBatches);

        List<Batch> result = batchService.findActiveBatches();

        assertEquals(2, result.size());
        verify(batchRepository).findActiveBatches();
    }
}
