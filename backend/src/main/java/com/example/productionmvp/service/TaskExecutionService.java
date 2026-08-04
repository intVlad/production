package com.example.productionmvp.service;

import com.example.productionmvp.model.*;
import com.example.productionmvp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class TaskExecutionService {

    private final TaskRepository taskRepository;
    private final WorkerRepository workerRepository;
    private final TimeLogRepository timeLogRepository;
    private final StageRepository stageRepository;
    private final ProductInstanceRepository productInstanceRepository;
    private final MaterialRepository materialRepository;
    private final HistoryEventRepository historyEventRepository;

    public TaskExecutionService(TaskRepository taskRepository, 
                                WorkerRepository workerRepository, 
                                TimeLogRepository timeLogRepository,
                                StageRepository stageRepository,
                                ProductInstanceRepository productInstanceRepository,
                                MaterialRepository materialRepository,
                                HistoryEventRepository historyEventRepository) {
        this.taskRepository = taskRepository;
        this.workerRepository = workerRepository;
        this.timeLogRepository = timeLogRepository;
        this.stageRepository = stageRepository;
        this.productInstanceRepository = productInstanceRepository;
        this.materialRepository = materialRepository;
        this.historyEventRepository = historyEventRepository;
    }

    private void logHistoryEvent(String action, Task task, Worker worker) {
        HistoryEvent event = new HistoryEvent();
        event.setAction(action);
        event.setTask(task);
        if (task != null) {
            event.setProductInstance(task.getProductInstance());
            event.setStage(task.getStage());
        }
        event.setWorker(worker);
        historyEventRepository.save(event);
    }

    @Transactional
    public Task startTask(UUID taskId, UUID workerId) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        Worker worker = workerRepository.findById(workerId).orElseThrow(() -> new RuntimeException("Worker not found"));

        if (task.getStatus() != TaskStatus.PENDING && task.getStatus() != TaskStatus.PAUSED) {
            throw new RuntimeException("Task cannot be started from current status: " + task.getStatus());
        }

        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setAssignedWorker(worker);
        taskRepository.save(task);

        // Update ProductInstance status if it's the first task
        ProductInstance instance = task.getProductInstance();
        if (instance.getStatus() == InstanceStatus.PENDING) {
            instance.setStatus(InstanceStatus.IN_PROGRESS);
        }

        TimeLog timeLog = new TimeLog();
        timeLog.setTask(task);
        timeLog.setWorker(worker);
        timeLog.setStartTime(LocalDateTime.now());
        timeLogRepository.save(timeLog);

        logHistoryEvent("Розпочато роботу", task, worker);

        return task;
    }

    @Transactional
    public Task pauseTask(UUID taskId, UUID workerId) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        Worker worker = workerRepository.findById(workerId).orElseThrow(() -> new RuntimeException("Worker not found"));

        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new RuntimeException("Only in-progress tasks can be paused.");
        }

        task.setStatus(TaskStatus.PAUSED);
        taskRepository.save(task);

        TimeLog timeLog = timeLogRepository.findByTaskAndEndTimeIsNull(task)
                .orElseThrow(() -> new RuntimeException("Active time log not found"));
        
        timeLog.setEndTime(LocalDateTime.now());
        timeLog.setDurationSeconds(Duration.between(timeLog.getStartTime(), timeLog.getEndTime()).getSeconds());
        timeLogRepository.save(timeLog);

        logHistoryEvent("Пауза", task, worker);

        return task;
    }

    @Transactional
    public Task completeTask(UUID taskId, UUID workerId) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        Worker worker = workerRepository.findById(workerId).orElseThrow(() -> new RuntimeException("Worker not found"));

        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new RuntimeException("Only in-progress tasks can be completed.");
        }

        task.setStatus(TaskStatus.COMPLETED);
        taskRepository.save(task);

        TimeLog timeLog = timeLogRepository.findByTaskAndEndTimeIsNull(task)
                .orElseThrow(() -> new RuntimeException("Active time log not found"));
        
        timeLog.setEndTime(LocalDateTime.now());
        timeLog.setDurationSeconds(Duration.between(timeLog.getStartTime(), timeLog.getEndTime()).getSeconds());
        timeLogRepository.save(timeLog);

        // State Machine Progression
        ProductInstance instance = task.getProductInstance();
        Stage currentStage = task.getStage();
        
        Optional<Stage> nextStage = stageRepository.findFirstByDependsOnStage(currentStage);
        if (nextStage.isEmpty()) {
            nextStage = stageRepository.findFirstByProductModelAndOrderIndexGreaterThanOrderByOrderIndexAsc(
                    instance.getProductModel(), currentStage.getOrderIndex());
        }
                
        if (nextStage.isPresent()) {
            boolean taskExists = taskRepository.existsByProductInstanceAndStage(instance, nextStage.get());
            if (!taskExists) {
                Task nextTask = new Task();
                nextTask.setProductInstance(instance);
                nextTask.setStage(nextStage.get());
                nextTask.setStatus(TaskStatus.PENDING);
                nextTask.setCreatedAt(LocalDateTime.now());
                // dueDate is set in DataSeeder and below
                nextTask.setDueDate(LocalDateTime.now().plusDays(1));
                taskRepository.save(nextTask);
            }
            logHistoryEvent("Завершення етапу", task, worker);
        } else {
            instance.setStatus(InstanceStatus.COMPLETED);
            productInstanceRepository.save(instance);
            logHistoryEvent("Завершення етапу", task, worker);
            logHistoryEvent("Повне завершення роботи", task, worker);
        }

        return task;
    }

    @Transactional
    public Task blockTask(UUID taskId, UUID workerId) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        Worker worker = workerRepository.findById(workerId).orElseThrow(() -> new RuntimeException("Worker not found"));
        
        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw new RuntimeException("Completed tasks cannot be blocked.");
        }
        
        task.setStatus(TaskStatus.BLOCKED);
        taskRepository.save(task);
        
        // If it was in progress, close the time log regardless of who blocked it
        timeLogRepository.findByTaskAndEndTimeIsNull(task)
            .ifPresent(timeLog -> {
                timeLog.setEndTime(LocalDateTime.now());
                timeLog.setDurationSeconds(Duration.between(timeLog.getStartTime(), timeLog.getEndTime()).getSeconds());
                timeLogRepository.save(timeLog);
            });

        logHistoryEvent("Блокування", task, worker);

        return task;
    }

    @Transactional
    public Task unblockTask(UUID taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        if (task.getStatus() != TaskStatus.BLOCKED) {
            throw new RuntimeException("Task is not blocked.");
        }
        task.setStatus(TaskStatus.PENDING);
        logHistoryEvent("Розблокування", task, null);
        return taskRepository.save(task);
    }

    @Transactional
    public Task reportMissingMaterials(UUID taskId, UUID materialId, UUID workerId) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        task.setMissingMaterials(true);
        String actionStr = "Брак матеріалів";
        if (materialId != null) {
            Optional<Material> optMat = materialRepository.findById(materialId);
            if (optMat.isPresent()) {
                task.getMissingMaterialsList().add(optMat.get());
                actionStr += ": " + optMat.get().getName();
            }
        }
        Worker worker = null;
        if (workerId != null) {
            worker = workerRepository.findById(workerId).orElse(null);
        }
        logHistoryEvent(actionStr, task, worker);
        return taskRepository.save(task);
    }

    @Transactional
    public Task resolveMissingMaterials(UUID taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        task.setMissingMaterials(false);
        task.getMissingMaterialsList().clear();
        return taskRepository.save(task);
    }

    @Transactional
    public Task reworkTask(UUID taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        
        if (task.getStatus() != TaskStatus.COMPLETED) {
            throw new RuntimeException("Only completed tasks can be reworked.");
        }
        
        task.setStatus(TaskStatus.PENDING);
        
        ProductInstance instance = task.getProductInstance();
        
        // Find if a next stage task was already created, if so, delete it
        Optional<Stage> nextStage = stageRepository.findFirstByDependsOnStage(task.getStage());
        if (nextStage.isEmpty()) {
            nextStage = stageRepository.findFirstByProductModelAndOrderIndexGreaterThanOrderByOrderIndexAsc(
                    instance.getProductModel(), task.getStage().getOrderIndex());
        }
        
        if (nextStage.isPresent()) {
            taskRepository.findByProductInstanceAndStage(instance, nextStage.get()).ifPresent(nextTask -> {
                timeLogRepository.deleteByTask(nextTask);
                historyEventRepository.deleteByTask(nextTask);
                taskRepository.delete(nextTask);
            });
        } else {
            // If there is no next stage, it means this was the final stage and the instance was marked COMPLETED
            if (instance.getStatus() == InstanceStatus.COMPLETED) {
                instance.setStatus(InstanceStatus.IN_PROGRESS);
                productInstanceRepository.save(instance);
            }
        }
        
        logHistoryEvent("Повторення (Rework)", task, null);
        
        // We do not delete TimeLogs to preserve the history of work, 
        // but it will allow the task to be started again.
        return taskRepository.save(task);
    }

    @Transactional
    public Task simulateTime(UUID taskId, UUID workerId, long secondsToAdd) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        
        if (task.getStatus() == TaskStatus.PENDING || task.getStatus() == TaskStatus.COMPLETED) {
            throw new RuntimeException("Cannot simulate time for PENDING or COMPLETED tasks.");
        }
        
        Worker worker = workerRepository.findById(workerId).orElseThrow(() -> new RuntimeException("Worker not found"));
        
        TimeLog simulatedLog = new TimeLog();
        simulatedLog.setTask(task);
        simulatedLog.setWorker(worker);
        simulatedLog.setStartTime(LocalDateTime.now().minusSeconds(secondsToAdd));
        simulatedLog.setEndTime(LocalDateTime.now());
        simulatedLog.setDurationSeconds(secondsToAdd);
        
        timeLogRepository.save(simulatedLog);
        
        return task;
    }

    @Transactional
    public Task assignTask(UUID taskId, UUID workerId) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        Worker worker = workerId != null ? workerRepository.findById(workerId).orElse(null) : null;
        task.setAssignedWorker(worker);
        logHistoryEvent("Призначення працівника", task, worker);
        return taskRepository.save(task);
    }
}
