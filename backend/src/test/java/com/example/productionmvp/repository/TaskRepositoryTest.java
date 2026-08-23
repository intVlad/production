package com.example.productionmvp.repository;

import com.example.productionmvp.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Real DB-backed (not Mockito-mocked) on purpose: these two queries navigate to-one
// associations (productInstance, series, assignedWorker) in JPQL. Hibernate compiles that
// path navigation as an inner join, which silently drops rows where the association is null -
// a class of bug a mocked repository can never catch, since the mock just returns whatever
// the test tells it to.
@DataJpaTest
public class TaskRepositoryTest {

    @Autowired private TaskRepository taskRepository;
    @Autowired private WorkerRepository workerRepository;
    @Autowired private SeriesRepository seriesRepository;
    @Autowired private ProductModelRepository productModelRepository;

    @Test
    void findByAssignedWorkerIdAndStatusIn_FindsTaskWithoutProductInstance() {
        Worker worker = new Worker();
        worker.setName("Test Worker");
        worker = workerRepository.save(worker);

        Task task = new Task();
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setAssignedWorker(worker);
        // Deliberately no productInstance/stage: createIndividualTask (the only real
        // task-creation path in the v2 assemblyInstance/series model) never sets them.
        taskRepository.save(task);

        List<Task> result = taskRepository.findByAssignedWorkerIdAndStatusIn(
                worker.getId(), List.of(TaskStatus.IN_PROGRESS));

        assertEquals(1, result.size());
    }

    @Test
    void findByDashboardFilters_FindsTaskWithoutProductInstance() {
        ProductModel model = new ProductModel();
        model.setName("Test Model");
        model = productModelRepository.save(model);

        Series series = new Series();
        series.setNumber("S-1");
        series.setProductModel(model);
        series = seriesRepository.save(series);

        Task task = new Task();
        task.setStatus(TaskStatus.READY);
        task.setSeries(series);
        taskRepository.save(task);

        List<Task> bySeries = taskRepository.findByDashboardFilters(series.getId(), null);
        assertEquals(1, bySeries.size());

        List<Task> noFilters = taskRepository.findByDashboardFilters(null, null);
        assertEquals(1, noFilters.size());
    }

    @Test
    void findByDashboardFilters_SeriesFilterExcludesOtherSeries() {
        ProductModel model = new ProductModel();
        model.setName("Test Model");
        model = productModelRepository.save(model);

        Series seriesA = new Series();
        seriesA.setNumber("S-A");
        seriesA.setProductModel(model);
        seriesA = seriesRepository.save(seriesA);

        Series seriesB = new Series();
        seriesB.setNumber("S-B");
        seriesB.setProductModel(model);
        seriesB = seriesRepository.save(seriesB);

        Task taskA = new Task();
        taskA.setStatus(TaskStatus.READY);
        taskA.setSeries(seriesA);
        taskRepository.save(taskA);

        Task taskB = new Task();
        taskB.setStatus(TaskStatus.READY);
        taskB.setSeries(seriesB);
        taskRepository.save(taskB);

        List<Task> result = taskRepository.findByDashboardFilters(seriesA.getId(), null);

        assertEquals(1, result.size());
        assertEquals(seriesA.getId(), result.get(0).getSeries().getId());
    }
}
