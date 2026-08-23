package com.example.productionmvp.controller;

import com.example.productionmvp.model.OutsourceRecord;
import com.example.productionmvp.repository.OutsourceRecordRepository;
import com.example.productionmvp.service.OutsourceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/outsource")
@CrossOrigin(origins = "*")
public class OutsourceController {
    private final OutsourceRecordRepository outsourceRecordRepository;
    private final OutsourceService outsourceService;

    public OutsourceController(OutsourceRecordRepository outsourceRecordRepository, OutsourceService outsourceService) {
        this.outsourceRecordRepository = outsourceRecordRepository;
        this.outsourceService = outsourceService;
    }

    @GetMapping
    public ResponseEntity<List<OutsourceRecord>> getAllRecords() {
        return ResponseEntity.ok(outsourceRecordRepository.findAll());
    }

    @GetMapping("/active")
    public ResponseEntity<List<OutsourceRecord>> getActiveRecords() {
        return ResponseEntity.ok(outsourceService.findActiveRecords());
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<OutsourceRecord>> getOverdueRecords() {
        return ResponseEntity.ok(outsourceService.findOverdue());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OutsourceRecord> getRecordById(@PathVariable UUID id) {
        return outsourceRecordRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('DISPATCHER') or hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<OutsourceRecord> createRecord(@RequestBody com.example.productionmvp.dto.OutsourceRequestDTO body) {
        String partner = body.getPartner() != null ? body.getPartner() : "Невідомо";
        String workType = body.getWorkType() != null ? body.getWorkType() : "Невідомо";
        
        List<UUID> assemblyInstanceIds = body.getAssemblyInstanceIds() != null ? body.getAssemblyInstanceIds() : new java.util.ArrayList<>();
        
        LocalDate expectedReturnDate = body.getExpectedReturnDate();
        if (expectedReturnDate == null) {
            expectedReturnDate = LocalDate.now().plusDays(7);
        }

        return ResponseEntity.ok(outsourceService.createRecord(partner, workType, assemblyInstanceIds, expectedReturnDate.atStartOfDay()));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasRole('DISPATCHER') or hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<OutsourceRecord> sendOut(@PathVariable UUID id) {
        return ResponseEntity.ok(outsourceService.sendOut(id));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasRole('DISPATCHER') or hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<OutsourceRecord> receiveBack(@PathVariable UUID id, @RequestBody(required = false) com.example.productionmvp.dto.OutsourceRequestDTO body) {
        UUID receivedByWorkerId = null;
        if (body != null) {
            receivedByWorkerId = body.getReceivedByWorkerId();
        }
        return ResponseEntity.ok(outsourceService.receiveBack(id, receivedByWorkerId));
    }
}
