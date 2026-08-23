package com.example.productionmvp.controller;

import com.example.productionmvp.model.Task;
import com.example.productionmvp.dto.TaskDTO;
import com.example.productionmvp.dto.TaskActionRequestDTO;
import com.example.productionmvp.repository.TaskRepository;
import com.example.productionmvp.service.PrioritizationService;
import com.example.productionmvp.service.TaskExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
public class TaskController {
    private final TaskRepository taskRepository;
    private final TaskExecutionService taskExecutionService;
    private final PrioritizationService prioritizationService;

    public TaskController(TaskRepository taskRepository, TaskExecutionService taskExecutionService, PrioritizationService prioritizationService) {
        this.taskRepository = taskRepository;
        this.taskExecutionService = taskExecutionService;
        this.prioritizationService = prioritizationService;
    }

    // The JWT principal's "username" is the worker's own UUID (see UserDetailsServiceImpl) -
    // this is the one source of truth for "who is actually making this call." Every action
    // below used to trust a workerId supplied in the request body instead, which let any
    // authenticated worker attribute a start/pause/complete/damage/cancel to a colleague (or a
    // manager) just by passing their UUID - breaking the audit trail ТЗ §12.2 requires.
    private UUID actingWorkerId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    @GetMapping
    public ResponseEntity<List<TaskDTO>> getAllTasks() {
        return ResponseEntity.ok(taskRepository.findAll().stream()
                .map(TaskDTO::new)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDTO> getTaskById(@PathVariable UUID id) {
        return taskRepository.findById(id)
                .map(TaskDTO::new)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ТЗ §16: виконання робочих операцій — виключна дія працівника; керівник/диспетчер
    // не повинні мати змогу "виконувати" завдання за оператора.
    @PreAuthorize("hasRole('WORKER') or hasRole('ADMIN')")
    @PostMapping("/{taskId}/start")
    public ResponseEntity<TaskDTO> startTask(@PathVariable UUID taskId, Authentication authentication) {
        return ResponseEntity.ok(new TaskDTO(taskExecutionService.startTask(taskId, actingWorkerId(authentication))));
    }

    @PreAuthorize("hasRole('WORKER') or hasRole('ADMIN')")
    @PostMapping("/{taskId}/pause")
    public ResponseEntity<TaskDTO> pauseTask(@PathVariable UUID taskId, Authentication authentication) {
        return ResponseEntity.ok(new TaskDTO(taskExecutionService.pauseTask(taskId, actingWorkerId(authentication))));
    }

    @PreAuthorize("hasRole('WORKER') or hasRole('ADMIN')")
    @PostMapping("/{taskId}/resume")
    public ResponseEntity<TaskDTO> resumeTask(@PathVariable UUID taskId, Authentication authentication) {
        return ResponseEntity.ok(new TaskDTO(taskExecutionService.resumeTask(taskId, actingWorkerId(authentication))));
    }

    @PreAuthorize("hasRole('WORKER') or hasRole('ADMIN')")
    @PostMapping("/{taskId}/complete")
    public ResponseEntity<TaskDTO> completeTask(@PathVariable UUID taskId, Authentication authentication) {
        return ResponseEntity.ok(new TaskDTO(taskExecutionService.completeTask(taskId, actingWorkerId(authentication))));
    }

    @PreAuthorize("hasRole('WORKER') or hasRole('ADMIN')")
    @PostMapping("/{taskId}/damage")
    public ResponseEntity<TaskDTO> markDamaged(@PathVariable UUID taskId, @RequestBody TaskActionRequestDTO body, Authentication authentication) {
        if (body == null || body.getReason() == null) {
             return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(new TaskDTO(taskExecutionService.markDamaged(
                taskId, actingWorkerId(authentication), body.getReason(), body.getResolution())));
    }

    @PreAuthorize("hasRole('WORKER') or hasRole('DISPATCHER') or hasRole('MANAGER') or hasRole('ADMIN')")
    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<TaskDTO> cancelTask(@PathVariable UUID taskId, Authentication authentication) {
        return ResponseEntity.ok(new TaskDTO(taskExecutionService.cancelTask(taskId, actingWorkerId(authentication))));
    }

    // Manager-only undo for an accidentally completed task - see TaskExecutionService.reopenTask
    // for why this isn't offered to workers as self-service.
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    @PostMapping("/{taskId}/reopen")
    public ResponseEntity<TaskDTO> reopenTask(@PathVariable UUID taskId, Authentication authentication) {
        return ResponseEntity.ok(new TaskDTO(taskExecutionService.reopenTask(taskId, actingWorkerId(authentication))));
    }

    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    @PostMapping("/{taskId}/urgent")
    public ResponseEntity<TaskDTO> setUrgentPriority(@PathVariable UUID taskId) {
        return ResponseEntity.ok(new TaskDTO(prioritizationService.setUrgentPriority(taskId)));
    }

    @GetMapping("/post/{postId}/available")
    public ResponseEntity<List<TaskDTO>> getAvailableTasksForPost(@PathVariable UUID postId, @RequestParam UUID workerId) {
        return ResponseEntity.ok(prioritizationService.getAvailableTasksForPost(postId, workerId).stream()
                .map(TaskDTO::new)
                .collect(Collectors.toList()));
    }

    @GetMapping("/assembly-instance/{assemblyInstanceId}")
    public ResponseEntity<List<TaskDTO>> getTasksForAssemblyInstance(@PathVariable UUID assemblyInstanceId) {
        List<TaskDTO> tasks = taskRepository.findByAssemblyInstanceId(assemblyInstanceId).stream()
                .map(TaskDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(tasks);
    }
}
