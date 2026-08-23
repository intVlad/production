package com.example.productionmvp.controller;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.example.productionmvp.model.Material;
import com.example.productionmvp.repository.MaterialRepository;
import com.example.productionmvp.service.MaterialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc(addFilters = false)
public class MaterialControllerTest {

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private MaterialService materialService;

    @InjectMocks
    private MaterialController materialController;

    private Material material1;
    private Material material2;

    @BeforeEach
    void setUp() {
        material1 = new Material("Wood", "W-01");
        material1.setId(UUID.randomUUID());
        material1.setAvailableStock(5.0);
        material1.setMinimumStock(10.0);

        material2 = new Material("Metal", "M-01");
        material2.setId(UUID.randomUUID());
        material2.setAvailableStock(20.0);
        material2.setMinimumStock(10.0);
    }

    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtUtil jwtUtil;
    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.UserDetailsServiceImpl userDetailsService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void testGetAllMaterials() {
        when(materialRepository.findAll()).thenReturn(Arrays.asList(material1, material2));

        ResponseEntity<List<Material>> response = materialController.getAllMaterials();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        verify(materialRepository, times(1)).findAll();
    }

    @Test
    void testGetRequirements() {
        UUID seriesId = UUID.randomUUID();
        Map<String, Object> row = new HashMap<>();
        row.put("materialId", material1.getId());
        row.put("required", 100.0);

        List<Map<String, Object>> serviceResponse = List.of(row);

        when(materialService.calculateSeriesRequirements(seriesId)).thenReturn(serviceResponse);

        ResponseEntity<List<Map<String, Object>>> response = materialController.getRequirements(seriesId);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(serviceResponse, response.getBody());
        verify(materialService, times(1)).calculateSeriesRequirements(seriesId);
    }

    @Test
    void testGetDeficits() {
        when(materialRepository.findAll()).thenReturn(Arrays.asList(material1, material2));

        ResponseEntity<List<Material>> response = materialController.getDeficits();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(material1, response.getBody().get(0));
        verify(materialRepository, times(1)).findAll();
    }

    @Test
    void testUpdateStock_Success() {
        UUID id = material1.getId();
        com.example.productionmvp.dto.UpdateStockRequestDTO body = new com.example.productionmvp.dto.UpdateStockRequestDTO();
        body.setAvailableStock(15.5);

        when(materialRepository.findById(id)).thenReturn(Optional.of(material1));
        when(materialRepository.save(any(Material.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<Material> response = materialController.updateStock(id, body);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(15.5, response.getBody().getAvailableStock());
        verify(materialRepository, times(2)).findById(id);
        verify(materialRepository, times(1)).save(material1);
        verify(materialService, times(1)).updateSupplyStatus(id);
    }

    @Test
    void testUpdateStock_NotFound() {
        UUID id = UUID.randomUUID();
        com.example.productionmvp.dto.UpdateStockRequestDTO body = new com.example.productionmvp.dto.UpdateStockRequestDTO();
        body.setAvailableStock(15.5);

        when(materialRepository.findById(id)).thenReturn(Optional.empty());

        // A missing material is a 404, not the bare NoSuchElementException from an unguarded
        // orElseThrow() that the global handler could only report as a 500.
        assertThrows(com.example.productionmvp.exception.EntityNotFoundException.class, () -> {
            materialController.updateStock(id, body);
        });

        verify(materialRepository, times(1)).findById(id);
        verify(materialRepository, never()).save(any(Material.class));
    }
}
