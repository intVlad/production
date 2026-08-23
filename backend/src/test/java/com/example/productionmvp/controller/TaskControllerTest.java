package com.example.productionmvp.controller;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.example.productionmvp.model.AssemblyInstance;
import com.example.productionmvp.model.Task;
import com.example.productionmvp.repository.TaskRepository;
import com.example.productionmvp.service.PrioritizationService;
import com.example.productionmvp.service.TaskExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TaskController.class, excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = {com.example.productionmvp.config.security.SecurityConfig.class, com.example.productionmvp.config.security.JwtAuthenticationFilter.class}), excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class, org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
public class TaskControllerTest {

    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtUtil jwtUtil;
    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.UserDetailsServiceImpl userDetailsService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskRepository taskRepository;

    @MockBean
    private TaskExecutionService taskExecutionService;

    @MockBean
    private PrioritizationService prioritizationService;

    @Autowired
    private ObjectMapper objectMapper;

    private Task task1;
    private Task task2;
    private UUID taskId;
    private UUID workerId;
    private UUID assemblyInstanceId;
    private AssemblyInstance assemblyInstance;

    @BeforeEach
    void setUp() {
        taskId = UUID.randomUUID();
        workerId = UUID.randomUUID();
        assemblyInstanceId = UUID.randomUUID();
        
        assemblyInstance = new AssemblyInstance();
        assemblyInstance.setId(assemblyInstanceId);

        task1 = new Task();
        task1.setId(taskId);
        
        task2 = new Task();
        task2.setId(UUID.randomUUID());
        task2.setAssemblyInstance(assemblyInstance);
    }

    // The controller now derives the acting worker from the authenticated principal (its
    // "username" is the worker's own UUID, see UserDetailsServiceImpl) rather than trusting a
    // workerId in the request body - .principal(...) is how MockMvc injects that without going
    // through the real security filter chain, which this test class excludes entirely.
    private Authentication actingAs(UUID id) {
        return new UsernamePasswordAuthenticationToken(id.toString(), null, java.util.List.of());
    }

    @Test
    void getAllTasks_ShouldReturnListOfTasks() throws Exception {
        when(taskRepository.findAll()).thenReturn(Arrays.asList(task1, task2));

        mockMvc.perform(get("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0].id").value(task1.getId().toString()))
                .andExpect(jsonPath("$[1].id").value(task2.getId().toString()));
    }

    @Test
    void getTaskById_WhenExists_ShouldReturnTask() throws Exception {
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task1));

        mockMvc.perform(get("/api/tasks/{id}", taskId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task1.getId().toString()));
    }

    @Test
    void getTaskById_WhenNotExists_ShouldReturnNotFound() throws Exception {
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/tasks/{id}", taskId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void startTask_ShouldReturnUpdatedTask() throws Exception {
        when(taskExecutionService.startTask(taskId, workerId)).thenReturn(task1);

        mockMvc.perform(post("/api/tasks/{taskId}/start", taskId)
                .principal(actingAs(workerId))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task1.getId().toString()));
    }

    @Test
    void pauseTask_ShouldReturnUpdatedTask() throws Exception {
        when(taskExecutionService.pauseTask(taskId, workerId)).thenReturn(task1);

        mockMvc.perform(post("/api/tasks/{taskId}/pause", taskId)
                .principal(actingAs(workerId))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task1.getId().toString()));
    }

    @Test
    void resumeTask_ShouldReturnUpdatedTask() throws Exception {
        when(taskExecutionService.resumeTask(taskId, workerId)).thenReturn(task1);

        mockMvc.perform(post("/api/tasks/{taskId}/resume", taskId)
                .principal(actingAs(workerId))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task1.getId().toString()));
    }

    @Test
    void completeTask_ShouldReturnUpdatedTask() throws Exception {
        when(taskExecutionService.completeTask(taskId, workerId)).thenReturn(task1);

        mockMvc.perform(post("/api/tasks/{taskId}/complete", taskId)
                .principal(actingAs(workerId))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task1.getId().toString()));
    }

    @Test
    void markDamaged_ShouldReturnUpdatedTask() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("reason", "Broken part");

        when(taskExecutionService.markDamaged(taskId, workerId, "Broken part", null)).thenReturn(task1);

        mockMvc.perform(post("/api/tasks/{taskId}/damage", taskId)
                .principal(actingAs(workerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task1.getId().toString()));
    }

    @Test
    void getAvailableTasksForPost_ShouldReturnTasks() throws Exception {
        UUID postId = UUID.randomUUID();
        when(prioritizationService.getAvailableTasksForPost(postId, workerId)).thenReturn(Arrays.asList(task1));

        mockMvc.perform(get("/api/tasks/post/{postId}/available", postId)
                .param("workerId", workerId.toString())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].id").value(task1.getId().toString()));
    }

    @Test
    void getTasksForAssemblyInstance_ShouldReturnFilteredTasks() throws Exception {
        when(taskRepository.findByAssemblyInstanceId(assemblyInstanceId)).thenReturn(Arrays.asList(task2));

        mockMvc.perform(get("/api/tasks/assembly-instance/{assemblyInstanceId}", assemblyInstanceId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].id").value(task2.getId().toString()));
    }
}
