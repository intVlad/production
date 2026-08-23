package com.example.productionmvp.controller;

import com.example.productionmvp.dto.HistoryEventDTO;
import com.example.productionmvp.repository.HistoryEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

// Checklist §39 ("Чи є аудит-лог для дій керівника і диспетчера?") and §65-68 (full history,
// хто/коли/що, порівняння нормативного і фактичного часу) - the dashboard's recentHistory only
// ever showed the last 10 events with no filters, which doesn't answer either question for
// anything beyond the most recent handful of actions.
@RestController
@RequestMapping("/api/history")
@CrossOrigin(origins = "*")
public class HistoryController {

    private final HistoryEventRepository historyEventRepository;

    public HistoryController(HistoryEventRepository historyEventRepository) {
        this.historyEventRepository = historyEventRepository;
    }

    @PreAuthorize("hasRole('MANAGER') or hasRole('DISPATCHER') or hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<HistoryEventDTO>> getHistory(
            @RequestParam(required = false) UUID workerId,
            @RequestParam(required = false) String since,
            @RequestParam(required = false) String until,
            @RequestParam(required = false, defaultValue = "200") int limit) {
        LocalDateTime sinceDt = (since != null && !since.isEmpty()) ? LocalDateTime.parse(since) : null;
        LocalDateTime untilDt = (until != null && !until.isEmpty()) ? LocalDateTime.parse(until) : null;
        int cappedLimit = Math.min(Math.max(limit, 1), 500);

        List<HistoryEventDTO> events = historyEventRepository
                .findFiltered(workerId, sinceDt, untilDt, PageRequest.of(0, cappedLimit))
                .stream()
                .map(HistoryEventDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(events);
    }
}
