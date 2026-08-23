package com.example.productionmvp.controller;

import com.example.productionmvp.model.Assembly;
import com.example.productionmvp.model.Material;
import com.example.productionmvp.model.Operation;
import com.example.productionmvp.model.ProductModel;
import com.example.productionmvp.repository.AssemblyRepository;
import com.example.productionmvp.repository.OperationRepository;
import com.example.productionmvp.repository.ProductModelRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.productionmvp.repository.SectionRepository;
import com.example.productionmvp.repository.PostRepository;
import com.example.productionmvp.repository.MaterialRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/assemblies")
@CrossOrigin(origins = "*")
public class AssemblyController {
    private final AssemblyRepository assemblyRepository;
    private final OperationRepository operationRepository;
    private final ProductModelRepository productModelRepository;
    private final SectionRepository sectionRepository;
    private final PostRepository postRepository;
    private final MaterialRepository materialRepository;

    public AssemblyController(AssemblyRepository assemblyRepository,
                              OperationRepository operationRepository,
                              ProductModelRepository productModelRepository,
                              SectionRepository sectionRepository,
                              PostRepository postRepository,
                              MaterialRepository materialRepository) {
        this.assemblyRepository = assemblyRepository;
        this.operationRepository = operationRepository;
        this.productModelRepository = productModelRepository;
        this.sectionRepository = sectionRepository;
        this.postRepository = postRepository;
        this.materialRepository = materialRepository;
    }

    @GetMapping
    public ResponseEntity<List<Assembly>> getAllAssemblies() {
        return ResponseEntity.ok(assemblyRepository.findAll());
    }

    @GetMapping("/model/{modelId}")
    public ResponseEntity<List<Assembly>> getAssembliesByModel(@PathVariable UUID modelId) {
        List<Assembly> assemblies = assemblyRepository.findByProductModelId(modelId);
        return ResponseEntity.ok(assemblies);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Assembly> getAssemblyById(@PathVariable UUID id) {
        return assemblyRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<Assembly> createAssembly(@RequestBody com.example.productionmvp.dto.AssemblyRequestDTO body) {
        UUID productModelId = body.getProductModelId();
        ProductModel model = productModelRepository.findById(productModelId).orElseThrow(() -> new com.example.productionmvp.exception.EntityNotFoundException("Модель виробу не знайдено"));
        
        Assembly assembly = new Assembly();
        assembly.setProductModel(model);
        if (body.getCode() != null) assembly.setCode(body.getCode());
        if (body.getName() != null) assembly.setName(body.getName());
        if (body.getCategory() != null) assembly.setCategory(body.getCategory());
        if (body.getParts() != null) assembly.setParts(body.getParts());
        if (body.getNormativeTimeMinutes() != null) assembly.setNormativeTimeMinutes(body.getNormativeTimeMinutes());
        
        return ResponseEntity.ok(assemblyRepository.save(assembly));
    }

    @GetMapping("/{id}/operations")
    public ResponseEntity<List<Operation>> getOperationsForAssembly(@PathVariable UUID id) {
        List<Operation> ops = operationRepository.findByAssemblyIdOrderByOrderIndexAsc(id);
        return ResponseEntity.ok(ops);
    }

    @PostMapping("/{id}/operations")
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<Operation> addOperationToAssembly(@PathVariable UUID id, @RequestBody com.example.productionmvp.dto.OperationRequestDTO body) {
        Assembly assembly = assemblyRepository.findById(id).orElseThrow(() -> new com.example.productionmvp.exception.EntityNotFoundException("Вузол не знайдено"));
        Operation op = new Operation();
        op.setAssembly(assembly);
        if (body.getName() != null) op.setName(body.getName());
        if (body.getDescription() != null) op.setDescription(body.getDescription());
        if (body.getNormativeTimeMinutes() != null) op.setNormativeTimeMinutes(body.getNormativeTimeMinutes());
        if (body.getOrderIndex() != null) op.setOrderIndex(body.getOrderIndex());
        if (body.getType() != null) op.setType(body.getType());
        
        if (body.getSectionId() != null) {
            op.setSection(sectionRepository.findById(body.getSectionId()).orElse(null));
        }
        if (body.getPostId() != null) {
            op.setPost(postRepository.findById(body.getPostId()).orElse(null));
        }
        if (body.getEquipment() != null) op.setEquipment(body.getEquipment());
        if (body.getTools() != null) op.setTools(body.getTools());
        if (body.getRequiredQualification() != null) op.setRequiredQualification(body.getRequiredQualification());
        if (body.getDependsOnOperationId() != null) {
            op.setDependsOnOperation(operationRepository.findById(body.getDependsOnOperationId()).orElse(null));
        }
        if (body.getMaterialId() != null) {
            materialRepository.findById(body.getMaterialId()).ifPresent(material -> {
                Set<Material> materials = new HashSet<>();
                materials.add(material);
                op.setRequiredMaterials(materials);
            });
            if (body.getMaterialQuantityPerUnit() != null) {
                op.setMaterialQuantityPerUnit(body.getMaterialQuantityPerUnit());
            }
        }

        return ResponseEntity.ok(operationRepository.save(op));
    }
}
