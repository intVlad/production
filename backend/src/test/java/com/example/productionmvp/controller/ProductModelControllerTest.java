package com.example.productionmvp.controller;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.example.productionmvp.repository.ProductModelRepository;
import com.example.productionmvp.repository.AssemblyRepository;
import com.example.productionmvp.config.security.JwtUtil;
import com.example.productionmvp.config.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Collections;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductModelController.class, excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = {com.example.productionmvp.config.security.SecurityConfig.class, com.example.productionmvp.config.security.JwtAuthenticationFilter.class}), excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class, org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
public class ProductModelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductModelRepository productModelRepository;

    @MockBean
    private AssemblyRepository assemblyRepository;

    @MockBean
    private JwtUtil jwtUtil;
    
    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private com.example.productionmvp.config.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void testGetAllModels() throws Exception {
        when(productModelRepository.findAll()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/models"))
                .andExpect(status().isOk());
    }
}
