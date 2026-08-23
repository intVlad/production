package com.example.productionmvp.service;

import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.model.Operation;
import com.example.productionmvp.model.Series;
import com.example.productionmvp.model.SeriesPriority;
import com.example.productionmvp.model.Task;
import com.example.productionmvp.model.TaskPriority;
import com.example.productionmvp.model.TaskStatus;
import com.example.productionmvp.repository.SeriesRepository;
import com.example.productionmvp.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PrioritizationService {

    private final TaskRepository taskRepository;
    private final SeriesRepository seriesRepository;

    public PrioritizationService(TaskRepository taskRepository, SeriesRepository seriesRepository) {
        this.taskRepository = taskRepository;
        this.seriesRepository = seriesRepository;
    }

    public List<Task> getAvailableTasksForPost(UUID postId, UUID workerId) {
        List<Task> tasks = taskRepository.findAvailableTasksForPost(postId, workerId);

        return tasks.stream().sorted((t1, t2) -> {
            // Task Priority
            int p1 = getTaskPriorityWeight(t1.getPriority());
            int p2 = getTaskPriorityWeight(t2.getPriority());
            if (p1 != p2) return Integer.compare(p2, p1);

            // Series Priority
            int s1 = t1.getSeries() != null ? getSeriesPriorityWeight(t1.getSeries().getPriority()) : 0;
            int s2 = t2.getSeries() != null ? getSeriesPriorityWeight(t2.getSeries().getPriority()) : 0;
            if (s1 != s2) return Integer.compare(s2, s1);

            // Overdue tasks
            boolean o1 = t1.getDueDate() != null && t1.getDueDate().isBefore(LocalDateTime.now());
            boolean o2 = t2.getDueDate() != null && t2.getDueDate().isBefore(LocalDateTime.now());
            if (o1 && !o2) return -1;
            if (!o1 && o2) return 1;

            // FIFO
            if (t1.getCreatedAt() != null && t2.getCreatedAt() != null) {
                return t1.getCreatedAt().compareTo(t2.getCreatedAt());
            }

            return 0;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Task setUrgentPriority(UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
        task.setPriority(TaskPriority.URGENT);
        return taskRepository.save(task);
    }

    public List<Operation> calculateCriticalPath(UUID seriesId) {
        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new EntityNotFoundException("Series not found: " + seriesId));
        
        List<Task> delayedTasks = taskRepository.findDelayedTasksForSeries(seriesId);
                
        return delayedTasks.stream()
                .map(Task::getOperation)
                .distinct()
                .collect(Collectors.toList());
    }

    private int getTaskPriorityWeight(TaskPriority p) {
        if (p == null) return 0;
        switch (p) {
            case URGENT: return 4;
            case HIGH: return 3;
            case MEDIUM: return 2;
            case LOW: return 1;
            default: return 0;
        }
    }

    private int getSeriesPriorityWeight(SeriesPriority p) {
        if (p == null) return 0;
        switch (p) {
            case HIGH: return 3;
            case MEDIUM: return 2;
            case LOW: return 1;
            default: return 0;
        }
    }
}
