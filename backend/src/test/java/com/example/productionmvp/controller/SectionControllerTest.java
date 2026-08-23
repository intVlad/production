package com.example.productionmvp.controller;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.example.productionmvp.model.Post;
import com.example.productionmvp.model.Section;
import com.example.productionmvp.repository.PostRepository;
import com.example.productionmvp.repository.SectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SectionController.class, excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = {com.example.productionmvp.config.security.SecurityConfig.class, com.example.productionmvp.config.security.JwtAuthenticationFilter.class}), excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class, org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
public class SectionControllerTest {

    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtUtil jwtUtil;
    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.UserDetailsServiceImpl userDetailsService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SectionRepository sectionRepository;

    @MockBean
    private PostRepository postRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Section testSection;
    private UUID sectionId;

    @BeforeEach
    void setUp() {
        sectionId = UUID.randomUUID();
        testSection = new Section();
        testSection.setId(sectionId);
        testSection.setName("Test Section");
        testSection.setLocation("Test Location");
        testSection.setArea(100.5);
    }

    @Test
    void getAllSections_ShouldReturnListOfSections() throws Exception {
        when(sectionRepository.findAll()).thenReturn(Arrays.asList(testSection));

        mockMvc.perform(get("/api/sections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(sectionId.toString()))
                .andExpect(jsonPath("$[0].name").value("Test Section"))
                .andExpect(jsonPath("$[0].location").value("Test Location"))
                .andExpect(jsonPath("$[0].area").value(100.5));
    }

    @Test
    void getSectionById_WhenExists_ShouldReturnSection() throws Exception {
        when(sectionRepository.findById(sectionId)).thenReturn(Optional.of(testSection));

        mockMvc.perform(get("/api/sections/{id}", sectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sectionId.toString()))
                .andExpect(jsonPath("$.name").value("Test Section"));
    }

    @Test
    void getSectionById_WhenNotExists_ShouldReturnNotFound() throws Exception {
        when(sectionRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sections/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createSection_ShouldReturnCreatedSection() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "New Section");
        body.put("location", "New Location");
        body.put("area", "200.0");

        Section savedSection = new Section();
        savedSection.setId(UUID.randomUUID());
        savedSection.setName("New Section");
        savedSection.setLocation("New Location");
        savedSection.setArea(200.0);

        when(sectionRepository.save(any(Section.class))).thenReturn(savedSection);

        mockMvc.perform(post("/api/sections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Section"))
                .andExpect(jsonPath("$.location").value("New Location"))
                .andExpect(jsonPath("$.area").value(200.0));
    }
    
    @Test
    void createSection_WithPartialData_ShouldReturnCreatedSection() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "New Section");

        Section savedSection = new Section();
        savedSection.setId(UUID.randomUUID());
        savedSection.setName("New Section");

        when(sectionRepository.save(any(Section.class))).thenReturn(savedSection);

        mockMvc.perform(post("/api/sections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Section"));
    }

    @Test
    void getPostsInSection_ShouldReturnPostsForGivenSection() throws Exception {
        Post post1 = new Post();
        post1.setId(UUID.randomUUID());
        post1.setName("Post 1");
        post1.setSection(testSection);

        Section otherSection = new Section();
        otherSection.setId(UUID.randomUUID());
        Post post2 = new Post();
        post2.setId(UUID.randomUUID());
        post2.setName("Post 2");
        post2.setSection(otherSection);
        
        Post post3 = new Post();
        post3.setId(UUID.randomUUID());
        post3.setName("Post 3");
        // No section

        when(postRepository.findAll()).thenReturn(Arrays.asList(post1, post2, post3));

        mockMvc.perform(get("/api/sections/{id}/posts", sectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Post 1"));
    }
}
