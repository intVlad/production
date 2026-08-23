package com.example.productionmvp.controller;

import com.example.productionmvp.model.DefectRecord;
import com.example.productionmvp.repository.DefectRecordRepository;
import com.example.productionmvp.service.DefectService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/defects")
@CrossOrigin(origins = "*")
public class DefectController {
    private final DefectRecordRepository defectRecordRepository;
    private final DefectService defectService;

    public DefectController(DefectRecordRepository defectRecordRepository, DefectService defectService) {
        this.defectRecordRepository = defectRecordRepository;
        this.defectService = defectService;
    }

    @GetMapping
    public ResponseEntity<List<DefectRecord>> getAllDefects() {
        return ResponseEntity.ok(defectRecordRepository.findAll());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getDefectStats() {
        return ResponseEntity.ok(defectService.getDefectStats());
    }

    @GetMapping("/since/{date}")
    public ResponseEntity<List<DefectRecord>> getDefectsSince(@PathVariable String date) {
        LocalDateTime since = LocalDateTime.parse(date);
        return ResponseEntity.ok(defectService.findDefectsSince(since));
    }

    // confirmedById always comes from the authenticated caller, never the request body - a
    // client-supplied id let any WORKER attribute a write-off "confirmation" to a Manager or
    // Admin who never touched it, defeating the one accountability control ТЗ §6 requires
    // around scrapping material.
    @PreAuthorize("hasRole('WORKER') or hasRole('MANAGER') or hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<DefectRecord> reportDefect(@RequestBody com.example.productionmvp.dto.DefectRequestDTO body, Authentication authentication) {
        UUID assemblyInstanceId = body.getAssemblyInstanceId();
        UUID taskId = body.getTaskId();
        String reason = body.getReason() != null ? body.getReason() : "Не вказано";
        UUID confirmedById = UUID.fromString(authentication.getName());
        com.example.productionmvp.model.DefectResolution resEnum = body.getResolution();

        return ResponseEntity.ok(defectService.reportDefect(assemblyInstanceId, taskId, reason, confirmedById, resEnum));
    }
}
