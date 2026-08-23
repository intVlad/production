package com.example.productionmvp.service;

import com.example.productionmvp.model.*;
import com.example.productionmvp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final ProductInstanceRepository productInstanceRepository;
    private final TaskRepository taskRepository;
    private final TimeLogRepository timeLogRepository;
    private final HistoryEventRepository historyEventRepository;
    private final BatchRepository batchRepository;
    private final OutsourceRecordRepository outsourceRecordRepository;
    private final WorkerRepository workerRepository;
    private final PostRepository postRepository;
    private final DefectRecordRepository defectRecordRepository;
    private final SeriesRepository seriesRepository;

    public DashboardService(ProductInstanceRepository productInstanceRepository,
                            TaskRepository taskRepository,
                            TimeLogRepository timeLogRepository,
                            HistoryEventRepository historyEventRepository,
                            BatchRepository batchRepository,
                            OutsourceRecordRepository outsourceRecordRepository,
                            WorkerRepository workerRepository,
                            PostRepository postRepository,
                            DefectRecordRepository defectRecordRepository,
                            SeriesRepository seriesRepository) {
        this.productInstanceRepository = productInstanceRepository;
        this.taskRepository = taskRepository;
        this.timeLogRepository = timeLogRepository;
        this.historyEventRepository = historyEventRepository;
        this.batchRepository = batchRepository;
        this.outsourceRecordRepository = outsourceRecordRepository;
        this.workerRepository = workerRepository;
        this.postRepository = postRepository;
        this.defectRecordRepository = defectRecordRepository;
        this.seriesRepository = seriesRepository;
    }

    public com.example.productionmvp.dto.DashboardResponseDTO getDashboardData(UUID seriesId, UUID workerId, String status, String stage) {
        com.example.productionmvp.dto.DashboardResponseDTO data = new com.example.productionmvp.dto.DashboardResponseDTO();
        
        // Production overview
        long inProduction = productInstanceRepository.countByStatus(InstanceStatus.IN_PRODUCTION);
        long ready = productInstanceRepository.countByStatus(InstanceStatus.READY);
        
        data.setProductsInProduction(inProduction);
        data.setProductsReady(ready);
        
        // Map products by their current stage (v2: a "stage" is the operation name of
        // whichever assembly instance on the product currently has an in-progress task;
        // ProductInstance.currentStage is a v1 field that's no longer maintained).
        Map<UUID, String> currentStageByProduct = new HashMap<>();
        for (Task t : taskRepository.findByDashboardFilters(null, null)) {
            if (t.getStatus() != TaskStatus.IN_PROGRESS) continue;
            if (t.getAssemblyInstance() == null || t.getAssemblyInstance().getProductInstance() == null || t.getOperation() == null) continue;
            currentStageByProduct.putIfAbsent(t.getAssemblyInstance().getProductInstance().getId(), t.getOperation().getName());
        }

        Map<String, Long> productsByStage = productInstanceRepository.findByStatusNot(InstanceStatus.READY).stream()
                .filter(p -> p.getStatus() == InstanceStatus.IN_PRODUCTION)
                .collect(Collectors.groupingBy(
                        p -> currentStageByProduct.getOrDefault(p.getId(), "Очікує"),
                        Collectors.counting()));
        data.setProductsByStage(productsByStage);
        
        // Tasks filtering efficiently using JPQL in repository
        List<Task> filteredTasks = taskRepository.findByDashboardFilters(seriesId, workerId);
        if (status != null && !status.isEmpty()) {
            filteredTasks = filteredTasks.stream().filter(t -> t.getStatus().name().equalsIgnoreCase(status)).collect(Collectors.toList());
        }
        // Checklist §64 explicitly asks for a dashboard filter "за етапом" (by stage) - "stage"
        // here means the operation name (§14's dashboard table has Виріб/Серія/Етап/... as
        // distinct columns, Етап being the operation), not TaskStatus, which the "status"
        // param above already covers separately.
        if (stage != null && !stage.isEmpty()) {
            filteredTasks = filteredTasks.stream()
                    .filter(t -> t.getOperation() != null && stage.equalsIgnoreCase(t.getOperation().getName()))
                    .collect(Collectors.toList());
        }

        List<Task> activeTasksList = filteredTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS || t.getStatus() == TaskStatus.READY || t.getStatus() == TaskStatus.ASSIGNED || t.getStatus() == TaskStatus.CREATED)
                .collect(Collectors.toList());
                
        long activeTasksCount = activeTasksList.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count();
        long overdueTasks = filteredTasks.stream()
                .filter(t -> (t.getStatus() == TaskStatus.READY || t.getStatus() == TaskStatus.IN_PROGRESS || t.getStatus() == TaskStatus.CREATED) 
                             && t.getDueDate() != null && t.getDueDate().isBefore(LocalDateTime.now()))
                .count();
        long blockedTasks = filteredTasks.stream().filter(t -> t.getStatus() == TaskStatus.BLOCKED).count();
        
        List<Map<String, Object>> activeTasksDTO = activeTasksList.stream().map(t -> {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", t.getId());
            dto.put("status", t.getStatus());
            dto.put("priority", t.getPriority());
            dto.put("deadline", t.getDueDate());
            dto.put("normativeTimeMinutes", t.getNormativeTimeMinutes());
            dto.put("actualTimeMinutes", t.getActualTimeMinutes());
            if (t.getOperation() != null) {
                dto.put("operationName", t.getOperation().getName());
            }
            if (t.getAssemblyInstance() != null && t.getAssemblyInstance().getProductInstance() != null) {
                dto.put("productSerial", t.getAssemblyInstance().getProductInstance().getSerialNumber());
            }
            if (t.getSeries() != null) {
                Map<String, Object> seriesDTO = new HashMap<>();
                seriesDTO.put("id", t.getSeries().getId());
                seriesDTO.put("name", t.getSeries().getNumber());
                dto.put("series", seriesDTO);
            }
            if (t.getAssignedWorker() != null) {
                Map<String, Object> workerDTO = new HashMap<>();
                workerDTO.put("id", t.getAssignedWorker().getId());
                workerDTO.put("name", t.getAssignedWorker().getName());
                dto.put("worker", workerDTO);
            }
            return dto;
        }).collect(Collectors.toList());
        
        data.setActiveTasks(activeTasksDTO);
        data.setActiveTasksCount(activeTasksCount);
        data.setOverdueTasks(overdueTasks);
        data.setBlockedTasks(blockedTasks);
        
        // Batches
        long activeBatches = batchRepository.findActiveBatches().size();
        data.setActiveBatches(activeBatches);
        
        // Outsource
        List<OutsourceRecord> outRecords = outsourceRecordRepository.findAll();
        long activeOutsource = outRecords.stream().filter(r -> r.getStatus() != OutsourceStatus.RECEIVED).count();
        long overdueOutsource = outsourceRecordRepository.findOverdueRecords().size();
        
        data.setActiveOutsource(activeOutsource);
        data.setOverdueOutsource(overdueOutsource);
        
        // Workers
        long activeWorkersCount = timeLogRepository.findAll().stream()
                .filter(tl -> tl.getEndTime() == null)
                .map(tl -> tl.getWorker().getId())
                .distinct()
                .count();
        data.setActiveWorkers(activeWorkersCount);
        
        // Time
        Long closedSeconds = timeLogRepository.sumAllDurations();
        if (closedSeconds == null) closedSeconds = 0L;
        data.setTotalHoursSpent(closedSeconds / 3600.0);
        
        long normativeMins = filteredTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED && t.getNormativeTimeMinutes() != null)
                .mapToLong(Task::getNormativeTimeMinutes)
                .sum();
        data.setNormativeHours(normativeMins / 60.0);
        
        // Post loads
        List<Map<String, Object>> postLoads = postRepository.findAll().stream().map(post -> {
            Map<String, Object> load = new HashMap<>();
            load.put("postName", post.getName());
            load.put("current", post.getCurrentLoad());
            load.put("max", post.getMaxCapacity());
            long queueSize = taskRepository.findAll().stream()
                    .filter(t -> t.getPost() != null && t.getPost().getId().equals(post.getId()))
                    .filter(t -> t.getStatus() == TaskStatus.READY || t.getStatus() == TaskStatus.ASSIGNED || t.getStatus() == TaskStatus.CREATED)
                    .count();
            load.put("queue", queueSize);
            return load;
        }).collect(Collectors.toList());
        data.setPostLoads(postLoads);
        
        // Material deficits
        long materialDeficits = taskRepository.countMissingMaterials();
        data.setMaterialDeficits(materialDeficits);
        
        // Defects Today
        long defectsToday = defectRecordRepository.findAll().stream()
                .filter(d -> d.getCreatedAt().toLocalDate().equals(java.time.LocalDate.now()))
                .count();
        data.setDefectsToday(defectsToday);
        
        // Series
        long activeSeries = seriesRepository.findByStatus(SeriesStatus.IN_PRODUCTION).size();
        data.setActiveSeries(activeSeries);
        
        // Recent history
        List<Map<String, Object>> recentHistory = historyEventRepository.findAll().stream()
                .sorted(Comparator.comparing(HistoryEvent::getTimestamp).reversed())
                .limit(10)
                .map(event -> {
                    Map<String, Object> historyInfo = new HashMap<>();
                    historyInfo.put("action", event.getAction());
                    historyInfo.put("taskSerial", event.getProductInstance() != null ? event.getProductInstance().getSerialNumber() : "Невідомо");
                    // A null worker on a history event is always a system-driven action (e.g.
                    // auto-creating the next operation's task when its dependency completes),
                    // never an unidentified person - "Невідомо" read as "someone we can't
                    // identify did this", which is misleading and alarming for no reason.
                    historyInfo.put("worker", event.getWorker() != null ? event.getWorker().getName() : "Система");
                    historyInfo.put("timestamp", event.getTimestamp().toString());
                    // Lets the manager dashboard offer an "undo completion" action right on the
                    // audit entry; taskStatus tells the UI whether that's still valid (the task
                    // may have moved on, or already been reopened, since this event was logged).
                    if (event.getTask() != null) {
                        historyInfo.put("taskId", event.getTask().getId());
                        historyInfo.put("taskStatus", event.getTask().getStatus());
                    }
                    return historyInfo;
                })
                .collect(Collectors.toList());
        data.setRecentHistory(recentHistory);
        
        return data;
    }
}
