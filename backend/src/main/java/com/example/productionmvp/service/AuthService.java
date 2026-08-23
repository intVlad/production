package com.example.productionmvp.service;

import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.model.Worker;
import com.example.productionmvp.repository.WorkerRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final WorkerRepository workerRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(WorkerRepository workerRepository) {
        this.workerRepository = workerRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Transactional(readOnly = true)
    public Worker loginWithPin(UUID workerId, String pin) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new EntityNotFoundException("Worker not found"));
        
        if (worker.getPinHash() != null && passwordEncoder.matches(pin, worker.getPinHash())) {
            return worker;
        }
        
        throw new RuntimeException("Invalid PIN");
    }

    @Transactional(readOnly = true)
    public Worker loginWithPin(String pin) {
        java.util.List<Worker> workers = workerRepository.findAll();
        for (Worker worker : workers) {
            if (worker.getPinHash() != null && passwordEncoder.matches(pin, worker.getPinHash())) {
                return worker;
            }
        }
        throw new RuntimeException("Invalid PIN");
    }

    @Transactional(readOnly = true)
    public Worker loginWithQrBadge(String qrBadgeCode) {
        return workerRepository.findByQrBadgeCode(qrBadgeCode)
                .orElseThrow(() -> new EntityNotFoundException("Worker not found for QR Badge"));
    }

    @Transactional
    public void setWorkerPin(UUID workerId, String pin) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new EntityNotFoundException("Worker not found"));

        worker.setPinHash(passwordEncoder.encode(pin));
        workerRepository.save(worker);
    }

    @Transactional
    public String generateQrBadge(UUID workerId) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new EntityNotFoundException("Worker not found"));

        String qrBadgeCode = "W-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        worker.setQrBadgeCode(qrBadgeCode);
        workerRepository.save(worker);

        return qrBadgeCode;
    }

    @Transactional(readOnly = true)
    public java.util.List<Map<String, String>> getWorkersForLogin() {
        return workerRepository.findAll().stream()
                .filter(w -> w.getSystemRole() == com.example.productionmvp.model.SystemRole.WORKER)
                .map(w -> Map.of(
                        "id", w.getId().toString(),
                        "name", w.getName()
                ))
                .toList();
    }
}
