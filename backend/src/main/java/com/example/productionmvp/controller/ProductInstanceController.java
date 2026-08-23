package com.example.productionmvp.controller;

import com.example.productionmvp.model.*;
import com.example.productionmvp.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final AssemblyInstanceRepository assemblyInstanceRepository;
    private final PalletRepository palletRepository;
    private final com.example.productionmvp.service.TaskExecutionService taskExecutionService;

    public ProductInstanceController(ProductModelRepository productModelRepository,
                                     ProductInstanceRepository productInstanceRepository,
                                     StageRepository stageRepository,
                                     TaskRepository taskRepository,
                                     WorkerRepository workerRepository,
                                     AssemblyInstanceRepository assemblyInstanceRepository,
                                     PalletRepository palletRepository,
                                     com.example.productionmvp.service.TaskExecutionService taskExecutionService) {
        this.productModelRepository = productModelRepository;
        this.productInstanceRepository = productInstanceRepository;
        this.stageRepository = stageRepository;
        this.taskRepository = taskRepository;
        this.workerRepository = workerRepository;
        this.assemblyInstanceRepository = assemblyInstanceRepository;
        this.palletRepository = palletRepository;
        this.taskExecutionService = taskExecutionService;
    }

    @GetMapping("/models")
    public ResponseEntity<List<ProductModel>> getProductModels() {
        return ResponseEntity.ok(productModelRepository.findAll());
    }

    @PostMapping("/start")
    @Transactional
    @PreAuthorize("hasRole('DISPATCHER') or hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<List<Task>> startProduction(@RequestBody com.example.productionmvp.dto.StartProductionRequestDTO body) {
        UUID modelId = body.getModelId();
        String serialNumber = body.getSerialNumber();
        UUID workerId = body.getWorkerId();

        if (modelId == null || serialNumber == null || serialNumber.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ProductModel model = productModelRepository.findById(modelId)
                .orElseThrow(() -> new com.example.productionmvp.exception.EntityNotFoundException("Model not found"));

        ProductInstance instance = new ProductInstance();
        instance.setProductModel(model);
        instance.setModelVersion(model.getVersion());
        instance.setSerialNumber(serialNumber);
        instance.setStatus(InstanceStatus.PLANNED);
        productInstanceRepository.save(instance);

        List<Task> createdTasks = new java.util.ArrayList<>();

        if (model.getAssemblies() != null && !model.getAssemblies().isEmpty()) {
            for (Assembly assembly : model.getAssemblies()) {
                AssemblyInstance asmInstance = new AssemblyInstance();
                asmInstance.setAssembly(assembly);
                asmInstance.setProductInstance(instance);
                asmInstance.setStatus(AssemblyInstanceStatus.PLANNED);
                asmInstance = assemblyInstanceRepository.save(asmInstance);

                List<Operation> initialOps = assembly.getOperations().stream()
                        .filter(op -> op.getDependsOnOperation() == null)
                        .toList();

                for (Operation firstOp : initialOps) {
                    Task task = taskExecutionService.createIndividualTask(
                            asmInstance.getId(), firstOp.getId(), workerId, 
                            firstOp.getPost() != null ? firstOp.getPost().getId() : null);
                    createdTasks.add(task);
                }
            }
        }
        
        return ResponseEntity.ok(createdTasks);
    }
    
    @GetMapping("/{id}/assemblies")
    public ResponseEntity<List<AssemblyInstance>> getAssemblyInstances(@PathVariable UUID id) {
        List<AssemblyInstance> assemblies = assemblyInstanceRepository.findByProductInstanceId(id);
        return ResponseEntity.ok(assemblies);
    }
    
    @GetMapping("/{id}/pallets")
    public ResponseEntity<List<Pallet>> getPallets(@PathVariable UUID id) {
        List<Pallet> pallets = palletRepository.findByOwnerProductId(id);
        return ResponseEntity.ok(pallets);
    }
}
