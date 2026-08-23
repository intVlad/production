package com.example.productionmvp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SseServiceTest {

    private SseService sseService;

    @BeforeEach
    public void setup() {
        sseService = new SseService();
    }

    @Test
    public void testAddEmitter() {
        SseEmitter emitter = sseService.addEmitter();
        assertNotNull(emitter);
        
        @SuppressWarnings("unchecked")
        CopyOnWriteArrayList<SseEmitter> emitters = 
                (CopyOnWriteArrayList<SseEmitter>) ReflectionTestUtils.getField(sseService, "emitters");
        
        assertNotNull(emitters);
        assertEquals(1, emitters.size());
        assertTrue(emitters.contains(emitter));
        // Was 60s, which expired the stream on any floor quiet enough to produce no task
        // activity for a minute; the heartbeat keeps live clients well inside this window.
        assertEquals(30 * 60 * 1000L, ReflectionTestUtils.getField(emitter, "timeout"));
    }

    // The response stayed uncommitted until the first real broadcast, so EventSource never
    // fired onopen and the UI could not tell a live feed from a dead one.
    @Test
    public void testAddEmitter_SendsImmediateHandshake() throws Exception {
        try (org.mockito.MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
            SseEmitter emitter = sseService.addEmitter();
            verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Test
    public void testBroadcastEvent_Success() throws Exception {
        SseEmitter mockEmitter1 = mock(SseEmitter.class);
        SseEmitter mockEmitter2 = mock(SseEmitter.class);
        
        @SuppressWarnings("unchecked")
        CopyOnWriteArrayList<SseEmitter> emitters = 
                (CopyOnWriteArrayList<SseEmitter>) ReflectionTestUtils.getField(sseService, "emitters");
        
        emitters.add(mockEmitter1);
        emitters.add(mockEmitter2);
        
        sseService.broadcastEvent("test-event", "test-data");
        
        verify(mockEmitter1, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(mockEmitter2, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        assertEquals(2, emitters.size());
    }

    @Test
    public void testBroadcastEvent_IOExceptionRemovesEmitter() throws Exception {
        SseEmitter mockEmitter1 = mock(SseEmitter.class);
        SseEmitter mockEmitter2 = mock(SseEmitter.class);
        
        @SuppressWarnings("unchecked")
        CopyOnWriteArrayList<SseEmitter> emitters = 
                (CopyOnWriteArrayList<SseEmitter>) ReflectionTestUtils.getField(sseService, "emitters");
        
        emitters.add(mockEmitter1);
        emitters.add(mockEmitter2);
        
        // Make the first emitter throw an IOException
        doThrow(new IOException("Test Exception")).when(mockEmitter1).send(any(SseEmitter.SseEventBuilder.class));
        
        sseService.broadcastEvent("error-event", "error-data");
        
        verify(mockEmitter1, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(mockEmitter2, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        
        // Emitter 1 should be removed, Emitter 2 should remain
        assertEquals(1, emitters.size());
        assertTrue(emitters.contains(mockEmitter2));
        assertFalse(emitters.contains(mockEmitter1));
    }

    @Test
    public void testCallbacksRemoveEmitter_MockedConstruction() {
        try (org.mockito.MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class, 
                (mock, context) -> {
                    doAnswer(invocation -> {
                        Runnable r = invocation.getArgument(0);
                        r.run();
                        return null;
                    }).when(mock).onCompletion(any(Runnable.class));
                    
                    doAnswer(invocation -> {
                        Runnable r = invocation.getArgument(0);
                        r.run();
                        return null;
                    }).when(mock).onTimeout(any(Runnable.class));
                    
                    doAnswer(invocation -> {
                        Consumer<Throwable> c = invocation.getArgument(0);
                        c.accept(new RuntimeException());
                        return null;
                    }).when(mock).onError(any(Consumer.class));
                })) {
            
            SseEmitter emitter = sseService.addEmitter();
            
            @SuppressWarnings("unchecked")
            CopyOnWriteArrayList<SseEmitter> emitters = 
                    (CopyOnWriteArrayList<SseEmitter>) ReflectionTestUtils.getField(sseService, "emitters");
            
            // All callbacks executed immediately, so it should be removed 3 times and end up empty
            assertEquals(0, emitters.size());
        }
    }
}
