package com.example.productionmvp.controller;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.example.productionmvp.dto.StartProductionRequestDTO;
import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.model.*;
import com.example.productionmvp.repository.*;
import com.example.productionmvp.service.TaskExecutionService;
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
public class ProductInstanceControllerTest {

    @Mock
    private ProductModelRepository productModelRepository;

    @Mock
    private ProductInstanceRepository productInstanceRepository;

    @Mock
    private StageRepository stageRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private AssemblyInstanceRepository assemblyInstanceRepository;

    @Mock
    private PalletRepository palletRepository;

    @Mock
    private TaskExecutionService taskExecutionService;

    @InjectMocks
    private ProductInstanceController productInstanceController;

    private UUID modelId;
    private UUID workerId;
    private UUID productInstanceId;
    private ProductModel productModel;
    private Assembly assembly;
    private Operation operation;

    @BeforeEach
    void setUp() {
        modelId = UUID.randomUUID();
        workerId = UUID.randomUUID();
        productInstanceId = UUID.randomUUID();

        operation = new Operation();
        operation.setId(UUID.randomUUID());

        assembly = new Assembly();
        assembly.setId(UUID.randomUUID());
        assembly.setOperations(List.of(operation));

        productModel = new ProductModel();
        productModel.setId(modelId);
        productModel.setAssemblies(List.of(assembly));
    }


    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtUtil jwtUtil;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.UserDetailsServiceImpl userDetailsService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void testGetProductModels() {
        when(productModelRepository.findAll()).thenReturn(List.of(productModel));

        ResponseEntity<List<ProductModel>> response = productInstanceController.getProductModels();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(modelId, response.getBody().get(0).getId());

        verify(productModelRepository, times(1)).findAll();
    }

    @Test
    void testStartProduction_Success_WithAssembliesAndFirstOperation() {
        StartProductionRequestDTO body = new StartProductionRequestDTO();
        body.setModelId(modelId);
        body.setSerialNumber("SN-12345");
        body.setWorkerId(workerId);

        when(productModelRepository.findById(modelId)).thenReturn(Optional.of(productModel));

        ProductInstance savedInstance = new ProductInstance();
        savedInstance.setId(productInstanceId);
        savedInstance.setSerialNumber("SN-12345");
        when(productInstanceRepository.save(any(ProductInstance.class))).thenReturn(savedInstance);

        AssemblyInstance savedAssemblyInstance = new AssemblyInstance();
        savedAssemblyInstance.setId(UUID.randomUUID());
        when(assemblyInstanceRepository.save(any(AssemblyInstance.class))).thenReturn(savedAssemblyInstance);

        Task createdTask = new Task();
        createdTask.setId(UUID.randomUUID());
        when(taskExecutionService.createIndividualTask(any(), eq(operation.getId()), eq(workerId), isNull()))
                .thenReturn(createdTask);

        ResponseEntity<List<Task>> response = productInstanceController.startProduction(body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(createdTask.getId(), response.getBody().get(0).getId());

        verify(productModelRepository, times(1)).findById(modelId);
        verify(productInstanceRepository, times(1)).save(any(ProductInstance.class));
        verify(assemblyInstanceRepository, times(1)).save(any(AssemblyInstance.class));
        verify(taskExecutionService, times(1)).createIndividualTask(any(), any(), any(), any());
    }

    @Test
    void testStartProduction_Success_FirstOperationHasPost() {
        StartProductionRequestDTO body = new StartProductionRequestDTO();
        body.setModelId(modelId);
        body.setSerialNumber("SN-12345");
        body.setWorkerId(workerId);

        Post post = new Post();
        post.setId(UUID.randomUUID());
        operation.setPost(post);

        when(productModelRepository.findById(modelId)).thenReturn(Optional.of(productModel));

        AssemblyInstance savedAssemblyInstance = new AssemblyInstance();
        savedAssemblyInstance.setId(UUID.randomUUID());
        when(assemblyInstanceRepository.save(any(AssemblyInstance.class))).thenReturn(savedAssemblyInstance);

        Task createdTask = new Task();
        when(taskExecutionService.createIndividualTask(any(), eq(operation.getId()), eq(workerId), eq(post.getId())))
                .thenReturn(createdTask);

        ResponseEntity<List<Task>> response = productInstanceController.startProduction(body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());

        verify(taskExecutionService, times(1)).createIndividualTask(any(), eq(operation.getId()), eq(workerId), eq(post.getId()));
    }

    @Test
    void testStartProduction_Success_NoWorkerIdProvided() {
        StartProductionRequestDTO body = new StartProductionRequestDTO();
        body.setModelId(modelId);
        body.setSerialNumber("SN-12345");

        when(productModelRepository.findById(modelId)).thenReturn(Optional.of(productModel));

        AssemblyInstance savedAssemblyInstance = new AssemblyInstance();
        savedAssemblyInstance.setId(UUID.randomUUID());
        when(assemblyInstanceRepository.save(any(AssemblyInstance.class))).thenReturn(savedAssemblyInstance);

        Task createdTask = new Task();
        when(taskExecutionService.createIndividualTask(any(), eq(operation.getId()), isNull(), isNull()))
                .thenReturn(createdTask);

        ResponseEntity<List<Task>> response = productInstanceController.startProduction(body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());

        verify(taskExecutionService, times(1)).createIndividualTask(any(), eq(operation.getId()), isNull(), isNull());
    }

    @Test
    void testStartProduction_Success_NoAssemblies() {
        StartProductionRequestDTO body = new StartProductionRequestDTO();
        body.setModelId(modelId);
        body.setSerialNumber("SN-12345");

        productModel.setAssemblies(Collections.emptyList());
        when(productModelRepository.findById(modelId)).thenReturn(Optional.of(productModel));

        ResponseEntity<List<Task>> response = productInstanceController.startProduction(body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());

        verify(productInstanceRepository, times(1)).save(any(ProductInstance.class));
        verify(assemblyInstanceRepository, never()).save(any());
        verify(taskExecutionService, never()).createIndividualTask(any(), any(), any(), any());
    }

    @Test
    void testStartProduction_Success_NoFirstOperation() {
        StartProductionRequestDTO body = new StartProductionRequestDTO();
        body.setModelId(modelId);
        body.setSerialNumber("SN-12345");

        Operation depOp = new Operation();
        depOp.setId(UUID.randomUUID());
        depOp.setDependsOnOperation(new Operation()); // not null
        assembly.setOperations(List.of(depOp));

        when(productModelRepository.findById(modelId)).thenReturn(Optional.of(productModel));

        AssemblyInstance savedAssemblyInstance = new AssemblyInstance();
        savedAssemblyInstance.setId(UUID.randomUUID());
        when(assemblyInstanceRepository.save(any(AssemblyInstance.class))).thenReturn(savedAssemblyInstance);

        ResponseEntity<List<Task>> response = productInstanceController.startProduction(body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());

        verify(assemblyInstanceRepository, times(1)).save(any(AssemblyInstance.class));
        verify(taskExecutionService, never()).createIndividualTask(any(), any(), any(), any());
    }

    @Test
    void testStartProduction_BadRequest_MissingModelId() {
        StartProductionRequestDTO body = new StartProductionRequestDTO();
        body.setSerialNumber("SN-12345");

        ResponseEntity<List<Task>> response = productInstanceController.startProduction(body);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(productModelRepository, never()).findById(any());
    }

    @Test
    void testStartProduction_BadRequest_MissingSerialNumber() {
        StartProductionRequestDTO body = new StartProductionRequestDTO();
        body.setModelId(modelId);

        ResponseEntity<List<Task>> response = productInstanceController.startProduction(body);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(productModelRepository, never()).findById(any());
    }

    @Test
    void testStartProduction_BadRequest_EmptySerialNumber() {
        StartProductionRequestDTO body = new StartProductionRequestDTO();
        body.setModelId(modelId);
        body.setSerialNumber("   ");

        ResponseEntity<List<Task>> response = productInstanceController.startProduction(body);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(productModelRepository, never()).findById(any());
    }

    @Test
    void testStartProduction_EntityNotFound_ModelNotFound() {
        StartProductionRequestDTO body = new StartProductionRequestDTO();
        body.setModelId(modelId);
        body.setSerialNumber("SN-12345");

        when(productModelRepository.findById(modelId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> {
            productInstanceController.startProduction(body);
        });

        assertEquals("Model not found", exception.getMessage());
        verify(productInstanceRepository, never()).save(any());
    }

    @Test
    void testGetAssemblyInstances() {
        AssemblyInstance assemblyInstance = new AssemblyInstance();
        assemblyInstance.setId(UUID.randomUUID());

        when(assemblyInstanceRepository.findByProductInstanceId(productInstanceId)).thenReturn(List.of(assemblyInstance));

        ResponseEntity<List<AssemblyInstance>> response = productInstanceController.getAssemblyInstances(productInstanceId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(assemblyInstance.getId(), response.getBody().get(0).getId());

        verify(assemblyInstanceRepository, times(1)).findByProductInstanceId(productInstanceId);
    }

    @Test
    void testGetPallets() {
        Pallet pallet = new Pallet();
        pallet.setId(UUID.randomUUID());

        when(palletRepository.findByOwnerProductId(productInstanceId)).thenReturn(List.of(pallet));

        ResponseEntity<List<Pallet>> response = productInstanceController.getPallets(productInstanceId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(pallet.getId(), response.getBody().get(0).getId());

        verify(palletRepository, times(1)).findByOwnerProductId(productInstanceId);
    }
}
