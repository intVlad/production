package com.example.productionmvp.controller;

import com.example.productionmvp.model.*;
import com.example.productionmvp.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductInstanceController {

    private final ProductModelRepository productModelRepository;
    private final ProductInstanceRepository productInstanceRepository;
    private final StageRepository stageRepository;
    private final TaskRepository taskRepository;
    private final WorkerRepository workerRepository;

    public ProductInstanceController(ProductModelRepository productModelRepository,
                                     ProductInstanceRepository productInstanceRepository,
                                     StageRepository stageRepository,
                                     TaskRepository taskRepository,
                                     WorkerRepository workerRepository) {
        this.productModelRepository = productModelRepository;
        this.productInstanceRepository = productInstanceRepository;
        this.stageRepository = stageRepository;
        this.taskRepository = taskRepository;
        this.workerRepository = workerRepository;
    }

    @GetMapping("/models")
    public ResponseEntity<List<ProductModel>> getProductModels() {
        return ResponseEntity.ok(productModelRepository.findAll());
    }

    @PostMapping("/start")
    @Transactional
    public ResponseEntity<Task> startProduction(@RequestBody Map<String, String> body) {
        String modelIdStr = body.get("modelId");
        String serialNumber = body.get("serialNumber");
        String workerIdStr = body.get("workerId");

        if (modelIdStr == null || serialNumber == null || serialNumber.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ProductModel model = productModelRepository.findById(UUID.fromString(modelIdStr))
                .orElseThrow(() -> new RuntimeException("Model not found"));

        ProductInstance instance = new ProductInstance();
        instance.setProductModel(model);
        instance.setSerialNumber(serialNumber);
        instance.setStatus(InstanceStatus.PENDING);
        productInstanceRepository.save(instance);

        Optional<Stage> firstStage = stageRepository.findByProductModelAndOrderIndex(model, 1);
        if (firstStage.isEmpty()) {
            throw new RuntimeException("No stages defined for this product model");
        }

        Task firstTask = new Task();
        firstTask.setProductInstance(instance);
        firstTask.setStage(firstStage.get());
        firstTask.setStatus(TaskStatus.PENDING);
        firstTask.setCreatedAt(LocalDateTime.now());
        firstTask.setDueDate(LocalDateTime.now().plusDays(1));
        
        if (workerIdStr != null && !workerIdStr.trim().isEmpty()) {
            Worker worker = workerRepository.findById(UUID.fromString(workerIdStr)).orElse(null);
            firstTask.setAssignedWorker(worker);
        }

        Task savedTask = taskRepository.save(firstTask);
        return ResponseEntity.ok(savedTask);
    }
}
