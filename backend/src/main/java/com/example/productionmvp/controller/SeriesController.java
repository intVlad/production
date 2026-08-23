package com.example.productionmvp.controller;

import com.example.productionmvp.model.Series;
import com.example.productionmvp.model.SeriesPriority;
import com.example.productionmvp.repository.SeriesRepository;
import com.example.productionmvp.service.SeriesService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/series")
@CrossOrigin(origins = "*")
public class SeriesController {
    private final SeriesRepository seriesRepository;
    private final SeriesService seriesService;

    public SeriesController(SeriesRepository seriesRepository, SeriesService seriesService) {
        this.seriesRepository = seriesRepository;
        this.seriesService = seriesService;
    }

    @GetMapping
    public ResponseEntity<List<com.example.productionmvp.dto.SeriesDTO>> getAllSeries() {
        return ResponseEntity.ok(seriesRepository.findAll().stream()
                .map(com.example.productionmvp.dto.SeriesDTO::new)
                .collect(java.util.stream.Collectors.toList()));
    }

    @GetMapping("/active")
    public ResponseEntity<List<com.example.productionmvp.dto.SeriesDTO>> getActiveSeries() {
        return ResponseEntity.ok(seriesService.findAllActive().stream()
                .map(com.example.productionmvp.dto.SeriesDTO::new)
                .collect(java.util.stream.Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<com.example.productionmvp.dto.SeriesDTO> getSeriesById(@PathVariable UUID id) {
        return ResponseEntity.ok(new com.example.productionmvp.dto.SeriesDTO(seriesService.findById(id)));
    }

    @GetMapping("/{id}/progress")
    public ResponseEntity<com.example.productionmvp.dto.SeriesProgressDTO> getSeriesProgress(@PathVariable UUID id) {
        return ResponseEntity.ok(seriesService.getSeriesProgress(id));
    }

    @GetMapping("/{id}/products")
    public ResponseEntity<List<com.example.productionmvp.dto.ProductInstanceSummaryDTO>> getSeriesProducts(@PathVariable UUID id) {
        return ResponseEntity.ok(seriesService.getProductInstances(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER') or hasRole('DISPATCHER') or hasRole('ADMIN')")
    public ResponseEntity<com.example.productionmvp.dto.SeriesDTO> createSeries(@RequestBody com.example.productionmvp.dto.SeriesRequestDTO body) {
        UUID modelId = body.getModelId();
        String number = body.getNumber();
        int plannedQuantity = body.getPlannedQuantity();
        String priority = body.getPriority() != null ? body.getPriority() : "MEDIUM";
        SeriesPriority seriesPriority = SeriesPriority.valueOf(priority.toUpperCase());
        LocalDate plannedStartDate = body.getPlannedStartDate();
        LocalDate plannedEndDate = body.getPlannedEndDate();
        
        java.time.LocalDateTime start = plannedStartDate != null ? plannedStartDate.atStartOfDay() : java.time.LocalDateTime.now();
        java.time.LocalDateTime end = plannedEndDate != null ? plannedEndDate.atStartOfDay() : java.time.LocalDateTime.now().plusMonths(1);

        Series series = seriesService.createSeries(modelId, number, plannedQuantity, seriesPriority, start, end);
        return ResponseEntity.ok(new com.example.productionmvp.dto.SeriesDTO(series));
    }
}
