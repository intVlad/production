package com.example.productionmvp.controller;

import com.example.productionmvp.model.Worker;
import com.example.productionmvp.repository.WorkerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workers")
@CrossOrigin(origins = "*")
public class WorkerController {

    private final WorkerRepository workerRepository;

    public WorkerController(WorkerRepository workerRepository) {
        this.workerRepository = workerRepository;
    }

    @GetMapping
    public ResponseEntity<List<Worker>> getAllWorkers() {
        return ResponseEntity.ok(workerRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Worker> createWorker(@RequestBody Worker worker) {
        if (worker.getName() == null || worker.getName().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Worker savedWorker = workerRepository.save(worker);
        return ResponseEntity.ok(savedWorker);
    }
}
