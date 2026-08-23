package com.example.productionmvp.config.security;

import com.example.productionmvp.model.Worker;
import com.example.productionmvp.repository.WorkerRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final WorkerRepository workerRepository;

    public UserDetailsServiceImpl(WorkerRepository workerRepository) {
        this.workerRepository = workerRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Here username is actually the Worker ID (UUID) as a string
        try {
            java.util.UUID workerId = java.util.UUID.fromString(username);
            Worker worker = workerRepository.findById(workerId)
                    .orElseThrow(() -> new UsernameNotFoundException("Worker not found: " + username));

            return new User(
                    worker.getId().toString(),
                    worker.getPinHash() != null ? worker.getPinHash() : "", // fallback for workers without PIN
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + worker.getSystemRole().name()))
            );
        } catch (IllegalArgumentException e) {
            // It might be a QR code login, let's try finding by QR code
            Worker worker = workerRepository.findByQrBadgeCode(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Worker not found for QR: " + username));
            
            return new User(
                    worker.getId().toString(),
                    "", // no password needed for QR, handled by filter/auth controller
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + worker.getSystemRole().name()))
            );
        }
    }
}
