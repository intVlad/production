package com.example.productionmvp.service;

import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.model.*;
import com.example.productionmvp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeriesServiceTest {

    @Mock
    private SeriesRepository seriesRepository;
    @Mock
    private ProductModelRepository productModelRepository;
    @Mock
    private ProductInstanceRepository productInstanceRepository;
    @Mock
    private AssemblyRepository assemblyRepository;
    @Mock
    private AssemblyInstanceRepository assemblyInstanceRepository;
    @Mock
    private HistoryEventRepository historyEventRepository;
    @Mock
    private TaskExecutionService taskExecutionService;

    @InjectMocks
    private SeriesService seriesService;

    private UUID seriesId;
    private UUID modelId;
    private ProductModel model;
    private Series series;

    @BeforeEach
    void setUp() {
        seriesId = UUID.randomUUID();
        modelId = UUID.randomUUID();

        model = new ProductModel();
        model.setId(modelId);
        model.setName("Test Model");

        series = new Series();
        series.setId(seriesId);
        series.setNumber("S-001");
        series.setPlannedQuantity(2);
        series.setStatus(SeriesStatus.PLANNED);
        series.setProductModel(model);
    }

    @Test
    void createSeries_Success() {
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusDays(7);

        when(productModelRepository.findById(modelId)).thenReturn(Optional.of(model));
        
        Assembly assembly = new Assembly();
        assembly.setId(UUID.randomUUID());
        assembly.setName("Main Assembly");
        
        when(assemblyRepository.findByProductModelId(modelId)).thenReturn(List.of(assembly));

        Series created = seriesService.createSeries(modelId, "S-001", 2, SeriesPriority.HIGH, start, end);

        assertNotNull(created);
        assertEquals("S-001", created.getNumber());
        assertEquals(2, created.getPlannedQuantity());
        assertEquals(SeriesPriority.HIGH, created.getPriority());
        assertEquals(SeriesStatus.PLANNED, created.getStatus());

        verify(seriesRepository, times(1)).save(any(Series.class));
        verify(productInstanceRepository, times(1)).saveAll(argThat(list -> {
            int count = 0;
            for (Object ignored : (Iterable<?>) list) count++;
            return count == 2;
        }));
        verify(assemblyInstanceRepository, times(1)).saveAll(argThat(list -> {
            int count = 0;
            for (Object ignored : (Iterable<?>) list) count++;
            return count == 2;
        }));
        verify(historyEventRepository, times(1)).save(any(HistoryEvent.class));
    }

    @Test
    void createSeries_CreatesInitialTasksForEachInstance_OnlyForRootOperations() {
        when(productModelRepository.findById(modelId)).thenReturn(Optional.of(model));

        Operation rootOp = new Operation();
        rootOp.setId(UUID.randomUUID());
        Operation dependentOp = new Operation();
        dependentOp.setId(UUID.randomUUID());
        dependentOp.setDependsOnOperation(rootOp);

        Assembly assembly = new Assembly();
        assembly.setId(UUID.randomUUID());
        assembly.setOperations(List.of(rootOp, dependentOp));

        when(assemblyRepository.findByProductModelId(modelId)).thenReturn(List.of(assembly));

        seriesService.createSeries(modelId, "S-002", 2, SeriesPriority.MEDIUM, LocalDateTime.now(), LocalDateTime.now());

        // 2 planned units x 1 root operation each = 2 initial tasks; the dependent
        // operation must NOT get a task yet (it unlocks only once the root completes).
        verify(taskExecutionService, times(2))
                .createIndividualTask(any(), eq(rootOp.getId()), isNull(), isNull());
        verify(taskExecutionService, never())
                .createIndividualTask(any(), eq(dependentOp.getId()), any(), any());
    }

    @Test
    void createSeries_ThrowsEntityNotFoundException_WhenModelNotFound() {
        when(productModelRepository.findById(modelId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            seriesService.createSeries(modelId, "S-001", 2, SeriesPriority.HIGH, LocalDateTime.now(), LocalDateTime.now());
        });

        verify(seriesRepository, never()).save(any());
    }

    @Test
    void getSeriesProgress_Success_PartialProgress() {
        when(seriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

        ProductInstance p1 = new ProductInstance();
        p1.setSeries(series);
        p1.setStatus(InstanceStatus.READY);

        ProductInstance p2 = new ProductInstance();
        p2.setSeries(series);
        p2.setStatus(InstanceStatus.PLANNED);

        when(productInstanceRepository.findBySeriesId(seriesId)).thenReturn(List.of(p1, p2));

        com.example.productionmvp.dto.SeriesProgressDTO progress = seriesService.getSeriesProgress(seriesId);

        assertEquals(2L, progress.getTotalProducts());
        assertEquals(1L, progress.getCompleted());
        assertEquals(50.0, progress.getPercentage());
    }

    @Test
    void getSeriesProgress_Success_ZeroQuantity() {
        series.setPlannedQuantity(0);
        when(seriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(productInstanceRepository.findBySeriesId(seriesId)).thenReturn(Collections.emptyList());

        com.example.productionmvp.dto.SeriesProgressDTO progress = seriesService.getSeriesProgress(seriesId);

        assertEquals(0L, progress.getTotalProducts());
        assertEquals(0L, progress.getCompleted());
        assertEquals(0.0, progress.getPercentage());
    }

    @Test
    void updateSeriesStatus_ToCompleted() {
        when(seriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

        ProductInstance p1 = new ProductInstance();
        p1.setSeries(series);
        p1.setStatus(InstanceStatus.READY);

        ProductInstance p2 = new ProductInstance();
        p2.setSeries(series);
        p2.setStatus(InstanceStatus.READY);

        when(productInstanceRepository.findBySeriesId(seriesId)).thenReturn(List.of(p1, p2));

        seriesService.updateSeriesStatus(seriesId);

        assertEquals(SeriesStatus.COMPLETED, series.getStatus());
        verify(seriesRepository, times(1)).save(series);
    }

    @Test
    void updateSeriesStatus_ToInProduction() {
        when(seriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

        ProductInstance p1 = new ProductInstance();
        p1.setSeries(series);
        p1.setStatus(InstanceStatus.READY); // one is ready

        ProductInstance p2 = new ProductInstance();
        p2.setSeries(series);
        p2.setStatus(InstanceStatus.PLANNED); // other is planned

        when(productInstanceRepository.findBySeriesId(seriesId)).thenReturn(List.of(p1, p2));

        seriesService.updateSeriesStatus(seriesId);

        assertEquals(SeriesStatus.IN_PRODUCTION, series.getStatus());
        verify(seriesRepository, times(1)).save(series);
    }

    @Test
    void updateSeriesStatus_ToPlanned() {
        when(seriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

        ProductInstance p1 = new ProductInstance();
        p1.setSeries(series);
        p1.setStatus(InstanceStatus.PLANNED);

        ProductInstance p2 = new ProductInstance();
        p2.setSeries(series);
        p2.setStatus(InstanceStatus.PLANNED);

        when(productInstanceRepository.findBySeriesId(seriesId)).thenReturn(List.of(p1, p2));

        seriesService.updateSeriesStatus(seriesId);

        assertEquals(SeriesStatus.PLANNED, series.getStatus());
        verify(seriesRepository, times(1)).save(series);
    }

    @Test
    void updateSeriesStatus_EmptyProducts() {
        when(seriesRepository.findById(seriesId)).thenReturn(Optional.of(series));
        when(productInstanceRepository.findBySeriesId(seriesId)).thenReturn(Collections.emptyList());

        seriesService.updateSeriesStatus(seriesId);

        // Should return early, not save
        verify(seriesRepository, never()).save(any());
    }

    @Test
    void findAllActive_Success() {
        when(seriesRepository.findActiveSeries()).thenReturn(List.of(series));

        List<Series> activeSeries = seriesService.findAllActive();

        assertNotNull(activeSeries);
        assertEquals(1, activeSeries.size());
        assertEquals(seriesId, activeSeries.get(0).getId());
    }

    @Test
    void findById_Success() {
        when(seriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

        Series found = seriesService.findById(seriesId);

        assertNotNull(found);
        assertEquals(seriesId, found.getId());
    }

    @Test
    void findById_NotFound() {
        when(seriesRepository.findById(seriesId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> seriesService.findById(seriesId));
    }
}
