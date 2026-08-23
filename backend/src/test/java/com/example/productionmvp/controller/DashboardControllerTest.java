package com.example.productionmvp.controller;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.example.productionmvp.dto.DashboardResponseDTO;
import com.example.productionmvp.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DashboardController.class, excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = {com.example.productionmvp.config.security.SecurityConfig.class, com.example.productionmvp.config.security.JwtAuthenticationFilter.class}), excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class, org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
public class DashboardControllerTest {


    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtUtil jwtUtil;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.UserDetailsServiceImpl userDetailsService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @Test
    public void testGetDashboardData_NoParams() throws Exception {
        DashboardResponseDTO mockData = new DashboardResponseDTO();
        mockData.setActiveSeries(10);
        mockData.setActiveTasksCount(3);

        when(dashboardService.getDashboardData(null, null, null, null)).thenReturn(mockData);

        mockMvc.perform(get("/api/dashboard")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSeries").value(10))
                .andExpect(jsonPath("$.activeTasksCount").value(3));
    }

    @Test
    public void testGetDashboardData_WithParams() throws Exception {
        DashboardResponseDTO mockData = new DashboardResponseDTO();
        mockData.setBlockedTasks(2);

        UUID seriesId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        String status = "ACTIVE";

        when(dashboardService.getDashboardData(seriesId, workerId, status, null)).thenReturn(mockData);

        mockMvc.perform(get("/api/dashboard")
                .param("seriesId", seriesId.toString())
                .param("workerId", workerId.toString())
                .param("status", status)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedTasks").value(2));
    }

    @Test
    public void testDirectMethodCallToEnsureConstructorCoverage() {
        // This test directly instantiates the controller to ensure constructor coverage
        // in case the @WebMvcTest context caching hides it from simple coverage tools.
        DashboardService mockService = mock(DashboardService.class);
        DashboardController controller = new DashboardController(mockService);

        DashboardResponseDTO expectedResponse = new DashboardResponseDTO();
        expectedResponse.setActiveWorkers(5);

        UUID seriesId = UUID.randomUUID();
        when(mockService.getDashboardData(seriesId, null, null, null)).thenReturn(expectedResponse);

        ResponseEntity<DashboardResponseDTO> response = controller.getDashboardData(seriesId, null, null, null);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(expectedResponse, response.getBody());
    }
}
