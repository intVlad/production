package com.example.productionmvp.repository;

import com.example.productionmvp.model.Operation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OperationRepository extends JpaRepository<Operation, UUID> {
    List<Operation> findByAssemblyIdOrderByOrderIndexAsc(UUID assemblyId);
    List<Operation> findByProductModelIdOrderByOrderIndexAsc(UUID productModelId);
    Optional<Operation> findFirstByDependsOnOperation(Operation operation);
    Optional<Operation> findByAssemblyIdAndOrderIndex(UUID assemblyId, Integer orderIndex);
    List<Operation> findByDependsOnOperation(Operation operation);
}
