package com.zuoye.mark.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DeepTutorClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final Duration requestTimeout;

    public DeepTutorClient(
            ObjectMapper objectMapper,
            @Value("${mark.deeptutor.base-url:http://127.0.0.1:8001}") String baseUrl,
            @Value("${mark.deeptutor.api-key:}") String apiKey,
            @Value("${mark.deeptutor.timeout-seconds:300}") long timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.requestTimeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Map<String, Object> readiness() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", "unreachable");
        result.put("endpoint", baseUrl + "/health/ready");
        result.put("checkedAt", Instant.now().toString());
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(baseUrl + "/health/ready"))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());
            boolean ready = response.statusCode() >= 200 && response.statusCode() < 300;
            result.put("state", ready ? "ok" : "unreachable");
            result.put("statusCode", response.statusCode());
            result.put("ready", ready);
        } catch (Exception error) {
            result.put("ready", false);
            result.put("error", error.getMessage());
        }
        return result;
    }

    public String chat(String message) throws Exception {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String content = complete("chat", Map.of(), message).content();
                if (content != null && !content.isBlank()) return content;
                throw new IllegalStateException("DeepTutor 返回了空内容。");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw error;
            } catch (Exception error) {
                lastFailure = error;
                if (attempt < 3) {
                    try {
                        Thread.sleep(500L * (1L << (attempt - 1)));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw interrupted;
                    }
                }
            }
        }
        throw new IllegalStateException(
                "DeepTutor 连续 3 次请求失败。",
                lastFailure);
    }

    public TurnResult complete(
            String capability,
            Map<String, Object> config,
            String content) throws Exception {
        String socketUrl = baseUrl.replaceFirst("^http", "ws") + "/ws";
        if (apiKey != null && !apiKey.isBlank()) {
            socketUrl += "?token=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        }

        CompletableFuture<TurnResult> result = new CompletableFuture<>();
        StringBuilder output = new StringBuilder();
        AtomicBoolean terminal = new AtomicBoolean(false);
        WebSocket.Listener listener = new DeepTutorListener(
                objectMapper,
                capability,
                config,
                content,
                output,
                terminal,
                result);

        WebSocket socket = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(socketUrl), listener)
                .get(12, TimeUnit.SECONDS);
        try {
            return result.get(requestTimeout.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception error) {
            if (!terminal.getAndSet(true)) {
                result.completeExceptionally(error);
            }
            throw error;
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done")
                    .orTimeout(2, TimeUnit.SECONDS)
                    .exceptionally(ignored -> null);
        }
    }

    private static final class DeepTutorListener implements WebSocket.Listener {

        private final ObjectMapper objectMapper;
        private final String capability;
        private final Map<String, Object> config;
        private final String inputContent;
        private final StringBuilder content;
        private final AtomicBoolean terminal;
        private final CompletableFuture<TurnResult> result;
        private JsonNode resultMetadata;
        private final StringBuilder frameBuffer = new StringBuilder();

        private DeepTutorListener(
                ObjectMapper objectMapper,
                String capability,
                Map<String, Object> config,
                String inputContent,
                StringBuilder content,
                AtomicBoolean terminal,
                CompletableFuture<TurnResult> result) {
            this.objectMapper = objectMapper;
            this.capability = capability;
            this.config = config;
            this.inputContent = inputContent;
            this.content = content;
            this.terminal = terminal;
            this.result = result;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            try {
                Map<String, Object> command = new LinkedHashMap<>();
                command.put("type", "start_turn");
                command.put("protocol_version", "2.0");
                command.put("capability", capability);
                command.put("config", config);
                command.put("content", inputContent);
                command.put("language", "zh");
                command.put("persist_user_message", false);
                command.put("capability_once", true);
                webSocket.sendText(objectMapper.writeValueAsString(command), true);
            } catch (Exception error) {
                fail(error);
            }
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(
                WebSocket webSocket,
                CharSequence data,
                boolean last) {
            frameBuffer.append(data);
            if (last) {
                String frame = frameBuffer.toString();
                frameBuffer.setLength(0);
                handleFrame(webSocket, frame);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(
                WebSocket webSocket,
                int statusCode,
                String reason) {
            if (!terminal.get()) {
                fail(new IllegalStateException(
                        "DeepTutor connection closed before a terminal event: " + reason));
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            fail(error);
        }

        private void handleFrame(WebSocket socket, String frame) {
            try {
                JsonNode event = objectMapper.readTree(frame);
                String type = event.path("type").asText("");
                if ("content".equals(type) || "text".equals(type)) {
                    content.append(event.path("content").asText(""));
                    return;
                }
                if ("result".equals(type)) {
                    resultMetadata = event.path("metadata").deepCopy();
                    return;
                }
                if ("done".equals(type)) {
                    if (terminal.compareAndSet(false, true)) {
                        result.complete(new TurnResult(
                                content.toString(),
                                resultMetadata == null
                                        ? objectMapper.createObjectNode()
                                        : resultMetadata));
                    }
                    socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                    return;
                }
                if ("error".equals(type) || "protocol_error".equals(type)) {
                    String detail = event.path("message").asText(
                            event.path("content").asText(frame));
                    fail(new IllegalStateException("DeepTutor error: " + detail));
                }
            } catch (Exception error) {
                fail(error);
            }
        }

        private void fail(Throwable error) {
            if (terminal.compareAndSet(false, true)) {
                result.completeExceptionally(error);
            }
        }
    }

    public record TurnResult(String content, JsonNode metadata) {}
}
