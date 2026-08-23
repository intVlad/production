package com.example.productionmvp.controller;

import com.example.productionmvp.model.Post;
import com.example.productionmvp.model.Section;
import com.example.productionmvp.model.Task;
import com.example.productionmvp.repository.PostRepository;
import com.example.productionmvp.repository.SectionRepository;
import com.example.productionmvp.service.ResourceConstraintService;
import com.example.productionmvp.service.PrioritizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.springframework.web.util.NestedServletException;

@WebMvcTest(controllers = PostController.class, excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = {com.example.productionmvp.config.security.SecurityConfig.class, com.example.productionmvp.config.security.JwtAuthenticationFilter.class}), excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class, org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
class PostControllerTest {

    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtUtil jwtUtil;
    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.UserDetailsServiceImpl userDetailsService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PostRepository postRepository;

    @MockBean
    private SectionRepository sectionRepository;

    @MockBean
    private ResourceConstraintService resourceConstraintService;

    @MockBean
    private PrioritizationService prioritizationService;

    private Post post;
    private Section section;
    private UUID postId;
    private UUID sectionId;
    private UUID workerId;

    @BeforeEach
    void setUp() {
        postId = UUID.randomUUID();
        sectionId = UUID.randomUUID();
        workerId = UUID.randomUUID();

        section = new Section();
        section.setId(sectionId);
        section.setName("Test Section");

        post = new Post();
        post.setId(postId);
        post.setName("Test Post");
        post.setMaxCapacity(10);
        post.setSection(section);
    }

    @Test
    void getAllPosts_ReturnsList() throws Exception {
        Mockito.when(postRepository.findAll()).thenReturn(List.of(post));

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(postId.toString()))
                .andExpect(jsonPath("$[0].name").value("Test Post"));
    }

    @Test
    void getPostById_WhenExists_ReturnsPost() throws Exception {
        Mockito.when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        mockMvc.perform(get("/api/posts/{id}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId.toString()))
                .andExpect(jsonPath("$.name").value("Test Post"));
    }

    @Test
    void getPostById_WhenDoesNotExist_ReturnsNotFound() throws Exception {
        Mockito.when(postRepository.findById(postId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/posts/{id}", postId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPostQueue_ReturnsTasks() throws Exception {
        Task task = new Task();
        task.setId(UUID.randomUUID());

        Mockito.when(resourceConstraintService.getPostQueue(postId)).thenReturn(List.of(task));

        mockMvc.perform(get("/api/posts/{id}/queue", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(task.getId().toString()));
    }

    @Test
    void getPostLoad_ReturnsMap() throws Exception {
        Map<String, Object> loadMap = new HashMap<>();
        loadMap.put("currentLoad", 5);
        loadMap.put("maxCapacity", 10);

        Mockito.when(resourceConstraintService.getPostLoad(postId)).thenReturn(loadMap);

        mockMvc.perform(get("/api/posts/{id}/load", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentLoad").value(5))
                .andExpect(jsonPath("$.maxCapacity").value(10));
    }

    @Test
    void createPost_WithFullBody_ReturnsCreatedPost() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "New Post");
        body.put("maxCapacity", 20);
        body.put("sectionId", sectionId.toString());

        Post newPost = new Post();
        newPost.setId(UUID.randomUUID());
        newPost.setName("New Post");
        newPost.setMaxCapacity(20);
        newPost.setSection(section);

        Mockito.when(sectionRepository.findById(sectionId)).thenReturn(Optional.of(section));
        Mockito.when(postRepository.save(any(Post.class))).thenReturn(newPost);

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Post"))
                .andExpect(jsonPath("$.maxCapacity").value(20));
    }

    @Test
    void createPost_WithoutSectionId_ReturnsCreatedPost() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "New Post");

        Post newPost = new Post();
        newPost.setId(UUID.randomUUID());
        newPost.setName("New Post");
        newPost.setMaxCapacity(0);

        Mockito.when(postRepository.save(any(Post.class))).thenReturn(newPost);

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Post"));
    }
    
    @Test
    void createPost_WithSectionId_SectionNotFound_ThrowsException() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("sectionId", sectionId.toString());

        Mockito.when(sectionRepository.findById(sectionId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAvailableTasks_ReturnsTasks() throws Exception {
        Task task = new Task();
        task.setId(UUID.randomUUID());

        Mockito.when(prioritizationService.getAvailableTasksForPost(postId, workerId)).thenReturn(List.of(task));

        mockMvc.perform(get("/api/posts/{id}/available-tasks", postId)
                        .param("workerId", workerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(task.getId().toString()));
    }
}
