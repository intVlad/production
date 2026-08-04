package com.example.productionmvp.service;

import com.example.productionmvp.model.*;
import com.example.productionmvp.repository.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final ProductInstanceRepository productInstanceRepository;
    private final TaskRepository taskRepository;
    private final TimeLogRepository timeLogRepository;
    private final HistoryEventRepository historyEventRepository;

    public DashboardService(ProductInstanceRepository productInstanceRepository, TaskRepository taskRepository, TimeLogRepository timeLogRepository, HistoryEventRepository historyEventRepository) {
        this.productInstanceRepository = productInstanceRepository;
        this.taskRepository = taskRepository;
        this.timeLogRepository = timeLogRepository;
        this.historyEventRepository = historyEventRepository;
    }

    public Map<String, Object> getDashboardData() {
        Map<String, Object> dashboard = new HashMap<>();
        
        // Active Products
        List<ProductInstance> activeInstances = productInstanceRepository.findByStatusNot(InstanceStatus.COMPLETED);
        dashboard.put("activeProductsCount", activeInstances.size());
        
        // Tasks currently being worked on
        List<Task> activeTasks = taskRepository.findByStatus(TaskStatus.IN_PROGRESS);
        dashboard.put("activeTasks", activeTasks.stream().map(task -> {
            Map<String, Object> taskInfo = new HashMap<>();
            taskInfo.put("taskId", task.getId());
            taskInfo.put("productSerialNumber", task.getProductInstance().getSerialNumber());
            taskInfo.put("stage", task.getStage().getName());
            taskInfo.put("worker", task.getAssignedWorker() != null ? task.getAssignedWorker().getName() : "Unassigned");
            return taskInfo;
        }).collect(Collectors.toList()));

        // Flagged issues
        List<Task> blockedTasks = taskRepository.findByStatus(TaskStatus.BLOCKED);
        List<Task> missingMaterialsTasks = taskRepository.findByMissingMaterialsTrue();
        // Overdue tasks
        List<Task> allIncomplete = taskRepository.findByStatusIn(List.of(TaskStatus.PENDING, TaskStatus.IN_PROGRESS, TaskStatus.PAUSED, TaskStatus.BLOCKED));
        List<Task> overdueTasks = allIncomplete.stream()
            .filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(LocalDateTime.now()))
            .collect(Collectors.toList());

        dashboard.put("flaggedIssues", Map.of(
            "blockedTasksCount", blockedTasks.size(),
            "missingMaterialsCount", missingMaterialsTasks.size(),
            "overdueTasksCount", overdueTasks.size()
        ));

        // Total hours spent (Optimized: using SQL sum for closed logs to prevent OOM)
        Long closedSeconds = timeLogRepository.sumAllDurations();
        long totalSeconds = closedSeconds != null ? closedSeconds : 0L;
        dashboard.put("totalHoursSpent", totalSeconds / 3600.0);

        // Calculate Bottleneck
        Map<String, Long> tasksPerStage = allIncomplete.stream()
            .collect(Collectors.groupingBy(t -> t.getStage().getName(), Collectors.counting()));
        
        String bottleneckStage = tasksPerStage.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("None");
        dashboard.put("bottleneckStage", bottleneckStage);

        // Missing materials detailed
        // Missing materials detailed
        List<Map<String, Object>> missingMaterialsDetails = missingMaterialsTasks.stream().map(task -> {
            Map<String, Object> info = new HashMap<>();
            info.put("taskId", task.getId());
            info.put("stage", task.getStage().getName());
            info.put("materials", task.getMissingMaterialsList().stream().map(Material::getName).collect(Collectors.toList()));
            return info;
        }).collect(Collectors.toList());
        dashboard.put("missingMaterialsDetails", missingMaterialsDetails);
        
        // Recent History
        List<HistoryEvent> recentEvents = historyEventRepository.findTop10ByOrderByTimestampDesc();
        List<Map<String, Object>> recentHistory = recentEvents.stream().map(event -> {
            Map<String, Object> historyInfo = new HashMap<>();
            historyInfo.put("action", event.getAction());
            historyInfo.put("taskSerial", event.getProductInstance() != null ? event.getProductInstance().getSerialNumber() : "Невідомо");
            historyInfo.put("stage", event.getStage() != null ? event.getStage().getName() : "Невідомо");
            historyInfo.put("worker", event.getWorker() != null ? event.getWorker().getName() : "Невідомо");
            historyInfo.put("timestamp", event.getTimestamp().toString());
            return historyInfo;
        }).collect(Collectors.toList());
        dashboard.put("recentHistory", recentHistory);

        return dashboard;
    }
}
