package com.zuoye.mark.config;

import com.zuoye.mark.service.PlatformLogService;
import java.time.OffsetDateTime;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class BackendEventLogger {

    private final PlatformLogService logService;

    public BackendEventLogger(PlatformLogService logService) {
        this.logService = logService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        logService.backendEvent(
                "STARTED",
                "{\"startedAt\":\"" + OffsetDateTime.now() + "\"}");
    }

    @EventListener(ContextClosedEvent.class)
    public void onClosed() {
        logService.backendEvent(
                "STOPPED",
                "{\"stoppedAt\":\"" + OffsetDateTime.now() + "\"}");
    }
}
