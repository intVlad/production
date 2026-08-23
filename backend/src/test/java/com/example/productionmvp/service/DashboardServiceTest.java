package com.example.productionmvp.service;

import com.example.productionmvp.dto.DashboardResponseDTO;
import com.example.productionmvp.model.InstanceStatus;
import com.example.productionmvp.model.Operation;
import com.example.productionmvp.model.SeriesStatus;
import com.example.productionmvp.model.Task;
import com.example.productionmvp.model.TaskStatus;
import com.example.productionmvp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DashboardServiceTest {
    @Mock private ProductInstanceRepository productInstanceRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private TimeLogRepository timeLogRepository;
    @Mock private HistoryEventRepository historyEventRepository;
    @Mock private BatchRepository batchRepository;
    @Mock private OutsourceRecordRepository outsourceRecordRepository;
    @Mock private WorkerRepository workerRepository;
    @Mock private PostRepository postRepository;
    @Mock private DefectRecordRepository defectRecordRepository;
    @Mock private SeriesRepository seriesRepository;

    @InjectMocks private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        when(productInstanceRepository.findByStatusNot(InstanceStatus.READY)).thenReturn(Collections.emptyList());
        when(taskRepository.findByDashboardFilters(any(), any())).thenReturn(Collections.emptyList());
        lenient().when(taskRepository.findAll()).thenReturn(Collections.emptyList());
        when(batchRepository.findActiveBatches()).thenReturn(Collections.emptyList());
        when(outsourceRecordRepository.findAll()).thenReturn(Collections.emptyList());
        when(outsourceRecordRepository.findOverdueRecords()).thenReturn(Collections.emptyList());
        when(timeLogRepository.findAll()).thenReturn(Collections.emptyList());
        when(postRepository.findAll()).thenReturn(Collections.emptyList());
        when(defectRecordRepository.findAll()).thenReturn(Collections.emptyList());
        when(seriesRepository.findByStatus(SeriesStatus.IN_PRODUCTION)).thenReturn(Collections.emptyList());
        when(historyEventRepository.findAll()).thenReturn(Collections.emptyList());
    }

    @Test
    void testGetDashboardData() {
        DashboardResponseDTO data = dashboardService.getDashboardData(null, null, null, null);
        assertNotNull(data);
        assertNotNull(data.getActiveTasks());
    }

    // Checklist §64: dashboard needs a filter "за етапом" (by stage/operation name), distinct
    // from the "status" (TaskStatus) filter already covered above.
    @Test
    void testGetDashboardData_FiltersByStage() {
        Operation cutting = new Operation();
        cutting.setName("Порізка");
        Task cuttingTask = new Task();
        cuttingTask.setStatus(TaskStatus.IN_PROGRESS);
        cuttingTask.setOperation(cutting);

        Operation welding = new Operation();
        welding.setName("Зварювання");
        Task weldingTask = new Task();
        weldingTask.setStatus(TaskStatus.IN_PROGRESS);
        weldingTask.setOperation(welding);

        List<Task> tasks = Arrays.asList(cuttingTask, weldingTask);
        when(taskRepository.findByDashboardFilters(any(), any())).thenReturn(tasks);

        DashboardResponseDTO data = dashboardService.getDashboardData(null, null, null, "Порізка");

        assertEquals(1, data.getActiveTasks().size());
    }
}
