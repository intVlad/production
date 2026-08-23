package com.example.productionmvp.controller;

import com.example.productionmvp.model.Pallet;
import com.example.productionmvp.model.PalletMovement;
import com.example.productionmvp.repository.PalletRepository;
import com.example.productionmvp.service.PalletService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/pallets")
@CrossOrigin(origins = "*")
public class PalletController {
    private final PalletRepository palletRepository;
    private final PalletService palletService;
    private final com.example.productionmvp.repository.PalletMovementRepository palletMovementRepository;

    public PalletController(PalletRepository palletRepository, PalletService palletService, com.example.productionmvp.repository.PalletMovementRepository palletMovementRepository) {
        this.palletRepository = palletRepository;
        this.palletService = palletService;
        this.palletMovementRepository = palletMovementRepository;
    }

    @GetMapping
    public ResponseEntity<List<Pallet>> getAllPallets() {
        return ResponseEntity.ok(palletRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pallet> getPalletById(@PathVariable UUID id) {
        return palletRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/qr/{qrCode}")
    public ResponseEntity<Map<String, Object>> getPalletByQr(@PathVariable String qrCode) {
        return ResponseEntity.ok(palletService.getPalletByQr(qrCode));
    }

    @GetMapping(value = "/{id}/qr-image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> generateQrCodeImage(@PathVariable UUID id) {
        Pallet pallet = palletRepository.findById(id).orElseThrow(() -> new com.example.productionmvp.exception.EntityNotFoundException("Піддон не знайдено"));
        byte[] image = palletService.generateQrCodeImage(pallet.getQrCode());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(image);
    }

    // Unlike every other mutating controller in this codebase, these three had no role check
    // at all - just "authenticated," which let any WORKER token create/link/move pallets
    // (verified live: a plain worker session could create a pallet with zero role gate).
    @PreAuthorize("hasRole('WORKER') or hasRole('DISPATCHER') or hasRole('MANAGER') or hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Pallet> createPallet(@RequestBody com.example.productionmvp.dto.PalletRequestDTO body) {
        UUID productId = body.getProductId();
        UUID productInstanceId = body.getProductInstanceId();
        String category = body.getCategory() != null ? body.getCategory() : "DEFAULT";
        return ResponseEntity.ok(palletService.createPallet(productId, productInstanceId, category));
    }

    @PreAuthorize("hasRole('WORKER') or hasRole('DISPATCHER') or hasRole('MANAGER') or hasRole('ADMIN')")
    @PostMapping("/{id}/add-assembly")
    public ResponseEntity<Pallet> addAssemblyToPallet(@PathVariable UUID id, @RequestBody com.example.productionmvp.dto.PalletRequestDTO body) {
        UUID assemblyInstanceId = body.getAssemblyInstanceId();
        return ResponseEntity.ok(palletService.addAssemblyToPallet(id, assemblyInstanceId));
    }

    @PreAuthorize("hasRole('WORKER') or hasRole('DISPATCHER') or hasRole('MANAGER') or hasRole('ADMIN')")
    @PostMapping("/{id}/move")
    public ResponseEntity<PalletMovement> movePallet(@PathVariable UUID id, @RequestBody com.example.productionmvp.dto.PalletRequestDTO body) {
        UUID toPostId = body.getToPostId();
        UUID movedByWorkerId = body.getMovedByWorkerId();
        return ResponseEntity.ok(palletService.movePallet(id, toPostId, movedByWorkerId));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<PalletMovement>> getPalletHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(palletMovementRepository.findByPalletIdOrderByMovedAtDesc(id));
    }
}
