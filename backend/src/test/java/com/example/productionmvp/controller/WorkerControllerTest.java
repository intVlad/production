package com.example.productionmvp.controller;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.example.productionmvp.model.Task;
import com.example.productionmvp.dto.TaskDTO;
import com.example.productionmvp.dto.WorkerRequestDTO;
import com.example.productionmvp.model.TaskStatus;
import com.example.productionmvp.model.Worker;
import com.example.productionmvp.repository.TaskRepository;
import com.example.productionmvp.repository.WorkerRepository;
import com.example.productionmvp.repository.SectionRepository;
import com.example.productionmvp.repository.OperationRepository;
import com.example.productionmvp.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc(addFilters = false)
class WorkerControllerTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private OperationRepository operationRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private WorkerController workerController;

    private Worker worker;
    private UUID workerId;

    @BeforeEach
    void setUp() {
        workerId = UUID.randomUUID();
        worker = new Worker();
        worker.setId(workerId);
        worker.setName("Test Worker");
    }

    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtUtil jwtUtil;
    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.UserDetailsServiceImpl userDetailsService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void testGetAllWorkers() {
        when(workerRepository.findAll()).thenReturn(Arrays.asList(worker));

        ResponseEntity<List<Worker>> response = workerController.getAllWorkers();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Test Worker", response.getBody().get(0).getName());
    }

    @Test
    void testCreateWorker_Success() {
        WorkerRequestDTO body = new WorkerRequestDTO();
        body.setName("Test Worker");
        when(workerRepository.save(any(Worker.class))).thenReturn(worker);

        ResponseEntity<Worker> response = workerController.createWorker(body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Test Worker", response.getBody().getName());
        verify(workerRepository, times(1)).save(any(Worker.class));
    }

    @Test
    void testCreateWorker_SetsInitialPin() {
        WorkerRequestDTO body = new WorkerRequestDTO();
        body.setName("Test Worker");
        body.setPin("1234");
        when(workerRepository.save(any(Worker.class))).thenReturn(worker);

        workerController.createWorker(body);

        // The bug this DTO fixes: pin used to be silently dropped because it isn't a real
        // field on the Worker entity - confirm it now actually reaches AuthService.
        verify(authService, times(1)).setWorkerPin(worker.getId(), "1234");
    }

    @Test
    void testCreateWorker_ResolvesSection() {
        UUID sectionId = UUID.randomUUID();
        com.example.productionmvp.model.Section section = new com.example.productionmvp.model.Section();
        section.setId(sectionId);
        WorkerRequestDTO body = new WorkerRequestDTO();
        body.setName("Test Worker");
        body.setSectionId(sectionId);
        when(sectionRepository.findById(sectionId)).thenReturn(java.util.Optional.of(section));
        when(workerRepository.save(any(Worker.class))).thenReturn(worker);

        workerController.createWorker(body);

        verify(sectionRepository, times(1)).findById(sectionId);
    }

    @Test
    void testCreateWorker_NullName() {
        WorkerRequestDTO invalidBody = new WorkerRequestDTO();
        invalidBody.setName(null);

        ResponseEntity<Worker> response = workerController.createWorker(invalidBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testCreateWorker_EmptyName() {
        WorkerRequestDTO invalidBody = new WorkerRequestDTO();
        invalidBody.setName("");

        ResponseEntity<Worker> response = workerController.createWorker(invalidBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testGetWorkerTasks() {
        Task taskInProgress = new Task();
        taskInProgress.setAssignedWorker(worker);
        taskInProgress.setStatus(TaskStatus.IN_PROGRESS);

        Task taskPaused = new Task();
        taskPaused.setAssignedWorker(worker);
        taskPaused.setStatus(TaskStatus.PAUSED);

        Task taskCompleted = new Task();
        taskCompleted.setAssignedWorker(worker);
        taskCompleted.setStatus(TaskStatus.COMPLETED);

        Task taskOtherWorker = new Task();
        Worker otherWorker = new Worker();
        otherWorker.setId(UUID.randomUUID());
        taskOtherWorker.setAssignedWorker(otherWorker);
        taskOtherWorker.setStatus(TaskStatus.IN_PROGRESS);

        Task taskUnassigned = new Task();
        taskUnassigned.setStatus(TaskStatus.IN_PROGRESS);

        when(taskRepository.findByAssignedWorkerIdAndStatusIn(
                workerId, List.of(TaskStatus.IN_PROGRESS, TaskStatus.PAUSED)
        )).thenReturn(Arrays.asList(taskInProgress, taskPaused));

        ResponseEntity<List<TaskDTO>> response = workerController.getWorkerTasks(workerId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void testGetWorkerHistory() {
        Task taskCompleted = new Task();
        taskCompleted.setAssignedWorker(worker);
        taskCompleted.setStatus(TaskStatus.COMPLETED);

        Task taskInProgress = new Task();
        taskInProgress.setAssignedWorker(worker);
        taskInProgress.setStatus(TaskStatus.IN_PROGRESS);

        Task taskOtherWorkerCompleted = new Task();
        Worker otherWorker = new Worker();
        otherWorker.setId(UUID.randomUUID());
        taskOtherWorkerCompleted.setAssignedWorker(otherWorker);
        taskOtherWorkerCompleted.setStatus(TaskStatus.COMPLETED);

        when(taskRepository.findByAssignedWorkerIdAndStatusIn(
                workerId, List.of(TaskStatus.COMPLETED)
        )).thenReturn(Arrays.asList(taskCompleted));

        ResponseEntity<List<TaskDTO>> response = workerController.getWorkerHistory(workerId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }
}
