package com.example.productionmvp.controller;

import com.example.productionmvp.model.Pallet;
import com.example.productionmvp.model.Worker;
import com.example.productionmvp.repository.PalletRepository;
import com.example.productionmvp.repository.PostRepository;
import com.example.productionmvp.repository.WorkerRepository;
import com.example.productionmvp.service.PalletService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/qr")
@CrossOrigin(origins = "*")
public class QRController {
    private final PalletService palletService;
    private final PalletRepository palletRepository;
    private final PostRepository postRepository;
    private final WorkerRepository workerRepository;

    public QRController(PalletService palletService, PalletRepository palletRepository, 
                        PostRepository postRepository, WorkerRepository workerRepository) {
        this.palletService = palletService;
        this.palletRepository = palletRepository;
        this.postRepository = postRepository;
        this.workerRepository = workerRepository;
    }

    @GetMapping(value = "/generate", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> generateQr(@RequestParam String data) {
        byte[] image = palletService.generateQrCodeImage(data);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(image);
    }

    @GetMapping(value = "/pallet/{palletId}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> generatePalletQr(@PathVariable UUID palletId) {
        Pallet pallet = palletRepository.findById(palletId).orElseThrow(() -> new com.example.productionmvp.exception.EntityNotFoundException("Піддон не знайдено"));
        byte[] image = palletService.generateQrCodeImage(pallet.getQrCode());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(image);
    }

    @GetMapping(value = "/post/{postId}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> generatePostQr(@PathVariable UUID postId) {
        byte[] image = palletService.generateQrCodeImage(postId.toString());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(image);
    }

    // Defense in depth: QR-badge login is dead code today (AuthService.generateQrBadge is
    // never called from anywhere, so every worker's qrBadgeCode is null in practice), but this
    // route renders whatever badge value a worker does have into a scannable image - the
    // moment that feature is wired up, an ungated version of this becomes a full
    // login-as-anyone image any authenticated session could fetch for any workerId.
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    @GetMapping(value = "/worker/{workerId}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> generateWorkerQrBadge(@PathVariable UUID workerId) {
        Worker worker = workerRepository.findById(workerId).orElseThrow(() -> new com.example.productionmvp.exception.EntityNotFoundException("Працівника не знайдено"));
        String badge = worker.getQrBadgeCode() != null ? worker.getQrBadgeCode() : workerId.toString();
        byte[] image = palletService.generateQrCodeImage(badge);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(image);
    }
}
