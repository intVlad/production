package com.example.productionmvp.controller;

import com.example.productionmvp.model.ProductModel;
import com.example.productionmvp.model.Assembly;
import com.example.productionmvp.repository.ProductModelRepository;
import com.example.productionmvp.repository.AssemblyRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/models")
@CrossOrigin(origins = "*")
public class ProductModelController {

    private final ProductModelRepository productModelRepository;
    private final AssemblyRepository assemblyRepository;

    public ProductModelController(ProductModelRepository productModelRepository, AssemblyRepository assemblyRepository) {
        this.productModelRepository = productModelRepository;
        this.assemblyRepository = assemblyRepository;
    }
    
    @PreAuthorize("hasAnyRole('MANAGER', 'DISPATCHER', 'WORKER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<List<com.example.productionmvp.dto.ProductModelDTO>> getAllModels() {
        List<com.example.productionmvp.dto.ProductModelDTO> activeModels = productModelRepository.findByArchivedAtIsNull().stream()
                .map(com.example.productionmvp.dto.ProductModelDTO::new)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(activeModels);
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'DISPATCHER', 'WORKER', 'ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<com.example.productionmvp.dto.ProductModelDTO> getModelById(@PathVariable UUID id) {
        return productModelRepository.findById(id)
                .map(com.example.productionmvp.dto.ProductModelDTO::new)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('DISPATCHER') or hasRole('MANAGER') or hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<?> createProductModel(@RequestBody com.example.productionmvp.dto.ProductModelRequestDTO payload) {
        String name = payload.getName();
        String description = payload.getDescription();
        String version = payload.getVersion();

        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Name is required"));
        }

        ProductModel model = new ProductModel();
        model.setName(name);
        model.setDescription(description);
        model.setSpecification(payload.getSpecification());
        model.setRequiredEquipment(payload.getRequiredEquipment());
        model.setRequiredTools(payload.getRequiredTools());
        if (version != null) {
            model.setVersion(version);
        } else {
            model.setVersion("v.1.0");
        }
        productModelRepository.save(model);

        return ResponseEntity.ok(new com.example.productionmvp.dto.ProductModelDTO(model));
    }

    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/{id}")
    public ResponseEntity<?> editProductModel(@PathVariable UUID id, @RequestBody com.example.productionmvp.dto.ProductModelRequestDTO payload) {
        ProductModel oldModel = productModelRepository.findById(id).orElse(null);
        if (oldModel == null) {
            return ResponseEntity.notFound().build();
        }

        // Archive old model
        oldModel.setArchivedAt(LocalDateTime.now());
        productModelRepository.save(oldModel);

        // Create new version
        ProductModel newModel = new ProductModel();
        newModel.setName(payload.getName() != null ? payload.getName() : oldModel.getName());
        newModel.setDescription(payload.getDescription() != null ? payload.getDescription() : oldModel.getDescription());
        
        // Bump version logic
        String oldVersion = oldModel.getVersion();
        if (payload.getVersion() != null && !payload.getVersion().isEmpty()) {
            newModel.setVersion(payload.getVersion());
        } else {
            if (oldVersion != null && oldVersion.contains(".")) {
                try {
                    String[] parts = oldVersion.replace("v.", "").replace("v", "").split("\\.");
                    int lastPart = Integer.parseInt(parts[parts.length - 1]);
                    parts[parts.length - 1] = String.valueOf(lastPart + 1);
                    newModel.setVersion("v." + String.join(".", parts));
                } catch (NumberFormatException e) {
                    newModel.setVersion(oldVersion + ".1");
                }
            } else {
                newModel.setVersion(oldVersion + ".1");
            }
        }
        
        newModel.setSpecification(payload.getSpecification() != null ? payload.getSpecification() : oldModel.getSpecification());
        newModel.setRequiredEquipment(payload.getRequiredEquipment() != null ? payload.getRequiredEquipment() : oldModel.getRequiredEquipment());
        newModel.setRequiredTools(payload.getRequiredTools() != null ? payload.getRequiredTools() : oldModel.getRequiredTools());
        
        // Clone assemblies and operations
        java.util.List<Assembly> clonedAssemblies = new java.util.ArrayList<>();
        java.util.Map<java.util.UUID, com.example.productionmvp.model.Operation> operationMapping = new java.util.HashMap<>();
        java.util.List<com.example.productionmvp.model.Operation> allNewOperations = new java.util.ArrayList<>();

        if (oldModel.getAssemblies() != null) {
            for (Assembly oldAssembly : oldModel.getAssemblies()) {
                Assembly newAssembly = new Assembly();
                newAssembly.setCode(oldAssembly.getCode());
                newAssembly.setName(oldAssembly.getName());
                newAssembly.setCategory(oldAssembly.getCategory());
                newAssembly.setParts(oldAssembly.getParts());
                newAssembly.setNormativeTimeMinutes(oldAssembly.getNormativeTimeMinutes());
                newAssembly.setProductModel(newModel);
                
                java.util.List<com.example.productionmvp.model.Operation> clonedOperations = new java.util.ArrayList<>();
                if (oldAssembly.getOperations() != null) {
                    for (com.example.productionmvp.model.Operation oldOp : oldAssembly.getOperations()) {
                        com.example.productionmvp.model.Operation newOp = new com.example.productionmvp.model.Operation();
                        newOp.setName(oldOp.getName());
                        newOp.setDescription(oldOp.getDescription());
                        newOp.setNormativeTimeMinutes(oldOp.getNormativeTimeMinutes());
                        newOp.setRequiredQualification(oldOp.getRequiredQualification());
                        newOp.setSection(oldOp.getSection());
                        newOp.setPost(oldOp.getPost());
                        newOp.setEquipment(oldOp.getEquipment());
                        newOp.setTools(oldOp.getTools());
                        newOp.setMaterialQuantityPerUnit(oldOp.getMaterialQuantityPerUnit());
                        newOp.setOrderIndex(oldOp.getOrderIndex());
                        newOp.setType(oldOp.getType());
                        newOp.setProductModel(newModel);
                        newOp.setAssembly(newAssembly);
                        if (oldOp.getRequiredMaterials() != null) {
                            newOp.setRequiredMaterials(new java.util.HashSet<>(oldOp.getRequiredMaterials()));
                        }
                        clonedOperations.add(newOp);
                        allNewOperations.add(newOp);
                        if (oldOp.getId() != null) {
                            operationMapping.put(oldOp.getId(), newOp);
                        }
                        
                        // We will set dependsOnOperation in the second pass to ensure it maps to the newly created Operation
                        // Temporarily store the old dependency reference in the newOp to resolve it later
                        newOp.setDependsOnOperation(oldOp.getDependsOnOperation());
                    }
                }
                newAssembly.setOperations(clonedOperations);
                clonedAssemblies.add(newAssembly);
            }
        }
        
        // Second pass: remap dependencies
        for (com.example.productionmvp.model.Operation newOp : allNewOperations) {
            if (newOp.getDependsOnOperation() != null && newOp.getDependsOnOperation().getId() != null) {
                com.example.productionmvp.model.Operation mappedDep = operationMapping.get(newOp.getDependsOnOperation().getId());
                if (mappedDep != null) {
                    newOp.setDependsOnOperation(mappedDep);
                } else {
                    newOp.setDependsOnOperation(null); // Dependency was not found in the cloned set
                }
            }
        }

        newModel.setAssemblies(clonedAssemblies);
        productModelRepository.save(newModel);
        
        return ResponseEntity.ok(new com.example.productionmvp.dto.ProductModelDTO(newModel));
    }
    
    @PreAuthorize("hasAnyRole('MANAGER', 'DISPATCHER', 'WORKER', 'ADMIN')")
    @GetMapping("/{id}/assemblies")
    public ResponseEntity<List<Assembly>> getAssembliesForModel(@PathVariable UUID id) {
        List<Assembly> assemblies = assemblyRepository.findByProductModelId(id);
        return ResponseEntity.ok(assemblies);
    }
}
