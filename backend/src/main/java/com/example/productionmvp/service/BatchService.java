package com.example.productionmvp.service;

import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.model.*;
import com.example.productionmvp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class BatchService {

    private final BatchRepository batchRepository;
    private final OperationRepository operationRepository;
    private final WorkerRepository workerRepository;
    private final SectionRepository sectionRepository;
    private final PostRepository postRepository;
    private final TaskRepository taskRepository;
    private final TimeLogRepository timeLogRepository;
    private final PalletRepository palletRepository;
    private final AssemblyInstanceRepository assemblyInstanceRepository;
    private final MaterialService materialService;
    private final SseService sseService;

    public BatchService(BatchRepository batchRepository,
                        OperationRepository operationRepository,
                        WorkerRepository workerRepository,
                        SectionRepository sectionRepository,
                        PostRepository postRepository,
                        TaskRepository taskRepository,
                        TimeLogRepository timeLogRepository,
                        PalletRepository palletRepository,
                        AssemblyInstanceRepository assemblyInstanceRepository,
                        MaterialService materialService,
                        SseService sseService) {
        this.batchRepository = batchRepository;
        this.operationRepository = operationRepository;
        this.workerRepository = workerRepository;
        this.sectionRepository = sectionRepository;
        this.postRepository = postRepository;
        this.taskRepository = taskRepository;
        this.timeLogRepository = timeLogRepository;
        this.palletRepository = palletRepository;
        this.assemblyInstanceRepository = assemblyInstanceRepository;
        this.materialService = materialService;
        this.sseService = sseService;
    }

    @Transactional
    public Batch createBatch(String number, UUID operationId, UUID workerId, UUID sectionId, UUID postId, String materialDetail, int plannedQty) {
        Operation operation = operationRepository.findById(operationId)
                .orElseThrow(() -> new EntityNotFoundException("Operation not found"));
        Worker worker = workerId != null ? workerRepository.findById(workerId).orElse(null) : null;
        Section section = sectionId != null ? sectionRepository.findById(sectionId).orElse(null) : null;
        // Same fallback as createIndividualTask: a batch task with post=null is permanently
        // invisible to findAvailableTasksForPost (its "t.post.id = :postId" join can never match
        // NULL), so a batch created without an explicit post would be un-executable by any worker.
        Post post = postId != null ? postRepository.findById(postId).orElse(null) : operation.getPost();

        if (post != null && post.getOperationTypes() != null && !post.getOperationTypes().isEmpty()) {
            boolean supportsOperation = false;
            for (String allowedType : post.getOperationTypes().split(",")) {
                if (allowedType.trim().equalsIgnoreCase(operation.getName())) {
                    supportsOperation = true;
                    break;
                }
            }
            if (!supportsOperation) {
                throw new IllegalArgumentException("Selected post does not support operation: " + operation.getName());
            }
        }

        Batch batch = new Batch();
        batch.setNumber(number);
        batch.setOperation(operation);
        batch.setWorker(worker);
        batch.setSection(section);
        batch.setPost(post);
        batch.setMaterialDetail(materialDetail);
        batch.setPlannedQuantity(plannedQty);
        batch.setStatus(BatchStatus.CREATED);
        batchRepository.save(batch);
        sseService.broadcastDashboardUpdate();

        Task batchTask = new Task();
        batchTask.setStatus(TaskStatus.CREATED);
        batchTask.setAssignedWorker(worker);
        batchTask.setCreatedAt(LocalDateTime.now());
        batchTask.setTaskType(OperationType.BATCH);
        batchTask.setOperation(operation);
        batchTask.setPost(post);
        batchTask.setBatch(batch);
        taskRepository.save(batchTask);

        reserveBatchMaterials(batchTask, operation, plannedQty);

        return batch;
    }

    // ТЗ 4.2: a batch consumes materials for its whole planned quantity at once
    // (e.g. "порізка 100 труб" reserves stock for all 100 up front).
    private void reserveBatchMaterials(Task batchTask, Operation operation, int plannedQty) {
        if (operation.getRequiredMaterials() == null || operation.getRequiredMaterials().isEmpty()) {
            return;
        }
        double qtyPerUnit = operation.getMaterialQuantityPerUnit() != null ? operation.getMaterialQuantityPerUnit() : 1.0;

        List<Map<String, Object>> materialReqs = new ArrayList<>();
        for (Material material : operation.getRequiredMaterials()) {
            Map<String, Object> req = new HashMap<>();
            req.put("materialId", material.getId().toString());
            req.put("requiredQuantity", qtyPerUnit * plannedQty);
            materialReqs.add(req);
        }

        List<Material> insufficient = materialService.findInsufficientMaterials(materialReqs);
        if (!insufficient.isEmpty()) {
            batchTask.setMissingMaterials(true);
            batchTask.getMissingMaterialsList().addAll(insufficient);
            batchTask.setStatus(TaskStatus.BLOCKED);
            taskRepository.save(batchTask);
            return;
        }

        materialService.reserveMaterials(batchTask.getId(), materialReqs);
    }

    @Transactional
    public Batch startBatch(UUID batchId, UUID workerId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new EntityNotFoundException("Batch not found"));
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new EntityNotFoundException("Worker not found"));

        batch.setStatus(BatchStatus.IN_PROGRESS);
        batch.setWorker(worker);
        // Only the batch's *task* was stamped, never the batch itself, so BatchDTO.startTime -
        // which reads Batch.startedAt - came back null for every batch that had ever been
        // started, and "коли почали різати" had no answer anywhere in the API.
        if (batch.getStartedAt() == null) {
            batch.setStartedAt(LocalDateTime.now());
        }
        batchRepository.save(batch);

        List<Task> batchTasks = taskRepository.findByBatchId(batch.getId());
        Task batchTask = null;
        if (!batchTasks.isEmpty()) {
            batchTask = batchTasks.get(0);
            batchTask.setStatus(TaskStatus.IN_PROGRESS);
            batchTask.setStartedAt(LocalDateTime.now());
            batchTask.setAssignedWorker(worker);
            taskRepository.save(batchTask);
        }

        TimeLog log = new TimeLog();
        log.setWorker(worker);
        log.setStartTime(LocalDateTime.now());
        if (batchTask != null) {
            log.setTask(batchTask);
        }
        timeLogRepository.save(log);
        sseService.broadcastDashboardUpdate();

        return batch;
    }

    @Transactional
    public Batch completeBatch(UUID batchId, int actualQty) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new EntityNotFoundException("Batch not found"));
        
        batch.setActualQuantity(actualQty);
        
        // Distribution check
        if (batch.getDistributedQuantity() < actualQty) {
            batch.setStatus(BatchStatus.AWAITING_DISTRIBUTION);
            batch.setDistributionComplete(false);
        } else {
            batch.setStatus(BatchStatus.COMPLETED);
            batch.setDistributionComplete(true);
            batch.setCompletedAt(LocalDateTime.now());
        }
        
        batchRepository.save(batch);

        List<Task> batchTasks = taskRepository.findByBatchId(batch.getId());
        if (!batchTasks.isEmpty()) {
            Task batchTask = batchTasks.get(0);
            batchTask.setStatus(TaskStatus.COMPLETED);
            batchTask.setCompletedAt(LocalDateTime.now());
            taskRepository.save(batchTask);

            materialService.consumeMaterials(batchTask.getId());

            timeLogRepository.findByTaskAndEndTimeIsNull(batchTask).ifPresent(tLog -> {
                tLog.setEndTime(LocalDateTime.now());
                if (tLog.getStartTime() != null) {
                    tLog.setDurationSeconds(java.time.Duration.between(tLog.getStartTime(), tLog.getEndTime()).getSeconds());
                }
                timeLogRepository.save(tLog);
            });
        }

        sseService.broadcastDashboardUpdate();
        return batch;
    }

    @Transactional
    public Batch distributeBatch(UUID batchId, Map<UUID, Integer> palletDistribution) {
        Batch batch = batchRepository.findByIdLocked(batchId)
                .orElseThrow(() -> new EntityNotFoundException("Batch not found"));

        // The batch's operation tells us which assembly the produced parts belong to
        // (e.g. "Порізка" on the "Рама-основа" assembly) so we know which AssemblyInstance
        // on the target pallet's owner product to attach the physical output to.
        Assembly targetAssembly = batch.getOperation() != null ? batch.getOperation().getAssembly() : null;

        int totalDistributedNow = 0;

        for (Map.Entry<UUID, Integer> entry : palletDistribution.entrySet()) {
            UUID palletId = entry.getKey();
            Integer qty = entry.getValue() != null ? entry.getValue() : 0;

            Pallet pallet = palletRepository.findById(palletId)
                    .orElseThrow(() -> new EntityNotFoundException("Pallet not found: " + palletId));

            if (targetAssembly != null && pallet.getOwnerProduct() != null) {
                assemblyInstanceRepository
                        .findByAssemblyIdAndProductInstanceId(targetAssembly.getId(), pallet.getOwnerProduct().getId())
                        .ifPresent(instance -> {
                            if (!pallet.getAssemblyInstances().contains(instance)) {
                                pallet.getAssemblyInstances().add(instance);
                            }
                            if (instance.getStatus() == AssemblyInstanceStatus.PLANNED) {
                                instance.setStatus(AssemblyInstanceStatus.IN_PROGRESS);
                                assemblyInstanceRepository.save(instance);
                            }
                        });
            }

            if (pallet.getStatus() == PalletStatus.EMPTY) {
                pallet.setStatus(PalletStatus.ASSEMBLING);
            }
            palletRepository.save(pallet);

            totalDistributedNow += qty;
        }

        int currentDist = batch.getDistributedQuantity() != null ? batch.getDistributedQuantity() : 0;
        int newDist = currentDist + totalDistributedNow;

        if (newDist > batch.getActualQuantity()) {
            // A bare RuntimeException falls through to the catch-all handler, so refusing this
            // answered 500 "An unexpected error occurred" - which reads as "the system broke"
            // rather than "you are trying to place more parts than were made". The rule is
            // correct; only the way it was reported was not.
            throw new IllegalStateException(String.format(
                    "Не можна розподілити більше, ніж вироблено. Вироблено %d шт, вже розподілено %d, ви додаєте ще %d.",
                    batch.getActualQuantity(), currentDist, totalDistributedNow));
        }

        batch.setDistributedQuantity(newDist);

        if (newDist == batch.getActualQuantity()) {
            batch.setStatus(BatchStatus.COMPLETED);
            batch.setDistributionComplete(true);
            batch.setCompletedAt(LocalDateTime.now());
        }

        Batch saved = batchRepository.save(batch);
        sseService.broadcastDashboardUpdate();
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Batch> findActiveBatches() {
        return batchRepository.findActiveBatches();
    }
}
