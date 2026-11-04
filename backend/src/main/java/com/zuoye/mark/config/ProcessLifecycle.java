package com.zuoye.mark.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProcessLifecycle {

    private final Path pidFile;

    public ProcessLifecycle(@Value("${mark.pid-file:}") String pidFile) {
        this.pidFile = pidFile == null || pidFile.isBlank() ? null : Path.of(pidFile);
    }

    @PostConstruct
    public void writePid() throws IOException {
        if (pidFile == null) {
            return;
        }
        if (pidFile.getParent() != null) {
            Files.createDirectories(pidFile.getParent());
        }
        Files.writeString(pidFile, Long.toString(ProcessHandle.current().pid()));
    }

    @PreDestroy
    public void removePid() throws IOException {
        if (pidFile != null) {
            Files.deleteIfExists(pidFile);
        }
    }
}
