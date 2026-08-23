package com.example.productionmvp.controller;

import com.example.productionmvp.model.Assembly;
import com.example.productionmvp.model.Operation;
import com.example.productionmvp.model.ProductModel;
import com.example.productionmvp.repository.AssemblyRepository;
import com.example.productionmvp.repository.OperationRepository;
import com.example.productionmvp.repository.ProductModelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AssemblyController.class, excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = {com.example.productionmvp.config.security.SecurityConfig.class, com.example.productionmvp.config.security.JwtAuthenticationFilter.class}), excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class, org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
public class AssemblyControllerTest {

    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtUtil jwtUtil;
    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.UserDetailsServiceImpl userDetailsService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AssemblyRepository assemblyRepository;

    @MockBean
    private OperationRepository operationRepository;

    @MockBean
    private ProductModelRepository productModelRepository;

    @MockBean
    private com.example.productionmvp.repository.SectionRepository sectionRepository;

    @MockBean
    private com.example.productionmvp.repository.PostRepository postRepository;

    @MockBean
    private com.example.productionmvp.repository.MaterialRepository materialRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Assembly assembly;
    private ProductModel productModel;
    private Operation operation;
    private UUID assemblyId;
    private UUID productModelId;
    private UUID operationId;

    @BeforeEach
    void setUp() {
        assemblyId = UUID.randomUUID();
        productModelId = UUID.randomUUID();
        operationId = UUID.randomUUID();

        productModel = new ProductModel();
        productModel.setId(productModelId);

        assembly = new Assembly();
        assembly.setId(assemblyId);
        assembly.setProductModel(productModel);
        assembly.setCode("ASM-001");
        assembly.setName("Main Assembly");

        operation = new Operation();
        operation.setId(operationId);
        operation.setAssembly(assembly);
        operation.setName("Welding");
    }

    @Test
    void testGetAllAssemblies() throws Exception {
        when(assemblyRepository.findAll()).thenReturn(List.of(assembly));

        mockMvc.perform(get("/api/assemblies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(assemblyId.toString()))
                .andExpect(jsonPath("$[0].name").value("Main Assembly"));
    }

    @Test
    void testGetAssembliesByModel() throws Exception {
        Assembly assembly2 = new Assembly();
        assembly2.setId(UUID.randomUUID());
        
        ProductModel productModel2 = new ProductModel();
        productModel2.setId(UUID.randomUUID());
        assembly2.setProductModel(productModel2);

        when(assemblyRepository.findByProductModelId(productModelId)).thenReturn(List.of(assembly));

        mockMvc.perform(get("/api/assemblies/model/" + productModelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(assemblyId.toString()));
    }

    @Test
    void testGetAssemblyById_Success() throws Exception {
        when(assemblyRepository.findById(assemblyId)).thenReturn(Optional.of(assembly));

        mockMvc.perform(get("/api/assemblies/" + assemblyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assemblyId.toString()))
                .andExpect(jsonPath("$.code").value("ASM-001"));
    }

    @Test
    void testGetAssemblyById_NotFound() throws Exception {
        when(assemblyRepository.findById(assemblyId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/assemblies/" + assemblyId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateAssembly() throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("productModelId", productModelId.toString());
        requestBody.put("code", "ASM-002");
        requestBody.put("name", "Sub Assembly");
        requestBody.put("category", "Electronics");
        requestBody.put("parts", "PCB, Wires");
        requestBody.put("normativeTimeMinutes", 120);

        when(productModelRepository.findById(productModelId)).thenReturn(Optional.of(productModel));
        when(assemblyRepository.save(any(Assembly.class))).thenAnswer(invocation -> {
            Assembly saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        mockMvc.perform(post("/api/assemblies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ASM-002"))
                .andExpect(jsonPath("$.name").value("Sub Assembly"))
                .andExpect(jsonPath("$.category").value("Electronics"))
                .andExpect(jsonPath("$.parts").value("PCB, Wires"))
                .andExpect(jsonPath("$.normativeTimeMinutes").value(120));
    }

    @Test
    void testCreateAssembly_EmptyBody() throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("productModelId", productModelId.toString());

        when(productModelRepository.findById(productModelId)).thenReturn(Optional.of(productModel));
        when(assemblyRepository.save(any(Assembly.class))).thenAnswer(invocation -> {
            Assembly saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        mockMvc.perform(post("/api/assemblies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.name").doesNotExist());
    }

    @Test
    void testGetOperationsForAssembly() throws Exception {
        Operation operation2 = new Operation();
        operation2.setId(UUID.randomUUID());
        Assembly assembly2 = new Assembly();
        assembly2.setId(UUID.randomUUID());
        operation2.setAssembly(assembly2);

        when(operationRepository.findByAssemblyIdOrderByOrderIndexAsc(assemblyId)).thenReturn(List.of(operation));

        mockMvc.perform(get("/api/assemblies/" + assemblyId + "/operations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(operationId.toString()));
    }

    @Test
    void testAddOperationToAssembly() throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", "Painting");
        requestBody.put("description", "Apply top coat");
        requestBody.put("normativeTimeMinutes", 45);
        requestBody.put("orderIndex", 2);
        requestBody.put("type", "INDIVIDUAL");

        when(assemblyRepository.findById(assemblyId)).thenReturn(Optional.of(assembly));
        when(operationRepository.save(any(Operation.class))).thenAnswer(invocation -> {
            Operation saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        mockMvc.perform(post("/api/assemblies/" + assemblyId + "/operations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Painting"))
                .andExpect(jsonPath("$.description").value("Apply top coat"))
                .andExpect(jsonPath("$.normativeTimeMinutes").value(45))
                .andExpect(jsonPath("$.orderIndex").value(2));
    }

    @Test
    void testAddOperationToAssembly_EmptyBody() throws Exception {
        Map<String, Object> requestBody = new HashMap<>();

        when(assemblyRepository.findById(assemblyId)).thenReturn(Optional.of(assembly));
        when(operationRepository.save(any(Operation.class))).thenAnswer(invocation -> {
            Operation saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        mockMvc.perform(post("/api/assemblies/" + assemblyId + "/operations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").doesNotExist());
    }
}
