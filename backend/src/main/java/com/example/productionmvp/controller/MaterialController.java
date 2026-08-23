package com.example.productionmvp.controller;

import com.example.productionmvp.model.Material;
import com.example.productionmvp.repository.MaterialRepository;
import com.example.productionmvp.service.MaterialService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/materials")
@CrossOrigin(origins = "*")
public class MaterialController {
    private final MaterialRepository materialRepository;
    private final MaterialService materialService;

    public MaterialController(MaterialRepository materialRepository, MaterialService materialService) {
        this.materialRepository = materialRepository;
        this.materialService = materialService;
    }

    @GetMapping
    public ResponseEntity<List<Material>> getAllMaterials() {
        return ResponseEntity.ok(materialRepository.findAll());
    }

    @GetMapping("/series/{seriesId}/requirements")
    public ResponseEntity<List<Map<String, Object>>> getRequirements(@PathVariable UUID seriesId) {
        return ResponseEntity.ok(materialService.calculateSeriesRequirements(seriesId));
    }

    @GetMapping("/deficits")
    public ResponseEntity<List<Material>> getDeficits() {
        List<Material> deficits = materialRepository.findAll().stream()
                .filter(m -> m.getAvailableStock() < m.getMinimumStock())
                .collect(Collectors.toList());
        return ResponseEntity.ok(deficits);
    }

    @PostMapping("/{id}/update-stock")
    @PreAuthorize("hasRole('MANAGER') or hasRole('SUPPLIER') or hasRole('ADMIN')")
    public ResponseEntity<Material> updateStock(@PathVariable UUID id, @RequestBody com.example.productionmvp.dto.UpdateStockRequestDTO body) {
        Material material = materialRepository.findById(id).orElseThrow(() -> new com.example.productionmvp.exception.EntityNotFoundException("Матеріал не знайдено"));
        Double availableStock = body.getAvailableStock();
        // A missing or negative value would otherwise be stored as-is, leaving the warehouse
        // holding a null or negative stock figure that every later calculation reads.
        if (availableStock == null || availableStock < 0) {
            throw new IllegalArgumentException("Залишок на складі має бути числом не менше 0.");
        }
        material.setAvailableStock(availableStock);
        Material saved = materialRepository.save(material);
        materialService.updateSupplyStatus(saved.getId());
        return ResponseEntity.ok(materialRepository.findById(saved.getId()).orElse(saved));
    }
}
