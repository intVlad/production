package com.example.productionmvp.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class SseService {

    // Long enough that a quiet shop floor doesn't churn the connection, but still bounded so a
    // client that vanished without closing cleanly is eventually reaped. The heartbeat below
    // keeps live clients well inside it.
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;
    private static final long HEARTBEAT_SECONDS = 20;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService heartbeat =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    public SseService() {
        // Proxies and browsers drop a stream that goes silent, and the emitter's own timeout
        // would otherwise fire on any floor quiet enough to have no task activity for a while.
        // A comment frame keeps the connection alive without reaching onmessage on the client.
        heartbeat.scheduleAtFixedRate(this::sendHeartbeat,
                HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    public SseEmitter addEmitter() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        // Nothing was written until the first real broadcast, so the response headers stayed
        // uncommitted and the browser's EventSource never left CONNECTING - onopen never fired
        // and the UI had no way to confirm the live feed was actually up. Send a comment frame
        // straight away: it commits the response (firing onopen) without being delivered to
        // onmessage, so it can't be mistaken for a dashboard update.
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    private void sendHeartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    public void broadcastEvent(String eventType, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventType).data(data));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    // manager.js/worker.js/tv.js all listen with the plain onmessage handler (not
    // addEventListener on a named type) and compare event.data === "dashboard_update" - so the
    // event must be sent as the default "message" type with that exact string as the payload,
    // not a named event (which onmessage never receives at all, regardless of payload).
    public void broadcastDashboardUpdate() {
        broadcastEvent("message", "dashboard_update");
    }

    @PreDestroy
    void shutdown() {
        heartbeat.shutdownNow();
    }
}
