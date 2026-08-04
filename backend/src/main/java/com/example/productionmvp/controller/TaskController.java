package com.example.productionmvp.controller;

import com.example.productionmvp.model.Task;
import com.example.productionmvp.repository.TaskRepository;
import com.example.productionmvp.service.TaskExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*") // Allow frontend to connect
public class TaskController {

    private final TaskExecutionService taskExecutionService;
    private final TaskRepository taskRepository;

    public TaskController(TaskExecutionService taskExecutionService, TaskRepository taskRepository) {
        this.taskExecutionService = taskExecutionService;
        this.taskRepository = taskRepository;
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<Task> getTask(@PathVariable UUID taskId) {
        return taskRepository.findById(taskId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<Task> searchTaskBySerialNumber(@RequestParam String serialNumber) {
        List<com.example.productionmvp.model.TaskStatus> activeStatuses = List.of(
            com.example.productionmvp.model.TaskStatus.PENDING, 
            com.example.productionmvp.model.TaskStatus.IN_PROGRESS,
            com.example.productionmvp.model.TaskStatus.PAUSED,
            com.example.productionmvp.model.TaskStatus.BLOCKED
        );
        List<Task> tasks = taskRepository.findByProductInstanceSerialNumberAndStatusIn(serialNumber, activeStatuses);
        if (tasks.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // Return the first active task for this serial number
        return ResponseEntity.ok(tasks.get(0));
    }

    @GetMapping("/worker/{workerId}")
    public ResponseEntity<List<Task>> getWorkerTasks(@PathVariable UUID workerId) {
        List<com.example.productionmvp.model.TaskStatus> activeStatuses = List.of(
            com.example.productionmvp.model.TaskStatus.PENDING, 
            com.example.productionmvp.model.TaskStatus.IN_PROGRESS,
            com.example.productionmvp.model.TaskStatus.PAUSED,
            com.example.productionmvp.model.TaskStatus.BLOCKED
        );
        return ResponseEntity.ok(taskRepository.findByAssignedWorkerIdAndStatusIn(workerId, activeStatuses));
    }

    @GetMapping("/blocked")
    public ResponseEntity<List<Task>> getBlockedTasks() {
        return ResponseEntity.ok(taskRepository.findByStatus(com.example.productionmvp.model.TaskStatus.BLOCKED));
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<Task>> getOverdueTasks() {
        List<com.example.productionmvp.model.TaskStatus> activeStatuses = List.of(
            com.example.productionmvp.model.TaskStatus.PENDING, 
            com.example.productionmvp.model.TaskStatus.IN_PROGRESS,
            com.example.productionmvp.model.TaskStatus.PAUSED
        );
        List<Task> incomplete = taskRepository.findByStatusIn(activeStatuses);
        List<Task> overdue = incomplete.stream()
            .filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(java.time.LocalDateTime.now()))
            .toList();
        return ResponseEntity.ok(overdue);
    }

    @PostMapping("/{taskId}/start")
    public ResponseEntity<Task> startTask(@PathVariable UUID taskId, @RequestBody Map<String, String> body) {
        if (!body.containsKey("workerId") || body.get("workerId").isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID workerId = UUID.fromString(body.get("workerId"));
        Task startedTask = taskExecutionService.startTask(taskId, workerId);
        return ResponseEntity.ok(startedTask);
    }

    @PostMapping("/{taskId}/pause")
    public ResponseEntity<Task> pauseTask(@PathVariable UUID taskId, @RequestBody Map<String, String> body) {
        if (!body.containsKey("workerId") || body.get("workerId").isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID workerId = UUID.fromString(body.get("workerId"));
        Task pausedTask = taskExecutionService.pauseTask(taskId, workerId);
        return ResponseEntity.ok(pausedTask);
    }

    @PostMapping("/{taskId}/complete")
    public ResponseEntity<Task> completeTask(@PathVariable UUID taskId, @RequestBody Map<String, String> body) {
        if (!body.containsKey("workerId") || body.get("workerId").isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID workerId = UUID.fromString(body.get("workerId"));
        Task completedTask = taskExecutionService.completeTask(taskId, workerId);
        return ResponseEntity.ok(completedTask);
    }

    @PostMapping("/{taskId}/block")
    public ResponseEntity<Task> blockTask(@PathVariable UUID taskId, @RequestBody Map<String, String> body) {
        if (!body.containsKey("workerId") || body.get("workerId").isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID workerId = UUID.fromString(body.get("workerId"));
        Task blockedTask = taskExecutionService.blockTask(taskId, workerId);
        return ResponseEntity.ok(blockedTask);
    }

    @PostMapping("/{taskId}/missing-materials")
    public ResponseEntity<Task> reportMissingMaterials(@PathVariable UUID taskId, @RequestBody Map<String, String> body) {
        String materialIdStr = body.get("materialId");
        UUID materialId = materialIdStr != null && !materialIdStr.isEmpty() ? UUID.fromString(materialIdStr) : null;
        String workerIdStr = body.get("workerId");
        UUID workerId = workerIdStr != null && !workerIdStr.isEmpty() ? UUID.fromString(workerIdStr) : null;
        Task updatedTask = taskExecutionService.reportMissingMaterials(taskId, materialId, workerId);
        return ResponseEntity.ok(updatedTask);
    }

    @PostMapping("/{taskId}/rework")
    public ResponseEntity<Task> reworkTask(@PathVariable UUID taskId, @RequestBody Map<String, String> body) {
        Task reworkedTask = taskExecutionService.reworkTask(taskId);
        return ResponseEntity.ok(reworkedTask);
    }

    @PostMapping("/{taskId}/unblock")
    public ResponseEntity<Task> unblockTask(@PathVariable UUID taskId, @RequestBody Map<String, String> body) {
        Task unblockedTask = taskExecutionService.unblockTask(taskId);
        return ResponseEntity.ok(unblockedTask);
    }

    @PostMapping("/{taskId}/materials-resolved")
    public ResponseEntity<Task> resolveMissingMaterials(@PathVariable UUID taskId, @RequestBody Map<String, String> body) {
        Task resolvedTask = taskExecutionService.resolveMissingMaterials(taskId);
        return ResponseEntity.ok(resolvedTask);
    }

    @PostMapping("/{taskId}/simulate-time")
    public ResponseEntity<Task> simulateTime(@PathVariable UUID taskId, @RequestBody Map<String, String> body) {
        if (!body.containsKey("workerId") || body.get("workerId").isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID workerId = UUID.fromString(body.get("workerId"));
        long seconds = Long.parseLong(body.getOrDefault("seconds", "3600"));
        Task updatedTask = taskExecutionService.simulateTime(taskId, workerId, seconds);
        return ResponseEntity.ok(updatedTask);
    }

    @PostMapping("/{taskId}/assign")
    public ResponseEntity<Task> assignTask(@PathVariable UUID taskId, @RequestBody Map<String, String> body) {
        String workerIdStr = body.get("workerId");
        UUID workerId = workerIdStr != null && !workerIdStr.isEmpty() ? UUID.fromString(workerIdStr) : null;
        Task assignedTask = taskExecutionService.assignTask(taskId, workerId);
        return ResponseEntity.ok(assignedTask);
    }
}
