package com.example.productionmvp.service;

import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.exception.InsufficientMaterialException;
import com.example.productionmvp.model.Material;
import com.example.productionmvp.model.MaterialReservation;
import com.example.productionmvp.model.Series;
import com.example.productionmvp.model.SupplyStatus;
import com.example.productionmvp.model.Task;
import com.example.productionmvp.repository.AssemblyRepository;
import com.example.productionmvp.repository.MaterialRepository;
import com.example.productionmvp.repository.MaterialReservationRepository;
import com.example.productionmvp.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MaterialServiceTest {

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private MaterialReservationRepository materialReservationRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private SeriesService seriesService;

    @Mock
    private AssemblyRepository assemblyRepository;

    @InjectMocks
    private MaterialService materialService;

    private Task task;
    private Material material;
    private UUID taskId;
    private UUID materialId;

    @BeforeEach
    void setUp() {
        taskId = UUID.randomUUID();
        materialId = UUID.randomUUID();

        task = new Task();
        task.setId(taskId);

        material = new Material();
        material.setId(materialId);
        material.setName("Steel");
        material.setAvailableStock(100.0);
        material.setReservedQuantity(0.0);
        material.setUsedQuantity(0.0);
    }

    @Test
    void testReserveMaterials_Success() {
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(materialRepository.findByIdLocked(materialId)).thenReturn(Optional.of(material));
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));

        List<Map<String, Object>> materials = new ArrayList<>();
        Map<String, Object> req = new HashMap<>();
        req.put("materialId", materialId.toString());
        req.put("requiredQuantity", 20.0);
        materials.add(req);

        materialService.reserveMaterials(taskId, materials);

        assertEquals(80.0, material.getAvailableStock());
        assertEquals(20.0, material.getReservedQuantity());
        verify(materialRepository, times(2)).save(material);
        
        ArgumentCaptor<MaterialReservation> reservationCaptor = ArgumentCaptor.forClass(MaterialReservation.class);
        verify(materialReservationRepository).save(reservationCaptor.capture());
        
        MaterialReservation savedReservation = reservationCaptor.getValue();
        assertEquals(task, savedReservation.getTask());
        assertEquals(material, savedReservation.getMaterial());
        assertEquals(20.0, savedReservation.getReservedQuantity());
        assertEquals(0.0, savedReservation.getUsedQuantity());
    }

    @Test
    void testReserveMaterials_TaskNotFound() {
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        List<Map<String, Object>> materials = new ArrayList<>();

        assertThrows(EntityNotFoundException.class, () -> materialService.reserveMaterials(taskId, materials));
    }

    @Test
    void testReserveMaterials_MaterialNotFound() {
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(materialRepository.findByIdLocked(materialId)).thenReturn(Optional.empty());

        List<Map<String, Object>> materials = new ArrayList<>();
        Map<String, Object> req = new HashMap<>();
        req.put("materialId", materialId.toString());
        req.put("requiredQuantity", 20.0);
        materials.add(req);

        assertThrows(EntityNotFoundException.class, () -> materialService.reserveMaterials(taskId, materials));
    }

    @Test
    void testReserveMaterials_InsufficientStock() {
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(materialRepository.findByIdLocked(materialId)).thenReturn(Optional.of(material));

        List<Map<String, Object>> materials = new ArrayList<>();
        Map<String, Object> req = new HashMap<>();
        req.put("materialId", materialId.toString());
        req.put("requiredQuantity", 120.0);
        materials.add(req);

        assertThrows(InsufficientMaterialException.class, () -> materialService.reserveMaterials(taskId, materials));
    }

    @Test
    void testReleaseMaterialReservation_Success() {
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));
        
        MaterialReservation reservation = new MaterialReservation();
        reservation.setMaterial(material);
        reservation.setReservedQuantity(30.0);
        reservation.setUsedQuantity(10.0);
        
        when(materialReservationRepository.findByTaskId(taskId)).thenReturn(Collections.singletonList(reservation));

        materialService.releaseMaterialReservation(taskId);

        assertEquals(120.0, material.getAvailableStock());
        assertEquals(-20.0, material.getReservedQuantity());
        
        verify(materialRepository, times(2)).save(material);
        verify(materialReservationRepository).delete(reservation);
    }
    
    @Test
    void testReleaseMaterialReservation_NoQuantityToRelease() {
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        
        MaterialReservation reservation = new MaterialReservation();
        reservation.setMaterial(material);
        reservation.setReservedQuantity(30.0);
        reservation.setUsedQuantity(30.0);
        
        when(materialReservationRepository.findByTaskId(taskId)).thenReturn(Collections.singletonList(reservation));

        materialService.releaseMaterialReservation(taskId);

        verify(materialRepository, never()).save(any());
        verify(materialReservationRepository).delete(reservation);
    }

    @Test
    void testReleaseMaterialReservation_TaskNotFound() {
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> materialService.releaseMaterialReservation(taskId));
    }

    @Test
    void testConsumeMaterials_Success() {
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));
        
        MaterialReservation reservation = new MaterialReservation();
        reservation.setMaterial(material);
        reservation.setReservedQuantity(40.0);
        reservation.setUsedQuantity(10.0);
        
        when(materialReservationRepository.findByTaskId(taskId)).thenReturn(Collections.singletonList(reservation));

        materialService.consumeMaterials(taskId);

        assertEquals(-30.0, material.getReservedQuantity());
        assertEquals(30.0, material.getUsedQuantity());
        
        assertEquals(40.0, reservation.getUsedQuantity());
        verify(materialReservationRepository).save(reservation);
        verify(materialRepository, times(2)).save(material);
    }
    
    @Test
    void testConsumeMaterials_NoQuantityToConsume() {
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        
        MaterialReservation reservation = new MaterialReservation();
        reservation.setMaterial(material);
        reservation.setReservedQuantity(10.0);
        reservation.setUsedQuantity(10.0);
        
        when(materialReservationRepository.findByTaskId(taskId)).thenReturn(Collections.singletonList(reservation));

        materialService.consumeMaterials(taskId);

        verify(materialRepository, never()).save(any());
        verify(materialReservationRepository, never()).save(any());
    }

    @Test
    void testConsumeMaterials_TaskNotFound() {
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> materialService.consumeMaterials(taskId));
    }

    @Test
    void testCalculateSeriesRequirements_NoProductModel_ReturnsEmpty() {
        UUID seriesId = UUID.randomUUID();
        Series series = new Series();
        series.setPlannedQuantity(10);
        when(seriesService.findById(seriesId)).thenReturn(series);

        List<Map<String, Object>> result = materialService.calculateSeriesRequirements(seriesId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(seriesService).findById(seriesId);
    }

    @Test
    void testCalculateSeriesRequirements_ComputesPerMaterialTotals() {
        UUID seriesId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();

        com.example.productionmvp.model.ProductModel model = new com.example.productionmvp.model.ProductModel();
        model.setId(modelId);

        Series series = new Series();
        series.setProductModel(model);
        series.setPlannedQuantity(10);
        when(seriesService.findById(seriesId)).thenReturn(series);

        material.setAvailableStock(15.0);
        material.setReservedQuantity(5.0);

        com.example.productionmvp.model.Operation operation = new com.example.productionmvp.model.Operation();
        operation.setMaterialQuantityPerUnit(2.0);
        operation.setRequiredMaterials(Set.of(material));

        com.example.productionmvp.model.Assembly assembly = new com.example.productionmvp.model.Assembly();
        assembly.setOperations(List.of(operation));

        when(assemblyRepository.findByProductModelId(modelId)).thenReturn(List.of(assembly));

        List<Map<String, Object>> result = materialService.calculateSeriesRequirements(seriesId);

        assertEquals(1, result.size());
        Map<String, Object> row = result.get(0);
        assertEquals(materialId, row.get("materialId"));
        assertEquals(20.0, row.get("required")); // 2.0/unit * 10 planned
        assertEquals(15.0, row.get("available"));
        assertEquals(5.0, row.get("deficit")); // required 20 - available 15
        assertEquals("INSUFFICIENT", row.get("status"));
    }

    @Test
    void testUpdateSupplyStatus_CriticalDeficit() {
        material.setAvailableStock(0.0);
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));

        materialService.updateSupplyStatus(materialId);

        assertEquals(SupplyStatus.CRITICAL_DEFICIT, material.getSupplyStatus());
        verify(materialRepository).save(material);
    }

    // Below the reorder point is exactly what minimumStock exists to flag; this used to be
    // driven by reservedQuantity instead and reported SUFFICIENT here.
    @Test
    void testUpdateSupplyStatus_BelowMinimumStock_IsInsufficient() {
        material.setAvailableStock(10.0);
        material.setMinimumStock(20.0);
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));

        materialService.updateSupplyStatus(materialId);

        assertEquals(SupplyStatus.INSUFFICIENT, material.getSupplyStatus());
        verify(materialRepository).save(material);
    }

    @Test
    void testUpdateSupplyStatus_Sufficient() {
        material.setAvailableStock(50.0);
        material.setMinimumStock(20.0);
        material.setReservedQuantity(20.0);
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));

        materialService.updateSupplyStatus(materialId);

        assertEquals(SupplyStatus.SUFFICIENT, material.getSupplyStatus());
        verify(materialRepository).save(material);
    }

    // Stock committed to other tasks says nothing about whether this material needs reordering,
    // so a healthy stock above its reorder point stays SUFFICIENT however much is reserved.
    @Test
    void testUpdateSupplyStatus_HighReservationAboveMinimum_StaysSufficient() {
        material.setAvailableStock(50.0);
        material.setMinimumStock(10.0);
        material.setReservedQuantity(500.0);
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));

        materialService.updateSupplyStatus(materialId);

        assertEquals(SupplyStatus.SUFFICIENT, material.getSupplyStatus());
    }

    @Test
    void testUpdateSupplyStatus_MaterialNotFound() {
        when(materialRepository.findById(materialId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> materialService.updateSupplyStatus(materialId));
    }
}
