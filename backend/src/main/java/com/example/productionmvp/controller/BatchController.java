package com.example.productionmvp.controller;

import com.example.productionmvp.model.Batch;
import com.example.productionmvp.repository.BatchRepository;
import com.example.productionmvp.service.BatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/batches")
@CrossOrigin(origins = "*")
public class BatchController {
    private final BatchRepository batchRepository;
    private final BatchService batchService;

    public BatchController(BatchRepository batchRepository, BatchService batchService) {
        this.batchRepository = batchRepository;
        this.batchService = batchService;
    }

    @GetMapping
    public ResponseEntity<List<com.example.productionmvp.dto.BatchDTO>> getAllBatches() {
        return ResponseEntity.ok(batchRepository.findAll().stream()
                .map(com.example.productionmvp.dto.BatchDTO::new)
                .collect(java.util.stream.Collectors.toList()));
    }

    @GetMapping("/active")
    public ResponseEntity<List<com.example.productionmvp.dto.BatchDTO>> getActiveBatches() {
        return ResponseEntity.ok(batchService.findActiveBatches().stream()
                .map(com.example.productionmvp.dto.BatchDTO::new)
                .collect(java.util.stream.Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<com.example.productionmvp.dto.BatchDTO> getBatchById(@PathVariable UUID id) {
        return batchRepository.findById(id)
                .map(com.example.productionmvp.dto.BatchDTO::new)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('DISPATCHER') or hasRole('MANAGER') or hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<com.example.productionmvp.dto.BatchDTO> createBatch(@RequestBody com.example.productionmvp.dto.BatchRequestDTO body) {
        String number = body.getNumber() != null ? body.getNumber() : UUID.randomUUID().toString().substring(0, 8);
        
        UUID operationId = body.getOperationId();
        UUID workerId = body.getWorkerId();
        UUID sectionId = body.getSectionId();
        UUID postId = body.getPostId();
        
        String materialDetail = body.getMaterialDetail();
        int plannedQty = body.getQuantity() != null ? body.getQuantity() : (body.getPlannedQuantity() != null ? body.getPlannedQuantity() : 0);
        return ResponseEntity.ok(new com.example.productionmvp.dto.BatchDTO(batchService.createBatch(number, operationId, workerId, sectionId, postId, materialDetail, plannedQty)));
    }

    @PreAuthorize("hasRole('WORKER') or hasRole('ADMIN')")
    @PostMapping("/{id}/start")
    public ResponseEntity<com.example.productionmvp.dto.BatchDTO> startBatch(@PathVariable UUID id, @RequestBody com.example.productionmvp.dto.BatchRequestDTO body) {
        UUID workerId = body.getWorkerId();
        return ResponseEntity.ok(new com.example.productionmvp.dto.BatchDTO(batchService.startBatch(id, workerId)));
    }

    @PreAuthorize("hasRole('WORKER') or hasRole('ADMIN')")
    @PostMapping("/{id}/complete")
    public ResponseEntity<com.example.productionmvp.dto.BatchDTO> completeBatch(@PathVariable UUID id, @RequestBody com.example.productionmvp.dto.BatchRequestDTO body) {
        int actualQuantity = body.getActualQuantity() != null ? body.getActualQuantity() : (body.getQuantity() != null ? body.getQuantity() : 0);
        return ResponseEntity.ok(new com.example.productionmvp.dto.BatchDTO(batchService.completeBatch(id, actualQuantity)));
    }

    @PreAuthorize("hasRole('WORKER') or hasRole('DISPATCHER') or hasRole('MANAGER') or hasRole('ADMIN')")
    @PostMapping("/{id}/distribute")
    public ResponseEntity<com.example.productionmvp.dto.BatchDTO> distributeBatch(@PathVariable UUID id, @RequestBody com.example.productionmvp.dto.BatchDistributeRequestDTO body) {
        Map<UUID, Integer> distribution = body.getDistribution();
        if (distribution == null) {
            distribution = new java.util.HashMap<>();
        }
        return ResponseEntity.ok(new com.example.productionmvp.dto.BatchDTO(batchService.distributeBatch(id, distribution)));
    }
}
