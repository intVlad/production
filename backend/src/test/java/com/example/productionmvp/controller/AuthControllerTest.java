package com.example.productionmvp.controller;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.example.productionmvp.config.security.JwtUtil;
import com.example.productionmvp.dto.AuthRequestDTO;
import com.example.productionmvp.model.SystemRole;
import com.example.productionmvp.model.Worker;
import com.example.productionmvp.service.AuthService;
import com.example.productionmvp.service.LoginRateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc(addFilters = false)
public class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private LoginRateLimiterService rateLimiter;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AuthController authController;

    private Worker testWorker;
    private UUID workerId;

    @BeforeEach
    void setUp() {
        workerId = UUID.randomUUID();
        testWorker = new Worker();
        testWorker.setId(workerId);
        testWorker.setSystemRole(SystemRole.WORKER);
        lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    void loginWithPin_Success() {
        // Arrange
        AuthRequestDTO body = new AuthRequestDTO();
        body.setPin("1234");

        when(authService.loginWithPin("1234")).thenReturn(testWorker);
        when(jwtUtil.generateToken(workerId.toString(), "ROLE_WORKER")).thenReturn("test.jwt.token");

        // Act
        ResponseEntity<?> response = authController.loginWithPin(body, request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map);

        Map<?, ?> responseBody = (Map<?, ?>) response.getBody();
        assertEquals("test.jwt.token", responseBody.get("token"));
        assertEquals(testWorker, responseBody.get("worker"));
        verify(rateLimiter).recordSuccess("ip:127.0.0.1");
    }

    @Test
    void loginWithPin_InvalidCredentials() {
        // Arrange
        AuthRequestDTO body = new AuthRequestDTO();
        body.setPin("wrong-pin");

        when(authService.loginWithPin("wrong-pin")).thenThrow(new RuntimeException("Invalid pin"));

        // Act
        ResponseEntity<?> response = authController.loginWithPin(body, request);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());

        Map<?, ?> responseBody = (Map<?, ?>) response.getBody();
        assertEquals("Invalid credentials", responseBody.get("error"));
        verify(rateLimiter).recordFailure("ip:127.0.0.1");
    }

    @Test
    void loginWithPin_NullBodyException() {
        // Arrange
        AuthRequestDTO body = new AuthRequestDTO(); // missing "pin"

        // Act
        ResponseEntity<?> response = authController.loginWithPin(body, request);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Map<?, ?> responseBody = (Map<?, ?>) response.getBody();
        assertEquals("Invalid credentials", responseBody.get("error"));
    }

    @Test
    void loginWithPin_RateLimited_Returns429WithoutAttemptingLogin() {
        AuthRequestDTO body = new AuthRequestDTO();
        body.setPin("1234");

        org.mockito.Mockito.doThrow(new LoginRateLimiterService.TooManyAttemptsException("locked"))
                .when(rateLimiter).checkAllowed("ip:127.0.0.1");

        ResponseEntity<?> response = authController.loginWithPin(body, request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        org.mockito.Mockito.verifyNoInteractions(authService);
    }

    @Test
    void loginWithQrBadge_Success() {
        // Arrange
        AuthRequestDTO body = new AuthRequestDTO();
        body.setQrBadgeCode("QR-123");

        when(authService.loginWithQrBadge("QR-123")).thenReturn(testWorker);
        when(jwtUtil.generateToken(workerId.toString(), "ROLE_WORKER")).thenReturn("test.jwt.token");

        // Act
        ResponseEntity<?> response = authController.loginWithQrBadge(body, request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        Map<?, ?> responseBody = (Map<?, ?>) response.getBody();
        assertEquals("test.jwt.token", responseBody.get("token"));
        assertEquals(testWorker, responseBody.get("worker"));
    }

    @Test
    void loginWithQrBadge_InvalidCode() {
        // Arrange
        AuthRequestDTO body = new AuthRequestDTO();
        body.setQrBadgeCode("invalid-qr");

        when(authService.loginWithQrBadge("invalid-qr")).thenThrow(new RuntimeException("Invalid QR"));

        // Act
        ResponseEntity<?> response = authController.loginWithQrBadge(body, request);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        Map<?, ?> responseBody = (Map<?, ?>) response.getBody();
        assertEquals("Invalid QR code", responseBody.get("error"));
    }

    @Test
    void loginWithQrBadge_NullBodyException() {
        // Arrange
        AuthRequestDTO body = new AuthRequestDTO(); // missing "qrBadgeCode"

        // Act
        ResponseEntity<?> response = authController.loginWithQrBadge(body, request);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Map<?, ?> responseBody = (Map<?, ?>) response.getBody();
        assertEquals("Invalid QR code", responseBody.get("error"));
    }

    @Test
    void setWorkerPin_Success() {
        // Arrange
        AuthRequestDTO body = new AuthRequestDTO();
        body.setWorkerId(workerId);
        body.setPin("4321");

        // Act
        ResponseEntity<Void> response = authController.setWorkerPin(body);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(authService).setWorkerPin(workerId, "4321");
    }
}
