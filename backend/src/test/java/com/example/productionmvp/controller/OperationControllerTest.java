package com.example.productionmvp.controller;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.example.productionmvp.model.Operation;
import com.example.productionmvp.repository.OperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
public class OperationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OperationRepository operationRepository;

    @InjectMocks
    private OperationController operationController;

    private UUID operationId1;
    private UUID operationId2;
    private Operation operation1;
    private Operation operation2;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(operationController).build();

        operationId1 = UUID.randomUUID();
        operationId2 = UUID.randomUUID();

        operation1 = new Operation();
        operation1.setId(operationId1);
        operation1.setName("Operation 1");
        operation1.setDescription("Description 1");
        operation1.setNormativeTimeMinutes(10);
        operation1.setRequiredQualification("Basic");
        operation1.setEquipment("Equip 1");
        operation1.setTools("Tool 1");
        operation1.setMaterialQuantityPerUnit(1.5);
        operation1.setOrderIndex(1);

        operation2 = new Operation();
        operation2.setId(operationId2);
        operation2.setName("Operation 2");
        operation2.setDescription("Description 2");
        operation2.setNormativeTimeMinutes(20);
        operation2.setRequiredQualification("Advanced");
        operation2.setEquipment("Equip 2");
        operation2.setTools("Tool 2");
        operation2.setMaterialQuantityPerUnit(2.5);
        operation2.setOrderIndex(2);
    }

    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtUtil jwtUtil;
    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.UserDetailsServiceImpl userDetailsService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    public void testGetAllOperations() throws Exception {
        List<Operation> operations = Arrays.asList(operation1, operation2);

        when(operationRepository.findAll()).thenReturn(operations);

        mockMvc.perform(get("/api/operations")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(operationId1.toString())))
                .andExpect(jsonPath("$[0].name", is("Operation 1")))
                .andExpect(jsonPath("$[1].id", is(operationId2.toString())))
                .andExpect(jsonPath("$[1].name", is("Operation 2")));
    }

    @Test
    public void testGetAllOperations_empty() throws Exception {
        when(operationRepository.findAll()).thenReturn(Arrays.asList());

        mockMvc.perform(get("/api/operations")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void testGetOperationById_found() throws Exception {
        when(operationRepository.findById(operationId1)).thenReturn(Optional.of(operation1));

        mockMvc.perform(get("/api/operations/{id}", operationId1)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(operationId1.toString())))
                .andExpect(jsonPath("$.name", is("Operation 1")))
                .andExpect(jsonPath("$.description", is("Description 1")))
                .andExpect(jsonPath("$.normativeTimeMinutes", is(10)))
                .andExpect(jsonPath("$.requiredQualification", is("Basic")))
                .andExpect(jsonPath("$.equipment", is("Equip 1")))
                .andExpect(jsonPath("$.tools", is("Tool 1")))
                .andExpect(jsonPath("$.materialQuantityPerUnit", is(1.5)))
                .andExpect(jsonPath("$.orderIndex", is(1)));
    }

    @Test
    public void testGetOperationById_notFound() throws Exception {
        when(operationRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/operations/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
