package com.example.productionmvp.controller;

import com.example.productionmvp.model.Operation;
import com.example.productionmvp.model.Worker;
import com.example.productionmvp.model.Task;
import com.example.productionmvp.repository.WorkerRepository;
import com.example.productionmvp.repository.TaskRepository;
import com.example.productionmvp.repository.SectionRepository;
import com.example.productionmvp.repository.OperationRepository;
import com.example.productionmvp.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;


@RestController
@RequestMapping("/api/workers")
@CrossOrigin(origins = "*")
public class WorkerController {

    private final WorkerRepository workerRepository;
    private final TaskRepository taskRepository;
    private final SectionRepository sectionRepository;
    private final OperationRepository operationRepository;
    private final AuthService authService;

    public WorkerController(WorkerRepository workerRepository, TaskRepository taskRepository,
                            SectionRepository sectionRepository, OperationRepository operationRepository,
                            AuthService authService) {
        this.workerRepository = workerRepository;
        this.taskRepository = taskRepository;
        this.sectionRepository = sectionRepository;
        this.operationRepository = operationRepository;
        this.authService = authService;
    }

    // Manager/Dispatcher/Admin only: the full Worker directory is reachable by any
    // authenticated role otherwise (verified live - a WORKER token could enumerate every
    // account including Manager/Admin), and nothing in worker.html's real, reachable UI paths
    // needs it - the handful of Services.Workers.getAll() call sites in worker.js live in dead
    // code (a leftover manager-dashboard-style view structure with no matching elements in
    // worker.html, so those code paths never execute in the shipped worker kiosk flow).
    // Dispatcher needs this too - ТЗ §16 has "призначення працівників" as one of their
    // exclusive actions, and manager.html's worker-select dropdowns are reachable to them.
    @PreAuthorize("hasRole('MANAGER') or hasRole('DISPATCHER') or hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<Worker>> getAllWorkers() {
        return ResponseEntity.ok(workerRepository.findAll());
    }

    // Was @RequestBody Worker directly - the create-worker form sends "pin" and "sectionId",
    // neither of which is an actual field on Worker (it has pinHash and a section relation), so
    // Jackson silently dropped both. A worker created through the real UI ended up with no PIN
    // at all and could never log in, and its section was always null regardless of what was
    // selected. A proper request DTO resolves both correctly instead of guessing field names.
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Worker> createWorker(@RequestBody com.example.productionmvp.dto.WorkerRequestDTO body) {
        if (body.getName() == null || body.getName().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        // Checked before the worker row is written, not after: this method isn't transactional,
        // so rejecting the PIN once the worker is already saved would leave behind an account
        // with no PIN that nobody can log into and the manager didn't know got created.
        if (body.getPin() != null && !body.getPin().isEmpty()) {
            authService.assertPinAvailable(body.getPin(), null);
        }
        Worker worker = new Worker();
        worker.setName(body.getName());
        worker.setRole(body.getRole());
        worker.setPosition(body.getPosition());
        if (body.getSystemRole() != null) worker.setSystemRole(body.getSystemRole());
        if (body.getSectionId() != null) {
            worker.setSection(sectionRepository.findById(body.getSectionId()).orElse(null));
        }
        if (body.getQualifiedOperationIds() != null) {
            Set<Operation> ops = new HashSet<>();
            for (UUID opId : body.getQualifiedOperationIds()) {
                operationRepository.findById(opId).ifPresent(ops::add);
            }
            worker.setQualifiedOperations(ops);
        }

        Worker savedWorker = workerRepository.save(worker);
        if (body.getPin() != null && !body.getPin().isEmpty()) {
            authService.setWorkerPin(savedWorker.getId(), body.getPin());
        }
        return ResponseEntity.ok(savedWorker);
    }

    @GetMapping("/{id}/tasks")
    public ResponseEntity<List<com.example.productionmvp.dto.TaskDTO>> getWorkerTasks(@PathVariable UUID id) {
        List<com.example.productionmvp.dto.TaskDTO> tasks = taskRepository.findByAssignedWorkerIdAndStatusIn(
                        id, 
                        List.of(com.example.productionmvp.model.TaskStatus.IN_PROGRESS, com.example.productionmvp.model.TaskStatus.PAUSED)
                ).stream()
                .map(com.example.productionmvp.dto.TaskDTO::new)
                .toList();
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<com.example.productionmvp.dto.TaskDTO>> getWorkerHistory(@PathVariable UUID id) {
        List<com.example.productionmvp.dto.TaskDTO> tasks = taskRepository.findByAssignedWorkerIdAndStatusIn(
                        id, 
                        List.of(com.example.productionmvp.model.TaskStatus.COMPLETED)
                ).stream()
                .map(com.example.productionmvp.dto.TaskDTO::new)
                .toList();
        return ResponseEntity.ok(tasks);
    }
}
