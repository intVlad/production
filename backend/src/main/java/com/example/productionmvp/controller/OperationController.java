package com.example.productionmvp.controller;

import com.example.productionmvp.model.Operation;
import com.example.productionmvp.repository.OperationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/operations")
@CrossOrigin(origins = "*")
public class OperationController {
    private final OperationRepository operationRepository;

    public OperationController(OperationRepository operationRepository) {
        this.operationRepository = operationRepository;
    }

    @GetMapping
    public ResponseEntity<List<Operation>> getAllOperations() {
        return ResponseEntity.ok(operationRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Operation> getOperationById(@PathVariable UUID id) {
        return operationRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
