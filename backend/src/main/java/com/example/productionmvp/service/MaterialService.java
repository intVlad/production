package com.example.productionmvp.service;

import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.exception.InsufficientMaterialException;
import com.example.productionmvp.model.*;
import com.example.productionmvp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final MaterialReservationRepository materialReservationRepository;
    private final TaskRepository taskRepository;
    private final SeriesService seriesService;
    private final AssemblyRepository assemblyRepository;

    public MaterialService(MaterialRepository materialRepository,
                           MaterialReservationRepository materialReservationRepository,
                           TaskRepository taskRepository,
                           SeriesService seriesService,
                           AssemblyRepository assemblyRepository) {
        this.materialRepository = materialRepository;
        this.materialReservationRepository = materialReservationRepository;
        this.taskRepository = taskRepository;
        this.seriesService = seriesService;
        this.assemblyRepository = assemblyRepository;
    }

    // Read-only pre-check so callers (task/batch creation) can decide whether to attempt
    // reserveMaterials at all, instead of calling it speculatively and catching failure —
    // catching a RuntimeException from a @Transactional call marks the *caller's* transaction
    // rollback-only regardless of the catch, and REQUIRES_NEW can't see the not-yet-committed
    // Task/Batch row the caller just saved in the same transaction. This avoids both traps.
    @Transactional(readOnly = true)
    public List<Material> findInsufficientMaterials(List<Map<String, Object>> materials) {
        List<Material> insufficient = new ArrayList<>();
        for (Map<String, Object> matReq : materials) {
            UUID materialId = UUID.fromString(matReq.get("materialId").toString());
            Double requiredQty = Double.parseDouble(matReq.get("requiredQuantity").toString());
            Material material = materialRepository.findById(materialId).orElse(null);
            if (material == null || material.getAvailableStock() == null || material.getAvailableStock() < requiredQty) {
                if (material != null) insufficient.add(material);
            }
        }
        return insufficient;
    }

    @Transactional
    public void reserveMaterials(UUID taskId, List<Map<String, Object>> materials) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found"));

        for (Map<String, Object> matReq : materials) {
            UUID materialId = UUID.fromString(matReq.get("materialId").toString());
            Double requiredQty = Double.parseDouble(matReq.get("requiredQuantity").toString());

            Material material = materialRepository.findByIdLocked(materialId)
                    .orElseThrow(() -> new EntityNotFoundException("Material not found"));

            if (material.getAvailableStock() < requiredQty) {
                throw new InsufficientMaterialException("Not enough available quantity for material: " + material.getName());
            }

            material.setAvailableStock(material.getAvailableStock() - requiredQty);
            material.setReservedQuantity(material.getReservedQuantity() + requiredQty);
            materialRepository.save(material);
            updateSupplyStatus(material.getId());

            MaterialReservation reservation = new MaterialReservation();
            reservation.setTask(task);
            reservation.setMaterial(material);
            reservation.setReservedQuantity(requiredQty);
            reservation.setUsedQuantity(0.0);
            materialReservationRepository.save(reservation);
        }
    }

    @Transactional
    public void releaseMaterialReservation(UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found"));

        List<MaterialReservation> reservations = materialReservationRepository.findByTaskId(taskId);
        for (MaterialReservation reservation : reservations) {
            Material material = reservation.getMaterial();
            Double reservedToRelease = reservation.getReservedQuantity() - reservation.getUsedQuantity();
            
            if (reservedToRelease > 0) {
                material.setAvailableStock(material.getAvailableStock() + reservedToRelease);
                material.setReservedQuantity(material.getReservedQuantity() - reservedToRelease);
                materialRepository.save(material);
                updateSupplyStatus(material.getId());
            }
            materialReservationRepository.delete(reservation);
        }
    }

    @Transactional
    public void consumeMaterials(UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found"));

        List<MaterialReservation> reservations = materialReservationRepository.findByTaskId(taskId);
        for (MaterialReservation reservation : reservations) {
            Material material = reservation.getMaterial();
            Double toConsume = reservation.getReservedQuantity() - reservation.getUsedQuantity();
            
            if (toConsume > 0) {
                material.setReservedQuantity(material.getReservedQuantity() - toConsume);
                material.setUsedQuantity(material.getUsedQuantity() + toConsume);
                materialRepository.save(material);
                updateSupplyStatus(material.getId());
                
                reservation.setUsedQuantity(reservation.getReservedQuantity());
                materialReservationRepository.save(reservation);
            }
        }
    }

    // Mirrors consumeMaterials in reverse - used when a manager reopens an accidentally
    // completed task: the reservation goes back to "reserved but not yet used" so the
    // reopened task can complete again later without double-consuming stock.
    @Transactional
    public void reverseConsumedMaterials(UUID taskId) {
        List<MaterialReservation> reservations = materialReservationRepository.findByTaskId(taskId);
        for (MaterialReservation reservation : reservations) {
            Double toRestore = reservation.getUsedQuantity();
            if (toRestore != null && toRestore > 0) {
                Material material = reservation.getMaterial();
                material.setUsedQuantity(material.getUsedQuantity() - toRestore);
                material.setReservedQuantity(material.getReservedQuantity() + toRestore);
                materialRepository.save(material);
                updateSupplyStatus(material.getId());

                reservation.setUsedQuantity(0.0);
                materialReservationRepository.save(reservation);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> calculateSeriesRequirements(UUID seriesId) {
        Series series = seriesService.findById(seriesId);
        int plannedQuantity = series.getPlannedQuantity() != null ? series.getPlannedQuantity() : 0;

        Map<UUID, Double> requiredPerMaterial = new LinkedHashMap<>();
        Map<UUID, Material> materialsById = new LinkedHashMap<>();

        if (series.getProductModel() != null && plannedQuantity > 0) {
            List<Assembly> assemblies = assemblyRepository.findByProductModelId(series.getProductModel().getId());
            for (Assembly assembly : assemblies) {
                if (assembly.getOperations() == null) continue;
                for (Operation operation : assembly.getOperations()) {
                    if (operation.getRequiredMaterials() == null || operation.getRequiredMaterials().isEmpty()) continue;
                    double qtyPerUnit = operation.getMaterialQuantityPerUnit() != null ? operation.getMaterialQuantityPerUnit() : 1.0;
                    double totalForOperation = qtyPerUnit * plannedQuantity;
                    for (Material material : operation.getRequiredMaterials()) {
                        materialsById.put(material.getId(), material);
                        requiredPerMaterial.merge(material.getId(), totalForOperation, Double::sum);
                    }
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : requiredPerMaterial.entrySet()) {
            Material material = materialsById.get(entry.getKey());
            double required = entry.getValue();
            double available = material.getAvailableStock() != null ? material.getAvailableStock() : 0.0;
            double reserved = material.getReservedQuantity() != null ? material.getReservedQuantity() : 0.0;
            double deficit = Math.max(0.0, required - available);

            Map<String, Object> row = new HashMap<>();
            row.put("materialId", material.getId());
            row.put("materialName", material.getName());
            row.put("unit", material.getUnit());
            row.put("required", required);
            row.put("available", available);
            row.put("reserved", reserved);
            row.put("deficit", deficit);
            row.put("status", deficit <= 0 ? "SUFFICIENT" : (available <= 0 ? "CRITICAL_DEFICIT" : "INSUFFICIENT"));
            result.add(row);
        }
        return result;
    }

    // The badge shown against every material in "Склад та Матеріали". It used to compare stock
    // against 0 and against reservedQuantity, and never looked at minimumStock at all - so a
    // material sitting at 1 kg with a 20 kg reorder point was reported SUFFICIENT, which is the
    // exact situation the reorder point exists to warn about. It also disagreed with
    // GET /api/materials/deficits, which was already using minimumStock correctly; the two now
    // answer the same question the same way.
    @Transactional
    public void updateSupplyStatus(UUID materialId) {
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new EntityNotFoundException("Material not found"));

        double available = material.getAvailableStock() != null ? material.getAvailableStock() : 0.0;
        double minimum = material.getMinimumStock() != null ? material.getMinimumStock() : 0.0;

        if (available <= 0) {
            material.setSupplyStatus(SupplyStatus.CRITICAL_DEFICIT);
        } else if (available < minimum) {
            material.setSupplyStatus(SupplyStatus.INSUFFICIENT);
        } else {
            material.setSupplyStatus(SupplyStatus.SUFFICIENT);
        }
        materialRepository.save(material);
    }
}
