package com.example.productionmvp.controller;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.example.productionmvp.model.Pallet;
import com.example.productionmvp.model.PalletMovement;
import com.example.productionmvp.repository.PalletMovementRepository;
import com.example.productionmvp.repository.PalletRepository;
import com.example.productionmvp.service.PalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PalletController.class, excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = {com.example.productionmvp.config.security.SecurityConfig.class, com.example.productionmvp.config.security.JwtAuthenticationFilter.class}), excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class, org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
public class PalletControllerTest {

    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtUtil jwtUtil;
    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.UserDetailsServiceImpl userDetailsService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PalletRepository palletRepository;

    @MockBean
    private PalletService palletService;

    @MockBean
    private PalletMovementRepository palletMovementRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Pallet pallet;
    private PalletMovement palletMovement;
    private UUID palletId;
    private String qrCode;

    @BeforeEach
    void setUp() {
        palletId = UUID.randomUUID();
        qrCode = "PALLET-12345";
        pallet = new Pallet();
        pallet.setId(palletId);
        pallet.setQrCode(qrCode);
        
        palletMovement = new PalletMovement();
        palletMovement.setId(UUID.randomUUID());
    }

    @Test
    void testGetAllPallets() throws Exception {
        Mockito.when(palletRepository.findAll()).thenReturn(Arrays.asList(pallet));

        mockMvc.perform(get("/api/pallets")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(palletId.toString()))
                .andExpect(jsonPath("$[0].qrCode").value(qrCode));
    }

    @Test
    void testGetPalletById_Found() throws Exception {
        Mockito.when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));

        mockMvc.perform(get("/api/pallets/{id}", palletId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(palletId.toString()))
                .andExpect(jsonPath("$.qrCode").value(qrCode));
    }

    @Test
    void testGetPalletById_NotFound() throws Exception {
        Mockito.when(palletRepository.findById(palletId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/pallets/{id}", palletId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetPalletByQr() throws Exception {
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("id", palletId.toString());
        responseMap.put("qrCode", qrCode);

        Mockito.when(palletService.getPalletByQr(qrCode)).thenReturn(responseMap);

        mockMvc.perform(get("/api/pallets/qr/{qrCode}", qrCode)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(palletId.toString()))
                .andExpect(jsonPath("$.qrCode").value(qrCode));
    }

    @Test
    void testGenerateQrCodeImage_Found() throws Exception {
        byte[] imageBytes = new byte[]{1, 2, 3};
        Mockito.when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));
        Mockito.when(palletService.generateQrCodeImage(qrCode)).thenReturn(imageBytes);

        mockMvc.perform(get("/api/pallets/{id}/qr-image", palletId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(imageBytes));
    }

    @Test
    void testGenerateQrCodeImage_NotFound() throws Exception {
        Mockito.when(palletRepository.findById(palletId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/pallets/{id}/qr-image", palletId))
                .andExpect(status().is(500));
    }

    @Test
    void testCreatePallet() throws Exception {
        UUID productId = UUID.randomUUID();
        String category = "BOX";
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("productId", productId.toString());
        requestBody.put("category", category);

        Mockito.when(palletService.createPallet(productId, null, category)).thenReturn(pallet);

        mockMvc.perform(post("/api/pallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(palletId.toString()))
                .andExpect(jsonPath("$.qrCode").value(qrCode));
    }

    @Test
    void testCreatePallet_WithoutProductId() throws Exception {
        String category = "DEFAULT";
        
        Map<String, Object> requestBody = new HashMap<>();

        Mockito.when(palletService.createPallet(null, null, category)).thenReturn(pallet);

        mockMvc.perform(post("/api/pallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(palletId.toString()))
                .andExpect(jsonPath("$.qrCode").value(qrCode));
    }

    @Test
    void testAddAssemblyToPallet() throws Exception {
        UUID assemblyInstanceId = UUID.randomUUID();
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("assemblyInstanceId", assemblyInstanceId.toString());

        Mockito.when(palletService.addAssemblyToPallet(palletId, assemblyInstanceId)).thenReturn(pallet);

        mockMvc.perform(post("/api/pallets/{id}/add-assembly", palletId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(palletId.toString()))
                .andExpect(jsonPath("$.qrCode").value(qrCode));
    }

    @Test
    void testMovePallet() throws Exception {
        UUID toPostId = UUID.randomUUID();
        UUID movedByWorkerId = UUID.randomUUID();
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("toPostId", toPostId.toString());
        requestBody.put("movedByWorkerId", movedByWorkerId.toString());

        Mockito.when(palletService.movePallet(palletId, toPostId, movedByWorkerId)).thenReturn(palletMovement);

        mockMvc.perform(post("/api/pallets/{id}/move", palletId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk());
    }

    @Test
    void testGetPalletHistory() throws Exception {
        Mockito.when(palletMovementRepository.findByPalletIdOrderByMovedAtDesc(palletId))
                .thenReturn(Arrays.asList(palletMovement));

        mockMvc.perform(get("/api/pallets/{id}/history", palletId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
