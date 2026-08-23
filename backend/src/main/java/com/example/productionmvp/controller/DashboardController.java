package com.example.productionmvp.controller;

import com.example.productionmvp.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ResponseEntity<com.example.productionmvp.dto.DashboardResponseDTO> getDashboardData(
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.util.UUID seriesId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.util.UUID workerId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String status,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String stage) {
        return ResponseEntity.ok(dashboardService.getDashboardData(seriesId, workerId, status, stage));
    }
}
