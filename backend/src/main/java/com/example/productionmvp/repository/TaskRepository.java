package com.example.productionmvp.repository;

import com.example.productionmvp.model.Task;
import com.example.productionmvp.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    @Query("SELECT t FROM Task t JOIN FETCH t.productInstance JOIN FETCH t.stage LEFT JOIN FETCH t.assignedWorker WHERE t.status = :status")
    List<Task> findByStatus(@Param("status") TaskStatus status);

    @Query("SELECT t FROM Task t JOIN FETCH t.productInstance JOIN FETCH t.stage LEFT JOIN FETCH t.assignedWorker WHERE t.status IN :statuses")
    List<Task> findByStatusIn(@Param("statuses") List<TaskStatus> statuses);

    @Query("SELECT t FROM Task t JOIN FETCH t.productInstance JOIN FETCH t.stage LEFT JOIN FETCH t.assignedWorker LEFT JOIN FETCH t.missingMaterialsList WHERE t.missingMaterials = true")
    List<Task> findByMissingMaterialsTrue();

    boolean existsByProductInstanceAndStage(com.example.productionmvp.model.ProductInstance productInstance, com.example.productionmvp.model.Stage stage);

    @Query("SELECT t FROM Task t JOIN FETCH t.productInstance JOIN FETCH t.stage LEFT JOIN FETCH t.assignedWorker WHERE t.assignedWorker.id = :workerId")
    List<Task> findByAssignedWorkerId(@Param("workerId") UUID workerId);

    // LEFT JOIN (not JOIN FETCH): productInstance/stage are v1 fields that createIndividualTask
    // (the only task-creation path in the current v2 assemblyInstance/series model) never sets.
    // An inner join here silently drops every v2 task from the result.
    @Query("SELECT t FROM Task t LEFT JOIN FETCH t.productInstance LEFT JOIN FETCH t.stage JOIN FETCH t.assignedWorker w WHERE w.id = :workerId AND t.status IN :statuses")
    List<Task> findByAssignedWorkerIdAndStatusIn(@Param("workerId") UUID workerId, @Param("statuses") List<TaskStatus> statuses);

    @Query("SELECT t FROM Task t JOIN FETCH t.productInstance JOIN FETCH t.stage LEFT JOIN FETCH t.assignedWorker WHERE t.productInstance.serialNumber = :serialNumber AND t.status IN :statuses")
    List<Task> findByProductInstanceSerialNumberAndStatusIn(@Param("serialNumber") String serialNumber, @Param("statuses") List<TaskStatus> statuses);

    java.util.Optional<Task> findByProductInstanceAndStage(com.example.productionmvp.model.ProductInstance productInstance, com.example.productionmvp.model.Stage stage);

    java.util.Optional<Task> findFirstByAssemblyInstanceAndOperationOrderByCreatedAtDesc(com.example.productionmvp.model.AssemblyInstance assemblyInstance, com.example.productionmvp.model.Operation operation);

    List<Task> findByAssemblyInstanceId(UUID assemblyInstanceId);

    List<Task> findByBatchId(UUID batchId);

    // Explicit LEFT JOINs, not the implicit path-navigation join JPQL would otherwise compile
    // to an INNER JOIN: t.series/t.assignedWorker are legitimately null for some tasks (batch
    // tasks, unassigned READY tasks), and an inner join would silently drop those rows even
    // when the corresponding filter is null. t.productInstance.series.id (the old predicate)
    // was worse still - productInstance itself is always null for v2 tasks, so it dropped
    // every real task in the system regardless of filters.
    @Query("SELECT t FROM Task t LEFT JOIN t.series s LEFT JOIN t.assignedWorker w WHERE " +
           "(:seriesId IS NULL OR s.id = :seriesId) AND " +
           "(:workerId IS NULL OR w.id = :workerId)")
    List<Task> findByDashboardFilters(@Param("seriesId") UUID seriesId, @Param("workerId") UUID workerId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.missingMaterials = true")
    long countMissingMaterials();
    @Query("SELECT t FROM Task t WHERE t.post.id = :postId AND (t.status = com.example.productionmvp.model.TaskStatus.READY OR t.status = com.example.productionmvp.model.TaskStatus.CREATED OR (t.status = com.example.productionmvp.model.TaskStatus.ASSIGNED AND t.assignedWorker.id = :workerId))")
    List<Task> findAvailableTasksForPost(@Param("postId") UUID postId, @Param("workerId") UUID workerId);

    @Query("SELECT t FROM Task t WHERE t.series.id = :seriesId AND t.status IN (com.example.productionmvp.model.TaskStatus.READY, com.example.productionmvp.model.TaskStatus.IN_PROGRESS, com.example.productionmvp.model.TaskStatus.CREATED) AND t.dueDate < CURRENT_TIMESTAMP ORDER BY t.dueDate ASC")
    List<Task> findDelayedTasksForSeries(@Param("seriesId") UUID seriesId);

    // Serializes concurrent start/pause/resume/complete on the same task (e.g. two operators
    // scanning the same pallet and both tapping "Start") so only one wins the status transition
    // instead of both succeeding and corrupting TimeLog state.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Task t WHERE t.id = :id")
    java.util.Optional<Task> findByIdLocked(@Param("id") UUID id);
}
