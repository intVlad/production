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

    // Used by the "Менеджер" and "ТВ-Дашборд" login cards, which ask for a PIN and nothing
    // else - so here the PIN alone has to identify a person. Returning the first match would
    // silently log someone in as whoever happened to come first in findAll() order: a newly
    // created manager whose PIN collided with an existing worker got that worker's identity
    // (and was then rejected by the frontend's role check, locking them out permanently),
    // and two managers sharing a PIN would have every audited action attributed to one of
    // them. setWorkerPin now rejects duplicates so this should be unreachable, but data
    // predating that check can still contain collisions - fail closed rather than guess.
    @Transactional(readOnly = true)
    public Worker loginWithPin(String pin) {
        java.util.List<Worker> matches = workerRepository.findAll().stream()
                .filter(w -> w.getPinHash() != null && passwordEncoder.matches(pin, w.getPinHash()))
                .toList();

        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "Цей PIN використовують кілька облікових записів. Зверніться до адміністратора, щоб змінити PIN.");
        }
        if (matches.isEmpty()) {
            throw new RuntimeException("Invalid PIN");
        }
        return matches.get(0);
    }

    // A PIN is an identity on the PIN-only login cards, so it has to be unique across every
    // account - not just unique among managers. Callers surface this as a 409 so the manager
    // creating the account is told to pick another PIN, instead of silently creating an
    // account that can never log in.
    @Transactional(readOnly = true)
    public void assertPinAvailable(String pin, UUID excludeWorkerId) {
        boolean taken = workerRepository.findAll().stream()
                .filter(w -> excludeWorkerId == null || !w.getId().equals(excludeWorkerId))
                .anyMatch(w -> w.getPinHash() != null && passwordEncoder.matches(pin, w.getPinHash()));
        if (taken) {
            throw new IllegalStateException("Цей PIN вже використовує інший обліковий запис. Оберіть інший PIN.");
        }
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

        assertPinAvailable(pin, workerId);

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
