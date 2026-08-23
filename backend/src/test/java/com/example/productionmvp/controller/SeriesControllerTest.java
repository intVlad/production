package com.example.productionmvp.controller;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.example.productionmvp.model.Series;
import com.example.productionmvp.model.SeriesPriority;
import com.example.productionmvp.repository.SeriesRepository;
import com.example.productionmvp.service.SeriesService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SeriesController.class, excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = {com.example.productionmvp.config.security.SecurityConfig.class, com.example.productionmvp.config.security.JwtAuthenticationFilter.class}), excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class, org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
public class SeriesControllerTest {

    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtUtil jwtUtil;
    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.UserDetailsServiceImpl userDetailsService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SeriesRepository seriesRepository;

    @MockBean
    private SeriesService seriesService;

    @Autowired
    private ObjectMapper objectMapper;

    private Series sampleSeries;
    private UUID seriesId;

    @BeforeEach
    void setUp() {
        seriesId = UUID.randomUUID();
        sampleSeries = new Series();
    }

    @Test
    void getAllSeries_ShouldReturnListOfSeries() throws Exception {
        when(seriesRepository.findAll()).thenReturn(List.of(sampleSeries));

        mockMvc.perform(get("/api/series"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    void getActiveSeries_ShouldReturnActiveSeries() throws Exception {
        when(seriesService.findAllActive()).thenReturn(List.of(sampleSeries));

        mockMvc.perform(get("/api/series/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    void getSeriesById_ShouldReturnSeries() throws Exception {
        when(seriesService.findById(seriesId)).thenReturn(sampleSeries);

        mockMvc.perform(get("/api/series/{id}", seriesId))
                .andExpect(status().isOk());
    }

    @Test
    void getSeriesProgress_ShouldReturnProgressMap() throws Exception {
        com.example.productionmvp.dto.SeriesProgressDTO progress =
                new com.example.productionmvp.dto.SeriesProgressDTO(100, 10, 10.0);

        when(seriesService.getSeriesProgress(seriesId)).thenReturn(progress);

        mockMvc.perform(get("/api/series/{id}/progress", seriesId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts").value(100))
                .andExpect(jsonPath("$.completed").value(10))
                .andExpect(jsonPath("$.percentage").value(10.0));
    }

    @Test
    void createSeries_ShouldCreateAndReturnSeries() throws Exception {
        UUID modelId = UUID.randomUUID();
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("modelId", modelId.toString());
        requestBody.put("number", "SER-001");
        requestBody.put("plannedQuantity", 500);
        requestBody.put("priority", "HIGH");
        requestBody.put("plannedStartDate", "2026-08-15");
        requestBody.put("plannedEndDate", "2026-08-20");

        LocalDate startDate = LocalDate.parse("2026-08-15");
        LocalDate endDate = LocalDate.parse("2026-08-20");

        when(seriesService.createSeries(
                eq(modelId),
                eq("SER-001"),
                eq(500),
                eq(SeriesPriority.HIGH),
                eq(startDate.atStartOfDay()),
                eq(endDate.atStartOfDay())
        )).thenReturn(sampleSeries);

        mockMvc.perform(post("/api/series")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk());
    }

    @Test
    void createSeries_WithDefaultPriority_ShouldCreateAndReturnSeries() throws Exception {
        UUID modelId = UUID.randomUUID();
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("modelId", modelId.toString());
        requestBody.put("number", "SER-002");
        requestBody.put("plannedQuantity", 300);
        // Omitting priority to test the default logic
        requestBody.put("plannedStartDate", "2026-09-01");
        requestBody.put("plannedEndDate", "2026-09-10");

        LocalDate startDate = LocalDate.parse("2026-09-01");
        LocalDate endDate = LocalDate.parse("2026-09-10");

        when(seriesService.createSeries(
                eq(modelId),
                eq("SER-002"),
                eq(300),
                eq(SeriesPriority.MEDIUM), // Default priority
                eq(startDate.atStartOfDay()),
                eq(endDate.atStartOfDay())
        )).thenReturn(sampleSeries);

        mockMvc.perform(post("/api/series")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk());
    }
}
