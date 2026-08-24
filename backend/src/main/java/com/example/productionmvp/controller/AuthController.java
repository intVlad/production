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

    // Which roles each sign-in screen may admit. Kept here rather than taken from the request,
    // so the caller names the screen and the server decides what that screen is allowed to do.
    // ТЗ §12.2 groups Диспетчер/Керівник/Постачальник into one "web login" class: all three
    // land on manager.html, which then shows and hides sections by role.
    private static final Map<String, java.util.Set<com.example.productionmvp.model.SystemRole>> LOGIN_CONTEXTS = Map.of(
            "MANAGER", java.util.EnumSet.of(
                    com.example.productionmvp.model.SystemRole.MANAGER,
                    com.example.productionmvp.model.SystemRole.ADMIN,
                    com.example.productionmvp.model.SystemRole.DISPATCHER,
                    com.example.productionmvp.model.SystemRole.SUPPLIER),
            "WORKER", java.util.EnumSet.of(com.example.productionmvp.model.SystemRole.WORKER),
            "TV", java.util.EnumSet.of(com.example.productionmvp.model.SystemRole.TV));

    /**
     * True when this worker may sign in on the screen the request came from. An unrecognised
     * context is refused rather than waved through, so a typo in the caller cannot quietly
     * disable the check.
     */
    private boolean allowedOnScreen(String loginContext, Worker worker) {
        if (loginContext == null || loginContext.isBlank()) {
            return true;
        }
        java.util.Set<com.example.productionmvp.model.SystemRole> allowed =
                LOGIN_CONTEXTS.get(loginContext.trim().toUpperCase());
        return allowed != null && worker.getSystemRole() != null && allowed.contains(worker.getSystemRole());
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

            // Refused before a token exists, and by throwing rather than returning, so this
            // takes the same path as a wrong PIN: identical 401, identical body, and counted
            // as a failed attempt by the rate limiter. Telling the caller that the PIN was
            // real but belonged elsewhere would answer the one question they should not be
            // able to ask.
            if (!allowedOnScreen(body.getLoginContext(), worker)) {
                throw new IllegalStateException("Login not permitted from this screen");
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
