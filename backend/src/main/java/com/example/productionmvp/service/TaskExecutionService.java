package com.example.productionmvp.service;

import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.exception.OperationDependencyException;
import com.example.productionmvp.model.*;
import com.example.productionmvp.repository.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class TaskExecutionService {

    private final TaskRepository taskRepository;
    private final AssemblyInstanceRepository assemblyInstanceRepository;
    private final OperationRepository operationRepository;
    private final WorkerRepository workerRepository;
    private final PostRepository postRepository;
    private final TimeLogRepository timeLogRepository;
    private final HistoryEventRepository historyEventRepository;
    private final ResourceConstraintService resourceConstraintService;
    private final DefectService defectService;
    private final MaterialService materialService;
    private final ProductInstanceRepository productInstanceRepository;
    private final SeriesService seriesService;
    private final SseService sseService;

    public TaskExecutionService(TaskRepository taskRepository,
                                AssemblyInstanceRepository assemblyInstanceRepository,
                                OperationRepository operationRepository,
                                WorkerRepository workerRepository,
                                PostRepository postRepository,
                                TimeLogRepository timeLogRepository,
                                HistoryEventRepository historyEventRepository,
                                ResourceConstraintService resourceConstraintService,
                                @Lazy DefectService defectService,
                                MaterialService materialService,
                                ProductInstanceRepository productInstanceRepository,
                                @Lazy SeriesService seriesService,
                                SseService sseService) {
        this.taskRepository = taskRepository;
        this.assemblyInstanceRepository = assemblyInstanceRepository;
        this.operationRepository = operationRepository;
        this.workerRepository = workerRepository;
        this.postRepository = postRepository;
        this.timeLogRepository = timeLogRepository;
        this.historyEventRepository = historyEventRepository;
        this.resourceConstraintService = resourceConstraintService;
        this.defectService = defectService;
        this.materialService = materialService;
        this.productInstanceRepository = productInstanceRepository;
        this.seriesService = seriesService;
        this.sseService = sseService;
    }

    @Transactional
    public Task createIndividualTask(UUID assemblyInstanceId, UUID operationId, UUID workerId, UUID postId) {
        AssemblyInstance instance = assemblyInstanceRepository.findById(assemblyInstanceId)
                .orElseThrow(() -> new EntityNotFoundException("AssemblyInstance not found: " + assemblyInstanceId));
        Operation operation = operationRepository.findById(operationId)
                .orElseThrow(() -> new EntityNotFoundException("Operation not found: " + operationId));
        Post post = postId != null ? postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found: " + postId)) : operation.getPost();
        Worker worker = workerId != null ? workerRepository.findById(workerId)
                .orElse(null) : null;

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

        Task task = new Task();
        task.setAssemblyInstance(instance);
        task.setOperation(operation);
        task.setPost(post);
        task.setAssignedWorker(worker);
        task.setTaskType(OperationType.INDIVIDUAL);
        task.setStatus(TaskStatus.READY);
        task.setSeries(instance.getProductInstance() != null ? instance.getProductInstance().getSeries() : null);
        task.setCreatedAt(LocalDateTime.now());
        task.setNormativeTimeMinutes(operation.getNormativeTimeMinutes());
        task.setEquipment(operation.getEquipment());
        task.setTools(operation.getTools());

        task = taskRepository.save(task);
        logHistoryEvent("Task Created", task, worker);

        reserveTaskMaterials(task, operation, 1);

        return task;
    }

    // ТЗ 4.2: reserve materials as soon as a task is created. If stock is insufficient,
    // the task is still created but blocked and flagged so the manager/dispatcher can see
    // the shortage instead of the operator silently starting work without materials.
    private void reserveTaskMaterials(Task task, Operation operation, int quantityMultiplier) {
        if (operation.getRequiredMaterials() == null || operation.getRequiredMaterials().isEmpty()) {
            return;
        }
        double qtyPerUnit = operation.getMaterialQuantityPerUnit() != null ? operation.getMaterialQuantityPerUnit() : 1.0;

        List<Map<String, Object>> materialReqs = new ArrayList<>();
        for (Material material : operation.getRequiredMaterials()) {
            Map<String, Object> req = new HashMap<>();
            req.put("materialId", material.getId().toString());
            req.put("requiredQuantity", qtyPerUnit * quantityMultiplier);
            materialReqs.add(req);
        }

        List<Material> insufficient = materialService.findInsufficientMaterials(materialReqs);
        if (!insufficient.isEmpty()) {
            task.setMissingMaterials(true);
            task.getMissingMaterialsList().addAll(insufficient);
            task.setStatus(TaskStatus.BLOCKED);
            taskRepository.save(task);
            logHistoryEvent("Task Blocked: insufficient materials", task, task.getAssignedWorker());
            return;
        }

        materialService.reserveMaterials(task.getId(), materialReqs);
    }

    @Transactional
    public Task startTask(UUID taskId, UUID workerId) {
        // Pessimistic lock: two operators tapping "Start" on the same task at the same time
        // must not both win the READY -> IN_PROGRESS transition (that used to leave two open
        // TimeLog rows behind and permanently break pause/complete on the task).
        Task task = taskRepository.findByIdLocked(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new EntityNotFoundException("Worker not found: " + workerId));

        if (task.getStatus() == TaskStatus.IN_PROGRESS) {
            // Silently returning "success" here let a worker who lost the race for a task see
            // the exact same response as the winner - the pessimistic lock above stopped the
            // data corruption, but the loser had no way to know they'd lost. Only treat it as
            // a harmless no-op (e.g. a double-tap by the same worker) when it's truly the same
            // worker who already holds it.
            if (task.getAssignedWorker() != null && !task.getAssignedWorker().getId().equals(workerId)) {
                throw new IllegalStateException("Задачу вже розпочав інший працівник: " + task.getAssignedWorker().getName());
            }
            return task;
        }

        if (task.getStatus() != TaskStatus.READY && task.getStatus() != TaskStatus.PAUSED) {
            throw new IllegalStateException("Task cannot be started from current status: " + task.getStatus());
        }

        if (!areDependenciesMet(task.getAssemblyInstance(), task.getOperation())) {
            throw new OperationDependencyException("Dependencies are not met for this operation.");
        }

        if (task.getPost() != null) {
            resourceConstraintService.occupyPost(task.getPost().getId());
        }

        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setStartedAt(LocalDateTime.now());
        task.setAssignedWorker(worker);
        
        if (task.getAssemblyInstance() != null) {
            task.getAssemblyInstance().setStatus(AssemblyInstanceStatus.IN_PROGRESS);
            assemblyInstanceRepository.save(task.getAssemblyInstance());
            markProductInProduction(task.getAssemblyInstance());
        }

        TimeLog timeLog = new TimeLog();
        timeLog.setTask(task);
        timeLog.setWorker(worker);
        timeLog.setStartTime(LocalDateTime.now());
        timeLogRepository.save(timeLog);

        taskRepository.save(task);
        logHistoryEvent("Task Started", task, worker);
        sseService.broadcastDashboardUpdate();

        return task;
    }

    // Dashboards read ProductInstance.status/Series.status to report "у виробництві"/"активні
    // серії" — without this, work could visibly be happening (tasks IN_PROGRESS) while every
    // product/series counter stays stuck on PLANNED forever.
    private void markProductInProduction(AssemblyInstance assemblyInstance) {
        ProductInstance product = assemblyInstance.getProductInstance();
        if (product == null || product.getStatus() != InstanceStatus.PLANNED) {
            return;
        }
        product.setStatus(InstanceStatus.IN_PRODUCTION);
        productInstanceRepository.save(product);
        if (product.getSeries() != null) {
            seriesService.updateSeriesStatus(product.getSeries().getId());
        }
    }

    private void checkProductReady(AssemblyInstance assemblyInstance) {
        ProductInstance product = assemblyInstance.getProductInstance();
        if (product == null || product.getStatus() == InstanceStatus.READY) {
            return;
        }
        boolean allDone = assemblyInstanceRepository.findByProductInstanceId(product.getId()).stream()
                .allMatch(ai -> ai.getStatus() == AssemblyInstanceStatus.COMPLETED);
        if (allDone) {
            product.setStatus(InstanceStatus.READY);
            productInstanceRepository.save(product);
        }
        if (product.getSeries() != null) {
            seriesService.updateSeriesStatus(product.getSeries().getId());
        }
    }

    // Without this, any authenticated worker who knew (or guessed/enumerated) a task id could
    // pause/resume/complete/damage a colleague's in-progress work with no error at all - the
    // pessimistic lock in startTask stops two people racing to *start* the same task, but
    // nothing stopped a third party acting on a task someone else already legitimately holds.
    // Admins are exempt since they're already trusted with broader access everywhere else.
    private void assertOwnedByOrAdmin(Task task, Worker worker) {
        if (task.getAssignedWorker() != null
                && !task.getAssignedWorker().getId().equals(worker.getId())
                && worker.getSystemRole() != SystemRole.ADMIN) {
            throw new IllegalStateException("Задача призначена іншому працівнику: " + task.getAssignedWorker().getName());
        }
    }

    @Transactional
    public Task pauseTask(UUID taskId, UUID workerId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new EntityNotFoundException("Worker not found: " + workerId));
        assertOwnedByOrAdmin(task, worker);

        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only in-progress tasks can be paused.");
        }

        task.setStatus(TaskStatus.PAUSED);
        
        if (task.getPost() != null) {
            resourceConstraintService.releasePost(task.getPost().getId());
        }

        closeTimeLog(task);

        taskRepository.save(task);
        logHistoryEvent("Task Paused", task, worker);
        sseService.broadcastDashboardUpdate();

        return task;
    }

    @Transactional
    public Task resumeTask(UUID taskId, UUID workerId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new EntityNotFoundException("Worker not found: " + workerId));
        assertOwnedByOrAdmin(task, worker);

        if (task.getStatus() != TaskStatus.PAUSED) {
            throw new IllegalStateException("Only paused tasks can be resumed.");
        }
        
        if (task.getPost() != null) {
            resourceConstraintService.occupyPost(task.getPost().getId());
        }

        task.setStatus(TaskStatus.IN_PROGRESS);

        TimeLog timeLog = new TimeLog();
        timeLog.setTask(task);
        timeLog.setWorker(worker);
        timeLog.setStartTime(LocalDateTime.now());
        timeLogRepository.save(timeLog);

        taskRepository.save(task);
        logHistoryEvent("Task Resumed", task, worker);
        sseService.broadcastDashboardUpdate();

        return task;
    }

    @Transactional
    public Task completeTask(UUID taskId, UUID workerId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new EntityNotFoundException("Worker not found: " + workerId));
        assertOwnedByOrAdmin(task, worker);

        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only in-progress tasks can be completed.");
        }

        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        
        if (task.getPost() != null) {
            resourceConstraintService.releasePost(task.getPost().getId());
        }

        closeTimeLog(task);

        materialService.consumeMaterials(task.getId());

        if (task.getAssemblyInstance() != null) {
            checkAndUnlockNextOperations(task.getAssemblyInstance(), task.getOperation());
        }

        taskRepository.save(task);
        logHistoryEvent("Task Completed", task, worker);
        sseService.broadcastDashboardUpdate();

        return task;
    }

    // Undo an accidental "complete" click. Only safe when nothing downstream has moved on
    // yet: if the next operation's task was already started, or the assembly/product/series
    // cascade already reached further than this one operation, reopening would leave the
    // system in an inconsistent state (two steps worked at once, a product marked ready that
    // isn't), so we refuse instead of guessing. Restricted to managers/admins (not the worker's
    // own self-service undo) so there's accountability for reversing a completion.
    @Transactional
    public Task reopenTask(UUID taskId, UUID managerId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
        Worker manager = managerId != null ? workerRepository.findById(managerId).orElse(null) : null;

        if (task.getStatus() != TaskStatus.COMPLETED) {
            throw new IllegalStateException("Only completed tasks can be reopened.");
        }

        AssemblyInstance assemblyInstance = task.getAssemblyInstance();
        List<Task> spawnedNextTasks = new ArrayList<>();
        boolean assemblyCompletedByThisTask = false;

        if (assemblyInstance != null && task.getOperation() != null) {
            List<Operation> nextOps = operationRepository.findByDependsOnOperation(task.getOperation());
            for (Operation nextOp : nextOps) {
                taskRepository.findFirstByAssemblyInstanceAndOperationOrderByCreatedAtDesc(assemblyInstance, nextOp)
                        .ifPresent(spawnedNextTasks::add);
            }
            for (Task next : spawnedNextTasks) {
                if (next.getStatus() != TaskStatus.CREATED && next.getStatus() != TaskStatus.READY) {
                    throw new IllegalStateException(
                            "Неможливо скасувати завершення: наступний етап (" + next.getOperation().getName() + ") вже розпочато.");
                }
            }
            assemblyCompletedByThisTask = nextOps.isEmpty() && assemblyInstance.getStatus() == AssemblyInstanceStatus.COMPLETED;
        }

        // Cancel rather than delete: every task has HistoryEvent audit rows pointing at it
        // (a foreign key), and cancelTask already correctly releases the post slot and any
        // reserved materials this auto-created task was holding.
        for (Task next : spawnedNextTasks) {
            cancelTask(next.getId(), managerId);
        }

        if (assemblyCompletedByThisTask) {
            assemblyInstance.setStatus(AssemblyInstanceStatus.IN_PROGRESS);
            assemblyInstanceRepository.save(assemblyInstance);

            ProductInstance product = assemblyInstance.getProductInstance();
            if (product != null && product.getStatus() == InstanceStatus.READY) {
                product.setStatus(InstanceStatus.IN_PRODUCTION);
                productInstanceRepository.save(product);
                if (product.getSeries() != null) {
                    seriesService.updateSeriesStatus(product.getSeries().getId());
                }
            }
        }

        if (task.getPost() != null) {
            resourceConstraintService.occupyPost(task.getPost().getId());
        }

        materialService.reverseConsumedMaterials(task.getId());

        TimeLog timeLog = new TimeLog();
        timeLog.setTask(task);
        timeLog.setWorker(task.getAssignedWorker());
        timeLog.setStartTime(LocalDateTime.now());
        timeLogRepository.save(timeLog);

        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setCompletedAt(null);
        taskRepository.save(task);
        logHistoryEvent("Task Reopened", task, manager);
        sseService.broadcastDashboardUpdate();

        return task;
    }

    @Transactional
    public Task cancelTask(UUID taskId, UUID workerId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
        Worker worker = workerId != null ? workerRepository.findById(workerId).orElse(null) : null;

        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED) {
            throw new IllegalStateException("Task cannot be cancelled from current status: " + task.getStatus());
        }

        if (task.getStatus() == TaskStatus.IN_PROGRESS && task.getPost() != null) {
            resourceConstraintService.releasePost(task.getPost().getId());
            closeTimeLog(task);
        }

        materialService.releaseMaterialReservation(task.getId());

        task.setStatus(TaskStatus.CANCELLED);
        task.setMissingMaterials(false);
        taskRepository.save(task);
        logHistoryEvent("Task Cancelled", task, worker);
        sseService.broadcastDashboardUpdate();

        return task;
    }

    @Transactional
    public Task markDamaged(UUID taskId, UUID workerId, String reason) {
        return markDamaged(taskId, workerId, reason, DefectResolution.REWORK);
    }

    // ТЗ §6: the worker reporting the defect picks one of the 3 outcomes (repair in place /
    // scrap the part / scrap the whole assembly); resolution defaults to REWORK when the
    // caller doesn't specify one, matching the previous behavior for existing callers.
    @Transactional
    public Task markDamaged(UUID taskId, UUID workerId, String reason, DefectResolution resolution) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new EntityNotFoundException("Worker not found: " + workerId));
        assertOwnedByOrAdmin(task, worker);

        // A task already COMPLETED (or otherwise inactive) can't legitimately be found
        // defective anymore - without this guard a stale worker screen could flag a finished
        // task as damaged while completedAt stays set, and the already-unlocked next
        // operation would be left READY on a part now marked bad.
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only in-progress tasks can be marked as damaged: " + task.getStatus());
        }

        task.setStatus(TaskStatus.DAMAGED);
        
        if (task.getPost() != null) {
            try {
                resourceConstraintService.releasePost(task.getPost().getId());
            } catch (Exception e) {
                // Ignore if already released
            }
        }
        
        try {
            closeTimeLog(task);
        } catch (Exception e) {
            // Ignore if no open time log
        }

        if (task.getAssemblyInstance() != null) {
            defectService.reportDefect(task.getAssemblyInstance().getId(), taskId, reason, workerId,
                    resolution != null ? resolution : DefectResolution.REWORK);
        }

        taskRepository.save(task);
        logHistoryEvent("Task Marked Damaged", task, worker);
        sseService.broadcastDashboardUpdate();

        return task;
    }

    @Transactional
    public Task blockTask(UUID taskId, UUID workerId, String reason) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
        Worker worker = workerId != null ? workerRepository.findById(workerId).orElse(null) : null;
        
        if (task.getStatus() == TaskStatus.IN_PROGRESS && task.getPost() != null) {
            resourceConstraintService.releasePost(task.getPost().getId());
            closeTimeLog(task);
        }

        task.setStatus(TaskStatus.BLOCKED);
        taskRepository.save(task);
        logHistoryEvent("Task Blocked: " + reason, task, worker);

        return task;
    }

    @Transactional
    public Task unblockTask(UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));

        if (task.isMissingMaterials() && task.getOperation() != null) {
            task.getMissingMaterialsList().clear();
            task.setMissingMaterials(false);
            reserveTaskMaterials(task, task.getOperation(), 1);
            if (task.isMissingMaterials()) {
                // reserveTaskMaterials already re-blocked the task and logged why
                return task;
            }
        }

        task.setStatus(TaskStatus.READY);
        taskRepository.save(task);
        logHistoryEvent("Task Unblocked", task, null);

        return task;
    }

    private void closeTimeLog(Task task) {
        // Uses the list-returning query (not the Optional one) so a pre-existing race-condition
        // artifact (two open logs on one task) gets closed out and self-heals instead of
        // throwing IncorrectResultSizeDataAccessException and permanently breaking the task.
        List<TimeLog> openLogs = timeLogRepository.findAllByTaskAndEndTimeIsNull(task);
        if (openLogs.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (TimeLog timeLog : openLogs) {
            timeLog.setEndTime(now);
            timeLog.setDurationSeconds(Duration.between(timeLog.getStartTime(), now).getSeconds());
            timeLogRepository.save(timeLog);

            // Worker.totalWorkedMinutes is returned on every worker record the API serves but
            // nothing ever wrote to it, so it reported 0 hours for everyone no matter how much
            // work they had logged. Accumulated per closed log (not recomputed from the task)
            // so a task worked by two people credits each with their own share.
            Worker logWorker = timeLog.getWorker();
            if (logWorker != null && timeLog.getDurationSeconds() != null) {
                long previous = logWorker.getTotalWorkedMinutes() != null ? logWorker.getTotalWorkedMinutes() : 0L;
                logWorker.setTotalWorkedMinutes(previous + (timeLog.getDurationSeconds() / 60));
                workerRepository.save(logWorker);
            }
        }

        long totalSeconds = timeLogRepository.findByTask(task).stream()
            .filter(t -> t.getDurationSeconds() != null)
            .mapToLong(TimeLog::getDurationSeconds)
            .sum();
        task.setActualTimeMinutes((int) (totalSeconds / 60));
    }

    private boolean areDependenciesMet(AssemblyInstance assemblyInstance, Operation operation) {
        if (operation.getDependsOnOperation() == null) return true;
        
        if (assemblyInstance == null) return true; // Batch task
        
        Optional<Task> prevTask = taskRepository.findFirstByAssemblyInstanceAndOperationOrderByCreatedAtDesc(assemblyInstance, operation.getDependsOnOperation());
                
        return prevTask.isPresent() && prevTask.get().getStatus() == TaskStatus.COMPLETED;
    }

    private void checkAndUnlockNextOperations(AssemblyInstance assemblyInstance, Operation completedOperation) {
        List<Operation> nextOps = operationRepository.findByDependsOnOperation(completedOperation);

        for (Operation nextOp : nextOps) {
            if (areDependenciesMet(assemblyInstance, nextOp)) {
                createIndividualTask(assemblyInstance.getId(), nextOp.getId(), null, nextOp.getPost() != null ? nextOp.getPost().getId() : null);
            }
        }

        // No next operation depends on this one -> the assembly (вузол) itself is done.
        // This is what lets checkProductReady ever see all assemblies as COMPLETED.
        if (nextOps.isEmpty() && assemblyInstance.getStatus() != AssemblyInstanceStatus.COMPLETED) {
            assemblyInstance.setStatus(AssemblyInstanceStatus.COMPLETED);
            assemblyInstanceRepository.save(assemblyInstance);
            checkProductReady(assemblyInstance);
        }
    }

    private void logHistoryEvent(String action, Task task, Worker worker) {
        HistoryEvent event = new HistoryEvent();
        event.setAction(action);
        event.setTask(task);
        event.setWorker(worker);
        if (task != null && task.getAssemblyInstance() != null) {
            event.setProductInstance(task.getAssemblyInstance().getProductInstance());
        }
        // HistoryEvent has dedicated operation/series/batch relations for exactly this, but
        // they were never populated - every task-lifecycle event (by far the most common kind)
        // came back with a null "Етап"/series/batch on the audit screen despite the task itself
        // carrying all three.
        if (task != null) {
            event.setOperation(task.getOperation());
            event.setSeries(task.getSeries());
            event.setBatch(task.getBatch());
        }
        event.setTimestamp(LocalDateTime.now());
        historyEventRepository.save(event);
    }
}
