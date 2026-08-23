package com.example.productionmvp.service;

import com.example.productionmvp.model.*;
import com.example.productionmvp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TaskExecutionServiceTest {
    @Mock private TaskRepository taskRepository;
    @Mock private AssemblyInstanceRepository assemblyInstanceRepository;
    @Mock private OperationRepository operationRepository;
    @Mock private WorkerRepository workerRepository;
    @Mock private PostRepository postRepository;
    @Mock private TimeLogRepository timeLogRepository;
    @Mock private HistoryEventRepository historyEventRepository;
    @Mock private ResourceConstraintService resourceConstraintService;
    @Mock private DefectService defectService;
    @Mock private MaterialService materialService;
    @Mock private ProductInstanceRepository productInstanceRepository;
    @Mock private SeriesService seriesService;
    @Mock private SseService sseService;

    @InjectMocks private TaskExecutionService taskExecutionService;

    private UUID assemblyInstanceId;
    private UUID operationId;
    private UUID materialId;
    private AssemblyInstance assemblyInstance;
    private Operation operation;
    private Material material;

    @BeforeEach
    void setUp() {
        assemblyInstanceId = UUID.randomUUID();
        operationId = UUID.randomUUID();
        materialId = UUID.randomUUID();

        assemblyInstance = new AssemblyInstance();
        assemblyInstance.setId(assemblyInstanceId);

        material = new Material();
        material.setId(materialId);

        operation = new Operation();
        operation.setId(operationId);
        operation.setMaterialQuantityPerUnit(2.0);
        operation.setRequiredMaterials(Set.of(material));

        lenient().when(assemblyInstanceRepository.findById(assemblyInstanceId)).thenReturn(Optional.of(assemblyInstance));
        lenient().when(operationRepository.findById(operationId)).thenReturn(Optional.of(operation));
        lenient().when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void testUnblockTask() {
        UUID taskId = UUID.randomUUID();
        Task mockTask = new Task();
        mockTask.setStatus(TaskStatus.BLOCKED);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(mockTask));

        Task result = taskExecutionService.unblockTask(taskId);
        assertEquals(TaskStatus.READY, result.getStatus());
        verify(taskRepository).save(mockTask);
    }

    @Test
    void createIndividualTask_ReservesRequiredMaterials() {
        Task created = taskExecutionService.createIndividualTask(assemblyInstanceId, operationId, null, null);

        assertEquals(TaskStatus.READY, created.getStatus());
        assertFalse(created.isMissingMaterials());

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<Map<String, Object>>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(materialService).reserveMaterials(org.mockito.ArgumentMatchers.eq(created.getId()), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(2.0, captor.getValue().get(0).get("requiredQuantity"));
    }

    @Test
    void createIndividualTask_InsufficientMaterials_BlocksTask() {
        when(materialService.findInsufficientMaterials(anyList())).thenReturn(List.of(material));

        Task created = taskExecutionService.createIndividualTask(assemblyInstanceId, operationId, null, null);

        assertEquals(TaskStatus.BLOCKED, created.getStatus());
        assertTrue(created.isMissingMaterials());
        verify(materialService, never()).reserveMaterials(any(), anyList());
        assertTrue(created.getMissingMaterialsList().contains(material));
    }

    @Test
    void completeTask_ConsumesReservedMaterials() {
        UUID taskId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();

        Task task = new Task();
        task.setId(taskId);
        task.setStatus(TaskStatus.IN_PROGRESS);

        Worker worker = new Worker();
        worker.setId(workerId);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));

        taskExecutionService.completeTask(taskId, workerId);

        verify(materialService).consumeMaterials(taskId);
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
    }

    @Test
    void cancelTask_ReleasesReservationAndPost() {
        UUID taskId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        Post post = new Post();
        post.setId(postId);

        Task task = new Task();
        task.setId(taskId);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setPost(post);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        Task result = taskExecutionService.cancelTask(taskId, null);

        assertEquals(TaskStatus.CANCELLED, result.getStatus());
        verify(resourceConstraintService).releasePost(postId);
        verify(materialService).releaseMaterialReservation(taskId);
    }

    @Test
    void cancelTask_AlreadyCompleted_ThrowsException() {
        UUID taskId = UUID.randomUUID();
        Task task = new Task();
        task.setId(taskId);
        task.setStatus(TaskStatus.COMPLETED);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        assertThrows(IllegalStateException.class, () -> taskExecutionService.cancelTask(taskId, null));
        verify(materialService, never()).releaseMaterialReservation(any());
    }

    @Test
    void unblockTask_RetriesReservation_StaysBlockedOnFailure() {
        UUID taskId = UUID.randomUUID();
        Task task = new Task();
        task.setId(taskId);
        task.setStatus(TaskStatus.BLOCKED);
        task.setMissingMaterials(true);
        task.setOperation(operation);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(materialService.findInsufficientMaterials(anyList())).thenReturn(List.of(material));

        Task result = taskExecutionService.unblockTask(taskId);

        assertEquals(TaskStatus.BLOCKED, result.getStatus());
        assertTrue(result.isMissingMaterials());
        verify(materialService, never()).reserveMaterials(any(), anyList());
    }

    @Test
    void unblockTask_RetriesReservation_ReadyOnSuccess() {
        UUID taskId = UUID.randomUUID();
        Task task = new Task();
        task.setId(taskId);
        task.setStatus(TaskStatus.BLOCKED);
        task.setMissingMaterials(true);
        task.setOperation(operation);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        Task result = taskExecutionService.unblockTask(taskId);

        assertEquals(TaskStatus.READY, result.getStatus());
        assertFalse(result.isMissingMaterials());
    }

    @Test
    void startTask_MarksProductInProduction_WhenPlanned() {
        UUID taskId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID seriesId = UUID.randomUUID();

        Series series = new Series();
        series.setId(seriesId);

        ProductInstance product = new ProductInstance();
        product.setId(productId);
        product.setStatus(InstanceStatus.PLANNED);
        product.setSeries(series);

        assemblyInstance.setProductInstance(product);
        assemblyInstance.setStatus(AssemblyInstanceStatus.PLANNED);

        Task task = new Task();
        task.setId(taskId);
        task.setStatus(TaskStatus.READY);
        task.setOperation(operation);
        task.setAssemblyInstance(assemblyInstance);

        Worker worker = new Worker();
        worker.setId(workerId);

        when(taskRepository.findByIdLocked(taskId)).thenReturn(Optional.of(task));
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));

        taskExecutionService.startTask(taskId, workerId);

        assertEquals(InstanceStatus.IN_PRODUCTION, product.getStatus());
        verify(productInstanceRepository).save(product);
        verify(seriesService).updateSeriesStatus(seriesId);
    }

    @Test
    void startTask_LeavesProductUntouched_WhenAlreadyInProduction() {
        UUID taskId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();

        ProductInstance product = new ProductInstance();
        product.setId(UUID.randomUUID());
        product.setStatus(InstanceStatus.IN_PRODUCTION);

        assemblyInstance.setProductInstance(product);
        assemblyInstance.setStatus(AssemblyInstanceStatus.PLANNED);

        Task task = new Task();
        task.setId(taskId);
        task.setStatus(TaskStatus.READY);
        task.setOperation(operation);
        task.setAssemblyInstance(assemblyInstance);

        Worker worker = new Worker();
        worker.setId(workerId);

        when(taskRepository.findByIdLocked(taskId)).thenReturn(Optional.of(task));
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));

        taskExecutionService.startTask(taskId, workerId);

        assertEquals(InstanceStatus.IN_PRODUCTION, product.getStatus());
        verify(productInstanceRepository, never()).save(any());
        verify(seriesService, never()).updateSeriesStatus(any());
    }

    @Test
    void completeTask_LastOperation_CompletesAssemblyAndReadiesProduct() {
        UUID taskId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        UUID seriesId = UUID.randomUUID();

        Series series = new Series();
        series.setId(seriesId);

        ProductInstance product = new ProductInstance();
        product.setId(UUID.randomUUID());
        product.setStatus(InstanceStatus.IN_PRODUCTION);
        product.setSeries(series);

        assemblyInstance.setProductInstance(product);
        assemblyInstance.setStatus(AssemblyInstanceStatus.IN_PROGRESS);

        Task task = new Task();
        task.setId(taskId);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setOperation(operation);
        task.setAssemblyInstance(assemblyInstance);

        Worker worker = new Worker();
        worker.setId(workerId);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(operationRepository.findByDependsOnOperation(operation)).thenReturn(List.of());
        when(assemblyInstanceRepository.findByProductInstanceId(product.getId()))
                .thenReturn(List.of(assemblyInstance));

        taskExecutionService.completeTask(taskId, workerId);

        assertEquals(AssemblyInstanceStatus.COMPLETED, assemblyInstance.getStatus());
        assertEquals(InstanceStatus.READY, product.getStatus());
        verify(productInstanceRepository).save(product);
        verify(seriesService).updateSeriesStatus(seriesId);
    }

    @Test
    void completeTask_LastOperation_ProductNotReadyWhileOtherAssemblyPending() {
        UUID taskId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();

        ProductInstance product = new ProductInstance();
        product.setId(UUID.randomUUID());
        product.setStatus(InstanceStatus.IN_PRODUCTION);

        assemblyInstance.setProductInstance(product);
        assemblyInstance.setStatus(AssemblyInstanceStatus.IN_PROGRESS);

        AssemblyInstance otherAssembly = new AssemblyInstance();
        otherAssembly.setId(UUID.randomUUID());
        otherAssembly.setProductInstance(product);
        otherAssembly.setStatus(AssemblyInstanceStatus.PLANNED);

        Task task = new Task();
        task.setId(taskId);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setOperation(operation);
        task.setAssemblyInstance(assemblyInstance);

        Worker worker = new Worker();
        worker.setId(workerId);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(operationRepository.findByDependsOnOperation(operation)).thenReturn(List.of());
        when(assemblyInstanceRepository.findByProductInstanceId(product.getId()))
                .thenReturn(List.of(assemblyInstance, otherAssembly));

        taskExecutionService.completeTask(taskId, workerId);

        assertEquals(AssemblyInstanceStatus.COMPLETED, assemblyInstance.getStatus());
        assertEquals(InstanceStatus.IN_PRODUCTION, product.getStatus());
        verify(productInstanceRepository, never()).save(any());
    }

}
