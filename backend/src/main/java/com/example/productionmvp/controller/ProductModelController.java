package com.example.productionmvp.controller;

import com.example.productionmvp.model.ProductModel;
import com.example.productionmvp.model.Stage;
import com.example.productionmvp.repository.ProductModelRepository;
import com.example.productionmvp.repository.StageRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/models")
@CrossOrigin(origins = "*")
public class ProductModelController {

    private final ProductModelRepository productModelRepository;
    private final StageRepository stageRepository;

    public ProductModelController(ProductModelRepository productModelRepository, StageRepository stageRepository) {
        this.productModelRepository = productModelRepository;
        this.stageRepository = stageRepository;
    }

    @PostMapping
    public ResponseEntity<?> createProductModel(@RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        String description = payload.get("description");

        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Name is required"));
        }

        ProductModel model = new ProductModel();
        model.setName(name);
        model.setDescription(description);
        productModelRepository.save(model);

        Stage stage1 = new Stage();
        stage1.setName("Assembly");
        stage1.setProductModel(model);
        stage1.setOrderIndex(1);
        stageRepository.save(stage1);

        Stage stage2 = new Stage();
        stage2.setName("Testing");
        stage2.setProductModel(model);
        stage2.setOrderIndex(2);
        stage2.setDependsOnStage(stage1);
        stageRepository.save(stage2);

        return ResponseEntity.ok(model);
    }
}
