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

    @Query("SELECT t FROM Task t JOIN FETCH t.productInstance JOIN FETCH t.stage LEFT JOIN FETCH t.assignedWorker WHERE t.assignedWorker.id = :workerId AND t.status IN :statuses")
    List<Task> findByAssignedWorkerIdAndStatusIn(@Param("workerId") UUID workerId, @Param("statuses") List<TaskStatus> statuses);

    @Query("SELECT t FROM Task t JOIN FETCH t.productInstance JOIN FETCH t.stage LEFT JOIN FETCH t.assignedWorker WHERE t.productInstance.serialNumber = :serialNumber AND t.status IN :statuses")
    List<Task> findByProductInstanceSerialNumberAndStatusIn(@Param("serialNumber") String serialNumber, @Param("statuses") List<TaskStatus> statuses);

    java.util.Optional<Task> findByProductInstanceAndStage(com.example.productionmvp.model.ProductInstance productInstance, com.example.productionmvp.model.Stage stage);
}
