package com.example.productionmvp.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseService {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter addEmitter() {
        SseEmitter emitter = new SseEmitter(60000L); // 60 seconds timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        return emitter;
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
}
