package com.example.productionmvp.service;

import com.example.productionmvp.exception.EntityNotFoundException;
import com.example.productionmvp.model.Worker;
import com.example.productionmvp.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private WorkerRepository workerRepository;

    @InjectMocks
    private AuthService authService;

    private Worker worker;
    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        worker = new Worker();
        worker.setId(UUID.randomUUID());
        worker.setName("Test Worker");
    }

    @Test
    void loginWithPin_Success() {
        String pin = "1234";
        worker.setPinHash(passwordEncoder.encode(pin));
        
        Worker anotherWorker = new Worker();
        anotherWorker.setId(UUID.randomUUID());
        anotherWorker.setPinHash(passwordEncoder.encode("0000"));
        
        when(workerRepository.findAll()).thenReturn(Arrays.asList(anotherWorker, worker));

        Worker result = authService.loginWithPin(pin);

        assertNotNull(result);
        assertEquals(worker.getId(), result.getId());
    }

    @Test
    void loginWithPin_InvalidPin_ThrowsException() {
        String pin = "1234";
        worker.setPinHash(passwordEncoder.encode("5678"));
        
        when(workerRepository.findAll()).thenReturn(Collections.singletonList(worker));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> authService.loginWithPin(pin));
        assertEquals("Invalid PIN", exception.getMessage());
    }

    @Test
    void loginWithPin_NullPinHash_ThrowsException() {
        String pin = "1234";
        worker.setPinHash(null);
        
        when(workerRepository.findAll()).thenReturn(Collections.singletonList(worker));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> authService.loginWithPin(pin));
        assertEquals("Invalid PIN", exception.getMessage());
    }
    
    @Test
    void loginWithPin_NoWorkers_ThrowsException() {
        String pin = "1234";
        
        when(workerRepository.findAll()).thenReturn(Collections.emptyList());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> authService.loginWithPin(pin));
        assertEquals("Invalid PIN", exception.getMessage());
    }

    @Test
    void loginWithQrBadge_Success() {
        String qrBadgeCode = "W-ABCDEF12";
        worker.setQrBadgeCode(qrBadgeCode);
        
        when(workerRepository.findByQrBadgeCode(qrBadgeCode)).thenReturn(Optional.of(worker));

        Worker result = authService.loginWithQrBadge(qrBadgeCode);

        assertNotNull(result);
        assertEquals(worker.getId(), result.getId());
    }

    @Test
    void loginWithQrBadge_NotFound_ThrowsException() {
        String qrBadgeCode = "W-ABCDEF12";
        
        when(workerRepository.findByQrBadgeCode(qrBadgeCode)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> authService.loginWithQrBadge(qrBadgeCode));
        assertEquals("Worker not found for QR Badge", exception.getMessage());
    }

    @Test
    void setWorkerPin_Success() {
        String pin = "1234";
        
        when(workerRepository.findById(worker.getId())).thenReturn(Optional.of(worker));

        authService.setWorkerPin(worker.getId(), pin);

        ArgumentCaptor<Worker> workerCaptor = ArgumentCaptor.forClass(Worker.class);
        verify(workerRepository).save(workerCaptor.capture());
        
        Worker savedWorker = workerCaptor.getValue();
        assertTrue(passwordEncoder.matches(pin, savedWorker.getPinHash()));
    }

    @Test
    void setWorkerPin_WorkerNotFound_ThrowsException() {
        String pin = "1234";
        UUID nonExistentId = UUID.randomUUID();
        
        when(workerRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> authService.setWorkerPin(nonExistentId, pin));
        assertEquals("Worker not found", exception.getMessage());
        verify(workerRepository, never()).save(any(Worker.class));
    }

    @Test
    void generateQrBadge_Success() {
        when(workerRepository.findById(worker.getId())).thenReturn(Optional.of(worker));

        String generatedCode = authService.generateQrBadge(worker.getId());

        assertNotNull(generatedCode);
        assertTrue(generatedCode.startsWith("W-"));
        assertEquals(10, generatedCode.length());

        ArgumentCaptor<Worker> workerCaptor = ArgumentCaptor.forClass(Worker.class);
        verify(workerRepository).save(workerCaptor.capture());
        
        Worker savedWorker = workerCaptor.getValue();
        assertEquals(generatedCode, savedWorker.getQrBadgeCode());
    }

    @Test
    void generateQrBadge_WorkerNotFound_ThrowsException() {
        UUID nonExistentId = UUID.randomUUID();
        
        when(workerRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> authService.generateQrBadge(nonExistentId));
        assertEquals("Worker not found", exception.getMessage());
        verify(workerRepository, never()).save(any(Worker.class));
    }
}
