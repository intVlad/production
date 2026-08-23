package com.example.productionmvp.service;

import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.model.*;
import com.example.productionmvp.repository.SeriesRepository;
import com.example.productionmvp.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrioritizationServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private SeriesRepository seriesRepository;

    @InjectMocks
    private PrioritizationService prioritizationService;

    private UUID postId;
    private UUID workerId;
    private Post post;
    private Worker worker;
    private Series series;

    @BeforeEach
    void setUp() {
        postId = UUID.randomUUID();
        workerId = UUID.randomUUID();

        post = new Post();
        post.setId(postId);

        worker = new Worker();
        worker.setId(workerId);

        series = new Series();
        series.setId(UUID.randomUUID());
    }

    // getAvailableTasksForPost tests
    // Note: the READY/CREATED/ASSIGNED-to-worker + post filtering itself now happens
    // in TaskRepository.findAvailableTasksForPost's JPQL query, not in Java. These tests
    // stub that query directly with the (already filtered) rows it would return, and
    // verify only the in-memory sorting PrioritizationService applies on top.

    @Test
    void getAvailableTasksForPost_ReturnsEmpty_WhenNoTasks() {
        when(taskRepository.findAvailableTasksForPost(postId, workerId)).thenReturn(Collections.emptyList());

        List<Task> result = prioritizationService.getAvailableTasksForPost(postId, workerId);

        assertTrue(result.isEmpty());
    }

    @Test
    void getAvailableTasksForPost_FiltersCorrectly() {
        // Task 3: Right post, status READY
        Task task3 = new Task();
        task3.setId(UUID.randomUUID());
        task3.setPost(post);
        task3.setStatus(TaskStatus.READY);

        // Task 4: Right post, status CREATED
        Task task4 = new Task();
        task4.setId(UUID.randomUUID());
        task4.setPost(post);
        task4.setStatus(TaskStatus.CREATED);

        // Task 6: Right post, status ASSIGNED and right worker
        Task task6 = new Task();
        task6.setId(UUID.randomUUID());
        task6.setPost(post);
        task6.setStatus(TaskStatus.ASSIGNED);
        task6.setAssignedWorker(worker);

        when(taskRepository.findAvailableTasksForPost(postId, workerId))
                .thenReturn(Arrays.asList(task3, task4, task6));

        List<Task> result = prioritizationService.getAvailableTasksForPost(postId, workerId);

        assertEquals(3, result.size());
        assertTrue(result.contains(task3));
        assertTrue(result.contains(task4));
        assertTrue(result.contains(task6));
    }

    @Test
    void getAvailableTasksForPost_SortsCorrectly_ByTaskPriority() {
        Task taskLow = createTask(TaskPriority.LOW);
        Task taskUrgent = createTask(TaskPriority.URGENT);
        Task taskHigh = createTask(TaskPriority.HIGH);
        Task taskNull = createTask(null);

        when(taskRepository.findAvailableTasksForPost(postId, workerId))
                .thenReturn(Arrays.asList(taskLow, taskUrgent, taskHigh, taskNull));

        List<Task> result = prioritizationService.getAvailableTasksForPost(postId, workerId);

        assertEquals(4, result.size());
        assertEquals(taskUrgent.getId(), result.get(0).getId());
        assertEquals(taskHigh.getId(), result.get(1).getId());
        assertEquals(taskLow.getId(), result.get(2).getId());
        assertEquals(taskNull.getId(), result.get(3).getId());
    }

    @Test
    void getAvailableTasksForPost_SortsCorrectly_BySeriesPriority() {
        Task task1 = createTask(TaskPriority.MEDIUM);
        Series seriesLow = new Series(); seriesLow.setPriority(SeriesPriority.LOW);
        task1.setSeries(seriesLow);

        Task task2 = createTask(TaskPriority.MEDIUM);
        Series seriesHigh = new Series(); seriesHigh.setPriority(SeriesPriority.HIGH);
        task2.setSeries(seriesHigh);

        Task task3 = createTask(TaskPriority.MEDIUM);
        // Null series priority

        when(taskRepository.findAvailableTasksForPost(postId, workerId))
                .thenReturn(Arrays.asList(task1, task2, task3));

        List<Task> result = prioritizationService.getAvailableTasksForPost(postId, workerId);

        assertEquals(3, result.size());
        assertEquals(task2.getId(), result.get(0).getId()); // High
        assertEquals(task1.getId(), result.get(1).getId()); // Low
        assertEquals(task3.getId(), result.get(2).getId()); // Null
    }

    @Test
    void getAvailableTasksForPost_SortsCorrectly_ByOverdue() {
        Task taskNotOverdue = createTask(TaskPriority.MEDIUM);
        taskNotOverdue.setDueDate(LocalDateTime.now().plusDays(1));
        taskNotOverdue.setCreatedAt(LocalDateTime.now()); // for deterministic tie break

        Task taskOverdue = createTask(TaskPriority.MEDIUM);
        taskOverdue.setDueDate(LocalDateTime.now().minusDays(1));
        taskOverdue.setCreatedAt(LocalDateTime.now());

        Task taskNullDate = createTask(TaskPriority.MEDIUM);
        taskNullDate.setCreatedAt(LocalDateTime.now());

        // We provide a specific order, it should reorder Overdue first
        when(taskRepository.findAvailableTasksForPost(postId, workerId))
                .thenReturn(Arrays.asList(taskNotOverdue, taskOverdue, taskNullDate));

        List<Task> result = prioritizationService.getAvailableTasksForPost(postId, workerId);

        assertEquals(3, result.size());
        assertEquals(taskOverdue.getId(), result.get(0).getId());
        // The remaining two have no due date or future due date, so they fallback to FIFO.
        // In our case they are created at same time, so they maintain relative order depending on sorting logic.
    }

    @Test
    void getAvailableTasksForPost_SortsCorrectly_ByFIFO() {
        Task task2 = createTask(TaskPriority.MEDIUM);
        task2.setCreatedAt(LocalDateTime.now().minusDays(1));

        Task task1 = createTask(TaskPriority.MEDIUM);
        task1.setCreatedAt(LocalDateTime.now().minusDays(2));

        when(taskRepository.findAvailableTasksForPost(postId, workerId))
                .thenReturn(Arrays.asList(task2, task1));

        List<Task> result = prioritizationService.getAvailableTasksForPost(postId, workerId);

        assertEquals(2, result.size());
        assertEquals(task1.getId(), result.get(0).getId()); // Older first
        assertEquals(task2.getId(), result.get(1).getId());
    }

    // setUrgentPriority tests

    @Test
    void setUrgentPriority_Success() {
        UUID taskId = UUID.randomUUID();
        Task task = new Task();
        task.setId(taskId);
        task.setPriority(TaskPriority.LOW);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenReturn(task);

        Task result = prioritizationService.setUrgentPriority(taskId);

        assertEquals(TaskPriority.URGENT, result.getPriority());
        verify(taskRepository).save(task);
    }

    @Test
    void setUrgentPriority_NotFound_ThrowsException() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> prioritizationService.setUrgentPriority(taskId));
        verify(taskRepository, never()).save(any());
    }

    // calculateCriticalPath tests

    @Test
    void calculateCriticalPath_Success() {
        UUID seriesId = series.getId();
        when(seriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

        Operation op1 = new Operation(); op1.setId(UUID.randomUUID());
        Operation op2 = new Operation(); op2.setId(UUID.randomUUID());

        Task task1 = new Task();
        task1.setSeries(series);
        task1.setStatus(TaskStatus.READY);
        task1.setDueDate(LocalDateTime.now().minusDays(2));
        task1.setOperation(op1);

        Task task2 = new Task();
        task2.setSeries(series);
        task2.setStatus(TaskStatus.IN_PROGRESS);
        task2.setDueDate(LocalDateTime.now().minusDays(1));
        task2.setOperation(op2);

        Task task3 = new Task();
        task3.setSeries(series);
        task3.setStatus(TaskStatus.CREATED);
        task3.setDueDate(LocalDateTime.now().minusDays(3));
        task3.setOperation(op1); // Same op to test distinct

        // findDelayedTasksForSeries already excludes future-due, wrong-series and
        // wrong-status rows at the query level, so the mock only returns the rows
        // that would actually match (ordered by dueDate ASC, per the query).
        when(taskRepository.findDelayedTasksForSeries(seriesId)).thenReturn(Arrays.asList(
                task3, task1, task2
        ));

        List<Operation> result = prioritizationService.calculateCriticalPath(seriesId);

        // Operations in dueDate order: op1 (task3), op1 (task1), op2 (task2) -> distinct -> op1, op2
        assertEquals(2, result.size());
        assertEquals(op1, result.get(0));
        assertEquals(op2, result.get(1));
    }

    @Test
    void calculateCriticalPath_NotFound_ThrowsException() {
        UUID seriesId = UUID.randomUUID();
        when(seriesRepository.findById(seriesId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> prioritizationService.calculateCriticalPath(seriesId));
    }

    // Helper methods

    private Task createTask(TaskPriority priority) {
        Task task = new Task();
        task.setId(UUID.randomUUID());
        task.setPost(post);
        task.setStatus(TaskStatus.READY);
        task.setPriority(priority);
        return task;
    }
}
