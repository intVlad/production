package com.example.productionmvp.controller;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.example.productionmvp.model.DefectRecord;
import com.example.productionmvp.model.DefectResolution;
import com.example.productionmvp.repository.DefectRecordRepository;
import com.example.productionmvp.service.DefectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DefectController.class, excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = {com.example.productionmvp.config.security.SecurityConfig.class, com.example.productionmvp.config.security.JwtAuthenticationFilter.class}), excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class, org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
public class DefectControllerTest {

    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtUtil jwtUtil;
    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.UserDetailsServiceImpl userDetailsService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DefectRecordRepository defectRecordRepository;

    @MockBean
    private DefectService defectService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testGetAllDefects() throws Exception {
        DefectRecord defect1 = new DefectRecord();
        DefectRecord defect2 = new DefectRecord();
        when(defectRecordRepository.findAll()).thenReturn(Arrays.asList(defect1, defect2));

        mockMvc.perform(get("/api/defects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    public void testGetDefectStats() throws Exception {
        Map<DefectResolution, Long> stats = new HashMap<>();
        stats.put(DefectResolution.REWORK, 10L);
        stats.put(DefectResolution.REPLACE, 5L);

        when(defectService.getDefectStats()).thenReturn(stats);

        mockMvc.perform(get("/api/defects/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.REWORK").value(10))
                .andExpect(jsonPath("$.REPLACE").value(5));
    }

    @Test
    public void testGetDefectsSince() throws Exception {
        String dateStr = "2023-10-01T12:00:00";
        LocalDateTime since = LocalDateTime.parse(dateStr);
        DefectRecord defect = new DefectRecord();
        
        when(defectService.findDefectsSince(since)).thenReturn(Collections.singletonList(defect));

        mockMvc.perform(get("/api/defects/since/{date}", dateStr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    public void testReportDefectWithResolution() throws Exception {
        UUID assemblyInstanceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID confirmedById = UUID.randomUUID();
        String reason = "Scratch on surface";
        DefectResolution resolution = DefectResolution.REWORK;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("assemblyInstanceId", assemblyInstanceId.toString());
        requestBody.put("taskId", taskId.toString());
        requestBody.put("reason", reason);
        requestBody.put("resolution", resolution.name());

        DefectRecord savedDefect = new DefectRecord();

        when(defectService.reportDefect(
                eq(assemblyInstanceId),
                eq(taskId),
                eq(reason),
                eq(confirmedById),
                eq(resolution)
        )).thenReturn(savedDefect);

        mockMvc.perform(post("/api/defects")
                .principal(actingAs(confirmedById))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk());
    }

    // confirmedById now always comes from the authenticated principal, not the body - see
    // TaskControllerTest.actingAs for why .principal(...) is the right way to inject that here.
    private Authentication actingAs(UUID id) {
        return new UsernamePasswordAuthenticationToken(id.toString(), null, java.util.List.of());
    }

    @Test
    public void testReportDefectWithoutResolution() throws Exception {
        UUID assemblyInstanceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID confirmedById = UUID.randomUUID();
        String reason = "Missing part";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("assemblyInstanceId", assemblyInstanceId.toString());
        requestBody.put("taskId", taskId.toString());
        requestBody.put("reason", reason);

        DefectRecord savedDefect = new DefectRecord();

        when(defectService.reportDefect(
                eq(assemblyInstanceId),
                eq(taskId),
                eq(reason),
                eq(confirmedById),
                eq(null)
        )).thenReturn(savedDefect);

        mockMvc.perform(post("/api/defects")
                .principal(actingAs(confirmedById))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk());
    }
}
