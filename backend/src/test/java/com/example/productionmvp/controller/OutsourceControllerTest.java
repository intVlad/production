package com.example.productionmvp.controller;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.example.productionmvp.model.OutsourceRecord;
import com.example.productionmvp.repository.OutsourceRecordRepository;
import com.example.productionmvp.service.OutsourceService;
import com.example.productionmvp.config.security.JwtUtil;
import com.example.productionmvp.config.security.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OutsourceController.class, excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = {com.example.productionmvp.config.security.SecurityConfig.class, com.example.productionmvp.config.security.JwtAuthenticationFilter.class}), excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class, org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
public class OutsourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OutsourceRecordRepository outsourceRecordRepository;

    @MockBean
    private OutsourceService outsourceService;

    @MockBean
    private JwtUtil jwtUtil;
    
    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private com.example.productionmvp.config.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testGetAllRecords() throws Exception {
        OutsourceRecord record = new OutsourceRecord();
        record.setId(UUID.randomUUID());
        record.setPartner("Partner A");

        when(outsourceRecordRepository.findAll()).thenReturn(List.of(record));

        mockMvc.perform(get("/api/outsource"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].partner").value("Partner A"));
    }

    @Test
    void testGetActiveRecords() throws Exception {
        OutsourceRecord record = new OutsourceRecord();
        record.setId(UUID.randomUUID());
        record.setPartner("Active Partner");

        when(outsourceService.findActiveRecords()).thenReturn(List.of(record));

        mockMvc.perform(get("/api/outsource/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].partner").value("Active Partner"));
    }

    @Test
    void testGetOverdueRecords() throws Exception {
        OutsourceRecord record = new OutsourceRecord();
        record.setId(UUID.randomUUID());
        record.setPartner("Overdue Partner");

        when(outsourceService.findOverdue()).thenReturn(List.of(record));

        mockMvc.perform(get("/api/outsource/overdue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].partner").value("Overdue Partner"));
    }

    @Test
    void testGetRecordById_Found() throws Exception {
        UUID id = UUID.randomUUID();
        OutsourceRecord record = new OutsourceRecord();
        record.setId(id);
        record.setPartner("Found Partner");

        when(outsourceRecordRepository.findById(id)).thenReturn(Optional.of(record));

        mockMvc.perform(get("/api/outsource/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partner").value("Found Partner"));
    }

    @Test
    void testGetRecordById_NotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(outsourceRecordRepository.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/outsource/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateRecord() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("partner", "New Partner");
        body.put("workType", "Type A");
        body.put("assemblyInstanceIds", Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        body.put("expectedReturnDate", LocalDate.now().plusDays(5).toString());

        OutsourceRecord record = new OutsourceRecord();
        record.setId(UUID.randomUUID());
        record.setPartner("New Partner");

        when(outsourceService.createRecord(eq("New Partner"), eq("Type A"), any(List.class), any(LocalDateTime.class)))
                .thenReturn(record);

        mockMvc.perform(post("/api/outsource")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partner").value("New Partner"));
    }

    @Test
    void testSendOut() throws Exception {
        UUID id = UUID.randomUUID();
        OutsourceRecord record = new OutsourceRecord();
        record.setId(id);
        record.setPartner("Send Partner");

        when(outsourceService.sendOut(id)).thenReturn(record);

        mockMvc.perform(post("/api/outsource/" + id + "/send"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partner").value("Send Partner"));
    }

    @Test
    void testReceiveBack() throws Exception {
        UUID id = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        
        Map<String, Object> body = new HashMap<>();
        body.put("receivedByWorkerId", workerId.toString());

        OutsourceRecord record = new OutsourceRecord();
        record.setId(id);
        record.setPartner("Receive Partner");

        when(outsourceService.receiveBack(id, workerId)).thenReturn(record);

        mockMvc.perform(post("/api/outsource/" + id + "/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partner").value("Receive Partner"));
    }
}
