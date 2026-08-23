package com.example.productionmvp.repository;

import com.example.productionmvp.model.AssemblyInstance;
import com.example.productionmvp.model.AssemblyInstanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssemblyInstanceRepository extends JpaRepository<AssemblyInstance, UUID> {
    List<AssemblyInstance> findByProductInstanceId(UUID productInstanceId);
    List<AssemblyInstance> findByStatus(AssemblyInstanceStatus status);
    Optional<AssemblyInstance> findByAssemblyIdAndProductInstanceId(UUID assemblyId, UUID productInstanceId);
}
