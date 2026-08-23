package com.example.productionmvp.controller;

import com.example.productionmvp.model.*;
import com.example.productionmvp.repository.*;
import com.example.productionmvp.service.TaskExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/mock")
@CrossOrigin(origins = "*")
public class MockDataController {

    private final SeriesRepository seriesRepository;
    private final ProductModelRepository productModelRepository;
    private final ProductInstanceRepository productInstanceRepository;
    private final AssemblyInstanceRepository assemblyInstanceRepository;
    private final PalletRepository palletRepository;
    private final WorkerRepository workerRepository;
    private final TaskExecutionService taskExecutionService;

    public MockDataController(SeriesRepository seriesRepository,
                              ProductModelRepository productModelRepository,
                              ProductInstanceRepository productInstanceRepository,
                              AssemblyInstanceRepository assemblyInstanceRepository,
                              PalletRepository palletRepository,
                              WorkerRepository workerRepository,
                              TaskExecutionService taskExecutionService) {
        this.seriesRepository = seriesRepository;
        this.productModelRepository = productModelRepository;
        this.productInstanceRepository = productInstanceRepository;
        this.assemblyInstanceRepository = assemblyInstanceRepository;
        this.palletRepository = palletRepository;
        this.workerRepository = workerRepository;
        this.taskExecutionService = taskExecutionService;
    }

    // Dev/demo helper, not a real product feature (nothing in the frontend calls this) - it
    // spawns a fresh Worker with a hardcoded PIN "0000" plus a whole Series/Product/Pallet/Task
    // tree on every call, so it needs to be off-limits to a plain WORKER session, not just
    // "authenticated with any role."
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/workflow")
    @Transactional
    public ResponseEntity<Map<String, Object>> generateMockWorkflow() {
        Map<String, Object> response = new HashMap<>();

        // 1. Get existing Product Model (from DataSeeder)
        ProductModel model = productModelRepository.findAll().stream()
                .filter(m -> m.getAssemblies() != null && !m.getAssemblies().isEmpty())
                .findFirst().orElse(null);
        if (model == null) {
            response.put("error", "No ProductModel found. Run DataSeeder first.");
            return ResponseEntity.badRequest().body(response);
        }

        // 2. Create a test Worker
        Worker mockWorker = new Worker();
        mockWorker.setName("Тестовий Працівник (Мок)");
        mockWorker.setRole("Універсал");
        mockWorker.setPosition("Тестувальник");
        mockWorker.setSystemRole(SystemRole.WORKER);
        mockWorker.setPinHash(new BCryptPasswordEncoder().encode("0000"));
        mockWorker = workerRepository.save(mockWorker);

        // 3. Create a Series
        Series series = new Series();
        series.setNumber("MOCK-SERIES-" + System.currentTimeMillis());
        series.setProductModel(model);
        series.setStatus(SeriesStatus.IN_PRODUCTION);
        // Unset (null) Integer unboxes to NPE in SeriesService.getSeriesProgress - the real
        // create-series flow always defaults this to 0, but this mock endpoint never set it.
        series.setPlannedQuantity(1);
        series = seriesRepository.save(series);

        // 4. Create Product Instance
        ProductInstance instance = new ProductInstance();
        instance.setProductModel(model);
        instance.setSeries(series);
        instance.setSerialNumber("SN-" + System.currentTimeMillis());
        instance.setStatus(InstanceStatus.PLANNED);
        instance = productInstanceRepository.save(instance);

        // 5. Create Pallet
        Pallet pallet = new Pallet();
        pallet.setQrCode("MOCK-PALLET-" + System.currentTimeMillis());
        pallet.setStatus(PalletStatus.ASSEMBLING);
        pallet.setOwnerProduct(instance);
        pallet = palletRepository.save(pallet);

        // 6. Create Assembly Instances and Tasks
        List<AssemblyInstance> instances = new ArrayList<>();
        for (Assembly assembly : model.getAssemblies()) {
            AssemblyInstance asmInstance = new AssemblyInstance();
            asmInstance.setAssembly(assembly);
            asmInstance.setProductInstance(instance);
            asmInstance.setStatus(AssemblyInstanceStatus.PLANNED);
            asmInstance = assemblyInstanceRepository.save(asmInstance);
            
            instances.add(asmInstance);

            // Create Task for the first operation of this assembly
            Operation firstOp = assembly.getOperations().stream()
                    .filter(op -> op.getDependsOnOperation() == null)
                    .findFirst()
                    .orElse(null);

            if (firstOp != null) {
                // Creates a task ready for execution
                taskExecutionService.createIndividualTask(
                        asmInstance.getId(), firstOp.getId(), null, 
                        firstOp.getPost() != null ? firstOp.getPost().getId() : null);
            }
        }
        
        // Link Assemblies to Pallet
        pallet.setAssemblyInstances(instances);
        palletRepository.save(pallet);

        response.put("message", "Mock workflow data generated successfully.");
        response.put("seriesId", series.getId());
        response.put("workerId", mockWorker.getId());
        response.put("pallet", pallet.getQrCode());

        return ResponseEntity.ok(response);
    }
}
