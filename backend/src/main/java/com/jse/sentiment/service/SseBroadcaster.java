package com.jse.sentiment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseBroadcaster {

    private static final Logger logger = LoggerFactory.getLogger(SseBroadcaster.class);
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public void addEmitter(SseEmitter emitter) {
        this.emitters.add(emitter);
        
        emitter.onCompletion(() -> {
            logger.debug("SSE emitter completed. Removing.");
            this.emitters.remove(emitter);
        });
        
        emitter.onTimeout(() -> {
            logger.debug("SSE emitter timed out. Removing.");
            this.emitters.remove(emitter);
        });
        
        emitter.onError((e) -> {
            logger.debug("SSE emitter error: {}. Removing.", e.getMessage());
            this.emitters.remove(emitter);
        });
    }

    public void broadcast(String eventName, Object data) {
        if (emitters.isEmpty()) return;
        
        logger.info("Broadcasting '{}' event to {} connected SSE clients...", eventName, emitters.size());
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (IOException e) {
                logger.warn("Failed to send SSE event to emitter. Removing client.");
                deadEmitters.add(emitter);
            }
        }
        
        emitters.removeAll(deadEmitters);
    }
}
