package com.example.productionmvp.service;

import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.model.*;
import com.example.productionmvp.repository.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SeriesService {
    private final SeriesRepository seriesRepository;
    private final ProductModelRepository productModelRepository;
    private final ProductInstanceRepository productInstanceRepository;
    private final AssemblyRepository assemblyRepository;
    private final AssemblyInstanceRepository assemblyInstanceRepository;
    private final HistoryEventRepository historyEventRepository;
    private final TaskExecutionService taskExecutionService;

    public SeriesService(SeriesRepository seriesRepository,
                         ProductModelRepository productModelRepository,
                         ProductInstanceRepository productInstanceRepository,
                         AssemblyRepository assemblyRepository,
                         AssemblyInstanceRepository assemblyInstanceRepository,
                         HistoryEventRepository historyEventRepository,
                         @Lazy TaskExecutionService taskExecutionService) {
        this.seriesRepository = seriesRepository;
        this.productModelRepository = productModelRepository;
        this.productInstanceRepository = productInstanceRepository;
        this.assemblyRepository = assemblyRepository;
        this.assemblyInstanceRepository = assemblyInstanceRepository;
        this.historyEventRepository = historyEventRepository;
        this.taskExecutionService = taskExecutionService;
    }

    @Transactional
    public Series createSeries(UUID modelId, String number, int plannedQty, SeriesPriority priority, LocalDateTime startDate, LocalDateTime endDate) {
        ProductModel model = productModelRepository.findById(modelId)
                .orElseThrow(() -> new EntityNotFoundException("ProductModel not found"));

        Series series = new Series();
        series.setProductModel(model);
        series.setModelVersion(model.getVersion());
        series.setNumber(number);
        series.setPlannedQuantity(plannedQty);
        series.setPriority(priority);
        series.setPlannedStartDate(startDate);
        series.setPlannedEndDate(endDate);
        series.setStatus(SeriesStatus.PLANNED);
        seriesRepository.save(series);

        List<Assembly> modelAssemblies = assemblyRepository.findByProductModelId(model.getId());

        List<ProductInstance> instancesToSave = new ArrayList<>();
        List<AssemblyInstance> asmInstancesToSave = new ArrayList<>();

        for (int i = 1; i <= plannedQty; i++) {
            ProductInstance instance = new ProductInstance();
            instance.setProductModel(model);
            instance.setModelVersion(model.getVersion());
            instance.setSeries(series);
            instance.setSerialNumber(String.format("%s-%03d", number, i));
            instance.setStatus(InstanceStatus.PLANNED);
            instancesToSave.add(instance);

            for (Assembly assembly : modelAssemblies) {
                AssemblyInstance asmInstance = new AssemblyInstance();
                asmInstance.setAssembly(assembly);
                asmInstance.setProductInstance(instance);
                asmInstance.setStatus(AssemblyInstanceStatus.PLANNED);
                asmInstancesToSave.add(asmInstance);
            }
        }

        productInstanceRepository.saveAll(instancesToSave);
        assemblyInstanceRepository.saveAll(asmInstancesToSave);

        // ТЗ §17 крок 5-6: creating a series must make each product instance's first
        // operations immediately actionable, not just create PLANNED placeholder rows.
        for (AssemblyInstance asmInstance : asmInstancesToSave) {
            List<Operation> initialOps = asmInstance.getAssembly().getOperations() == null
                    ? List.of()
                    : asmInstance.getAssembly().getOperations().stream()
                            .filter(op -> op.getDependsOnOperation() == null)
                            .collect(Collectors.toList());
            for (Operation firstOp : initialOps) {
                taskExecutionService.createIndividualTask(
                        asmInstance.getId(), firstOp.getId(), null,
                        firstOp.getPost() != null ? firstOp.getPost().getId() : null);
            }
        }

        HistoryEvent event = new HistoryEvent();
        event.setAction("Створено серію: " + number);
        event.setSeries(series);
        event.setTimestamp(LocalDateTime.now());
        historyEventRepository.save(event);

        return series;
    }

    @Transactional(readOnly = true)
    public com.example.productionmvp.dto.SeriesProgressDTO getSeriesProgress(UUID seriesId) {
        Series series = findById(seriesId);
        List<ProductInstance> products = productInstanceRepository.findBySeriesId(seriesId);
        
        long totalProducts = series.getPlannedQuantity();
        long completed = products.stream()
                .filter(p -> p.getStatus() == InstanceStatus.READY)
                .count();
        double percentage = totalProducts == 0 ? 0 : ((double) completed / totalProducts) * 100;

        return new com.example.productionmvp.dto.SeriesProgressDTO(totalProducts, completed, percentage);
    }

    @Transactional(readOnly = true)
    public List<com.example.productionmvp.dto.ProductInstanceSummaryDTO> getProductInstances(UUID seriesId) {
        return productInstanceRepository.findBySeriesId(seriesId).stream()
                .map(com.example.productionmvp.dto.ProductInstanceSummaryDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateSeriesStatus(UUID seriesId) {
        Series series = findById(seriesId);
        List<ProductInstance> products = productInstanceRepository.findBySeriesId(seriesId);

        if (products.isEmpty()) {
            return;
        }

        boolean allCompleted = products.stream().allMatch(p -> p.getStatus() == InstanceStatus.READY);
        boolean anyInProgress = products.stream().anyMatch(p -> p.getStatus() != InstanceStatus.PLANNED);

        if (allCompleted) {
            series.setStatus(SeriesStatus.COMPLETED);
        } else if (anyInProgress) {
            series.setStatus(SeriesStatus.IN_PRODUCTION);
        } else {
            series.setStatus(SeriesStatus.PLANNED);
        }
        
        seriesRepository.save(series);
    }

    @Transactional(readOnly = true)
    public List<Series> findAllActive() {
        return seriesRepository.findActiveSeries();
    }

    @Transactional(readOnly = true)
    public Series findById(UUID id) {
        return seriesRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Series not found with id: " + id));
    }
}
