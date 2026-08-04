package com.example.productionmvp.repository;

import com.example.productionmvp.model.Task;
import com.example.productionmvp.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByStatus(TaskStatus status);
    List<Task> findByStatusIn(List<TaskStatus> statuses);
    List<Task> findByMissingMaterialsTrue();
    boolean existsByProductInstanceAndStage(com.example.productionmvp.model.ProductInstance productInstance, com.example.productionmvp.model.Stage stage);
    List<Task> findByAssignedWorkerId(UUID workerId);
    List<Task> findByAssignedWorkerIdAndStatusIn(UUID workerId, List<TaskStatus> statuses);
    List<Task> findByProductInstanceSerialNumberAndStatusIn(String serialNumber, List<TaskStatus> statuses);
    java.util.Optional<Task> findByProductInstanceAndStage(com.example.productionmvp.model.ProductInstance productInstance, com.example.productionmvp.model.Stage stage);
}
