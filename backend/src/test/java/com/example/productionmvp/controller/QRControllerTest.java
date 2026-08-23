package com.example.productionmvp.controller;

import com.example.productionmvp.model.Pallet;
import com.example.productionmvp.model.Worker;
import com.example.productionmvp.repository.PalletRepository;
import com.example.productionmvp.repository.PostRepository;
import com.example.productionmvp.repository.WorkerRepository;
import com.example.productionmvp.service.PalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = QRController.class, excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = {com.example.productionmvp.config.security.SecurityConfig.class, com.example.productionmvp.config.security.JwtAuthenticationFilter.class}), excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class, org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
public class QRControllerTest {

    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtUtil jwtUtil;
    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.UserDetailsServiceImpl userDetailsService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.productionmvp.config.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PalletService palletService;

    @MockBean
    private PalletRepository palletRepository;

    @MockBean
    private PostRepository postRepository;

    @MockBean
    private WorkerRepository workerRepository;

    private byte[] mockImageBytes;

    @BeforeEach
    void setUp() {
        mockImageBytes = new byte[]{1, 2, 3, 4, 5};
    }

    @Test
    void testGenerateQr() throws Exception {
        when(palletService.generateQrCodeImage("test-data")).thenReturn(mockImageBytes);

        mockMvc.perform(get("/api/qr/generate")
                .param("data", "test-data"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(mockImageBytes));
    }

    @Test
    void testGeneratePalletQr() throws Exception {
        UUID palletId = UUID.randomUUID();
        Pallet pallet = new Pallet();
        pallet.setId(palletId);
        pallet.setQrCode("pallet-qr-code");

        when(palletRepository.findById(palletId)).thenReturn(Optional.of(pallet));
        when(palletService.generateQrCodeImage("pallet-qr-code")).thenReturn(mockImageBytes);

        mockMvc.perform(get("/api/qr/pallet/{palletId}", palletId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(mockImageBytes));
    }

    @Test
    void testGeneratePostQr() throws Exception {
        UUID postId = UUID.randomUUID();

        when(palletService.generateQrCodeImage(postId.toString())).thenReturn(mockImageBytes);

        mockMvc.perform(get("/api/qr/post/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(mockImageBytes));
    }

    @Test
    void testGenerateWorkerQrBadge_WithQrBadgeCode() throws Exception {
        UUID workerId = UUID.randomUUID();
        Worker worker = new Worker();
        worker.setId(workerId);
        worker.setQrBadgeCode("custom-badge-code");

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(palletService.generateQrCodeImage("custom-badge-code")).thenReturn(mockImageBytes);

        mockMvc.perform(get("/api/qr/worker/{workerId}", workerId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(mockImageBytes));
    }

    @Test
    void testGenerateWorkerQrBadge_WithoutQrBadgeCode() throws Exception {
        UUID workerId = UUID.randomUUID();
        Worker worker = new Worker();
        worker.setId(workerId);
        worker.setQrBadgeCode(null);

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(palletService.generateQrCodeImage(workerId.toString())).thenReturn(mockImageBytes);

        mockMvc.perform(get("/api/qr/worker/{workerId}", workerId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(mockImageBytes));
    }
}
