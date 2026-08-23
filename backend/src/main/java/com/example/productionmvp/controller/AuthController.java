package com.example.productionmvp.controller;

import com.example.productionmvp.model.Worker;
import com.example.productionmvp.service.AuthService;
import com.example.productionmvp.service.LoginRateLimiterService;
import com.example.productionmvp.config.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final LoginRateLimiterService rateLimiter;

    public AuthController(AuthService authService, JwtUtil jwtUtil, LoginRateLimiterService rateLimiter) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
        this.rateLimiter = rateLimiter;
    }

    // This deployment has no reverse proxy in front of the backend (docker-compose.yml exposes
    // 8080 directly), so X-Forwarded-For is never a trustworthy value here - it's just a header
    // any client can set to whatever it wants. Trusting it let the login rate-limiter's lockout
    // be bypassed on the very next request by sending a different X-Forwarded-For each time.
    // If a real reverse proxy is ever added in front of this service, that proxy's own IP needs
    // to be allowlisted before this can safely read the header again.
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    @PostMapping("/login/pin")
    public ResponseEntity<?> loginWithPin(@RequestBody com.example.productionmvp.dto.AuthRequestDTO body, HttpServletRequest request) {
        UUID workerId = body.getWorkerId();
        List<String> keys = new ArrayList<>();
        keys.add("ip:" + clientIp(request));
        if (workerId != null) keys.add("worker:" + workerId);

        try {
            keys.forEach(rateLimiter::checkAllowed);
        } catch (LoginRateLimiterService.TooManyAttemptsException e) {
            return ResponseEntity.status(429).body(Map.of("error", e.getMessage()));
        }

        try {
            String pin = body.getPin();
            if (pin == null) throw new IllegalArgumentException("PIN required");

            Worker worker;
            if (workerId != null) {
                worker = authService.loginWithPin(workerId, pin);
            } else {
                worker = authService.loginWithPin(pin);
            }
            String token = jwtUtil.generateToken(worker.getId().toString(), "ROLE_" + worker.getSystemRole().name());
            keys.forEach(rateLimiter::recordSuccess);

            return ResponseEntity.ok(Map.of(
                "token", token,
                "worker", worker
            ));
        } catch (Exception e) {
            keys.forEach(rateLimiter::recordFailure);
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
    }

    @PostMapping("/login/qr")
    public ResponseEntity<?> loginWithQrBadge(@RequestBody com.example.productionmvp.dto.AuthRequestDTO body, HttpServletRequest request) {
        String ipKey = "ip:" + clientIp(request);
        try {
            rateLimiter.checkAllowed(ipKey);
        } catch (LoginRateLimiterService.TooManyAttemptsException e) {
            return ResponseEntity.status(429).body(Map.of("error", e.getMessage()));
        }

        try {
            String qrBadgeCode = body.getQrBadgeCode();
            if (qrBadgeCode == null) throw new IllegalArgumentException("QR code required");
            Worker worker = authService.loginWithQrBadge(qrBadgeCode);
            String token = jwtUtil.generateToken(worker.getId().toString(), "ROLE_" + worker.getSystemRole().name());
            rateLimiter.recordSuccess(ipKey);

            return ResponseEntity.ok(Map.of(
                "token", token,
                "worker", worker
            ));
        } catch (Exception e) {
            rateLimiter.recordFailure(ipKey);
            return ResponseEntity.status(401).body(Map.of("error", "Invalid QR code"));
        }
    }

    @PostMapping("/set-pin")
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<Void> setWorkerPin(@RequestBody com.example.productionmvp.dto.AuthRequestDTO body) {
        UUID workerId = body.getWorkerId();
        String pin = body.getPin();
        if (workerId == null || pin == null) return ResponseEntity.badRequest().build();
        authService.setWorkerPin(workerId, pin);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/workers")
    public ResponseEntity<?> getWorkersForLogin() {
        return ResponseEntity.ok(authService.getWorkersForLogin());
    }
}
