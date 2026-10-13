package com.example.SalesDashboard.agent.service;

import com.example.SalesDashboard.agent.exception.AgentDeadlineExceededException;
import com.example.SalesDashboard.agent.exception.AgentDisconnectedException;
import com.example.SalesDashboard.agent.exception.AgentResponseException;
import com.example.SalesDashboard.agent.exception.AgentStartTimeoutException;
import com.example.SalesDashboard.agent.exception.ResponseSizeLimitExceededException;
import com.example.SalesDashboard.agent.exception.StreamIdleTimeoutException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

@Slf4j
@Component
public class PendingRequestRegistry {

    private final long maxResponseSizeBytes;
    private final long maxCompressedSizeBytes;
    private final int maxConcurrentStreamingOperations;
    private final long startTimeoutSeconds;
    private final long streamIdleSeconds;
    private final long overallDeadlineSeconds;

    private final Semaphore filePermits;
    private final ScheduledExecutorService scheduler;
    private final Map<String, PendingStreamState> pending = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public PendingRequestRegistry(
            @Value("${tally.limits.max-response-size-bytes:104857600}") long maxResponseSizeBytes,
            @Value("${tally.limits.max-compressed-size-bytes:26214400}") long maxCompressedSizeBytes,
            @Value("${tally.limits.max-concurrent-streaming-operations:10}") int maxConcurrentStreamingOperations,
            @Value("${tally.timeouts.start-timeout-seconds:45}") long startTimeoutSeconds,
            @Value("${tally.timeouts.stream-idle-seconds:30}") long streamIdleSeconds,
            @Value("${tally.timeouts.overall-deadline-seconds:240}") long overallDeadlineSeconds
    ) {
        this.maxResponseSizeBytes = maxResponseSizeBytes;
        this.maxCompressedSizeBytes = maxCompressedSizeBytes;
        this.maxConcurrentStreamingOperations = Math.max(1, maxConcurrentStreamingOperations);
        this.startTimeoutSeconds = startTimeoutSeconds;
        this.streamIdleSeconds = streamIdleSeconds;
        this.overallDeadlineSeconds = overallDeadlineSeconds;

        this.filePermits = new Semaphore(this.maxConcurrentStreamingOperations, true);
        this.scheduler = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "tally-stream-timer");
            thread.setDaemon(true);
            return thread;
        });
    }

    public PendingRequestRegistry() {
        this(104857600L, 26214400L, 10, 45L, 30L, 240L);
    }

    // ============================================================
    // REGISTER
    // ============================================================

    public CompletableFuture<AgentRelayService.RelayResponse> register(String requestId) {
        return register(requestId, null);
    }

    public CompletableFuture<AgentRelayService.RelayResponse> register(String requestId, String agentId) {
        if (requestId == null || requestId.isBlank()) {
            CompletableFuture<AgentRelayService.RelayResponse> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("Request ID is required"));
            return failed;
        }

        PendingStreamState state = new PendingStreamState(requestId, agentId);
        pending.put(requestId, state);

        // Schedule agent response-start timeout
        state.startTimeoutTask = scheduler.schedule(() -> {
            fail(requestId, new AgentStartTimeoutException(
                    "Tally Agent did not start response within " + startTimeoutSeconds + " seconds"
            ));
        }, startTimeoutSeconds, TimeUnit.SECONDS);

        // Schedule overall operation deadline
        state.overallTimeoutTask = scheduler.schedule(() -> {
            fail(requestId, new AgentDeadlineExceededException(
                    "Tally request exceeded overall deadline of " + overallDeadlineSeconds + " seconds"
            ));
        }, overallDeadlineSeconds, TimeUnit.SECONDS);

        // Cleanup on completion
        state.future.whenComplete((result, error) -> {
            pending.remove(requestId, state);
            state.cancelTimeouts();
            if (error != null) {
                state.cleanupResources();
            }
        });

        return state.future;
    }

    // ============================================================
    // NON-STREAMING COMPLETE (PROXY_RESPONSE)
    // ============================================================

    public void complete(String requestId, JsonNode response) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }

        PendingStreamState state = pending.get(requestId);
        if (state == null) {
            log.debug("Late or unknown PROXY_RESPONSE received | requestId={}", requestId);
            return;
        }

        boolean ok = response.path("ok").asBoolean(true);
        int status = response.path("status").asInt(200);

        if (!ok) {
            String errorMsg = response.path("error").asText(
                    response.path("body").asText("Unknown agent error")
            );
            fail(requestId, new AgentResponseException("Tally Agent error: " + errorMsg));
            return;
        }

        String body = response.path("body").asText("");

        if (state.completed.compareAndSet(false, true)) {
            state.cancelTimeouts();
            state.cleanupResources();
            pending.remove(requestId, state);

            AgentRelayService.RelayResponse relayResponse =
                    AgentRelayService.RelayResponse.inMemory(status, body);

            state.future.complete(relayResponse);
        }
    }

    // ============================================================
    // STREAM START
    // ============================================================

    public void startStream(String requestId, int status) {
        startStream(requestId, status, null);
    }

    public void startStream(String requestId, int status, String encoding) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }

        PendingStreamState state = pending.get(requestId);
        if (state == null) {
            log.debug("Late or unknown PROXY_RESPONSE_START | requestId={}", requestId);
            return;
        }

        synchronized (state) {
            if (state.completed.get()) {
                return;
            }

            // Start received -> cancel start timeout
            if (state.startTimeoutTask != null) {
                state.startTimeoutTask.cancel(false);
                state.startTimeoutTask = null;
            }

            state.status = status;
            state.encoding = encoding;

            // Acquire concurrent file operation permit
            try {
                boolean acquired = filePermits.tryAcquire(5, TimeUnit.SECONDS);
                if (!acquired) {
                    fail(requestId, new ResponseSizeLimitExceededException(
                            "Maximum concurrent file-backed operations limit reached (" + maxConcurrentStreamingOperations + ")"
                    ));
                    return;
                }
                state.permitAcquired = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(requestId, e);
                return;
            }

            // Create temporary file
            try {
                Path tempFile = Files.createTempFile("tally-resp-" + requestId + "-", ".tmp");
                tempFile.toFile().deleteOnExit();
                state.tempFile = tempFile;
                state.outputStream = new BufferedOutputStream(Files.newOutputStream(tempFile), 64 * 1024);
            } catch (IOException e) {
                fail(requestId, new IOException("Failed to create temporary file for response: " + e.getMessage(), e));
                return;
            }

            // Start stream idle timeout
            scheduleIdleTimeout(state);
        }
    }

    // ============================================================
    // STREAM CHUNK
    // ============================================================

    public void appendChunk(String requestId, String chunk) {
        appendChunk(requestId, chunk, null, null);
    }

    public void appendChunk(String requestId, String chunk, String chunkEncoding, Long reportedCompressedBytes) {
        if (requestId == null || requestId.isBlank() || chunk == null || chunk.isEmpty()) {
            return;
        }

        PendingStreamState state = pending.get(requestId);
        if (state == null) {
            log.debug("Late or unknown PROXY_RESPONSE_CHUNK | requestId={}", requestId);
            return;
        }

        synchronized (state) {
            if (state.completed.get()) {
                return;
            }

            if (state.outputStream == null) {
                // In case startStream wasn't received or failed
                startStream(requestId, 200, chunkEncoding);
                if (state.completed.get() || state.outputStream == null) {
                    return;
                }
            }

            if (chunkEncoding != null && !chunkEncoding.isBlank()) {
                state.encoding = chunkEncoding;
            }

            // Reset idle timeout
            scheduleIdleTimeout(state);

            try {
                byte[] bytesToWrite;
                boolean isGzip = "gzip".equalsIgnoreCase(state.encoding);

                if (isGzip) {
                    bytesToWrite = Base64.getDecoder().decode(chunk);
                    long newTotal = state.totalBytesReceived + bytesToWrite.length;
                    if (newTotal > maxCompressedSizeBytes) {
                        fail(requestId, new ResponseSizeLimitExceededException(
                                "Compressed response size exceeded maximum limit of " + maxCompressedSizeBytes + " bytes (received: " + newTotal + " bytes)"
                        ));
                        return;
                    }
                    state.totalBytesReceived = newTotal;
                } else {
                    bytesToWrite = chunk.getBytes(StandardCharsets.UTF_8);
                    long newTotal = state.totalBytesReceived + bytesToWrite.length;
                    if (newTotal > maxResponseSizeBytes) {
                        fail(requestId, new ResponseSizeLimitExceededException(
                                "Response size exceeded maximum limit of " + maxResponseSizeBytes + " bytes (received: " + newTotal + " bytes)"
                        ));
                        return;
                    }
                    state.totalBytesReceived = newTotal;
                }

                state.outputStream.write(bytesToWrite);
                state.chunkCount++;
                state.lastActivityTime = Instant.now();

            } catch (IllegalArgumentException e) {
                fail(requestId, new IllegalArgumentException("Failed to decode chunk Base64: " + e.getMessage(), e));
            } catch (IOException e) {
                fail(requestId, new IOException("Failed to write response chunk to temporary file: " + e.getMessage(), e));
            }
        }
    }

    // ============================================================
    // STREAM END
    // ============================================================

    public void completeStream(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }

        PendingStreamState state = pending.get(requestId);
        if (state == null) {
            log.debug("Late or unknown PROXY_RESPONSE_END | requestId={}", requestId);
            return;
        }

        synchronized (state) {
            if (state.completed.compareAndSet(false, true)) {
                state.cancelTimeouts();
                pending.remove(requestId, state);

                if (state.outputStream != null) {
                    try {
                        state.outputStream.flush();
                        state.outputStream.close();
                    } catch (IOException e) {
                        log.warn("Error closing temp file output stream for requestId={}: {}", requestId, e.getMessage());
                    }
                    state.outputStream = null;
                }

                Path tempFile = state.tempFile;
                boolean permitAcquired = state.permitAcquired;
                String encoding = state.encoding;
                long totalBytes = state.totalBytesReceived;
                int chunkCount = state.chunkCount;
                int status = state.status;
                Duration duration = Duration.between(state.startTime, Instant.now());
                long limitBytes = maxResponseSizeBytes;

                // Cleanup action runs when RelayResponse.close() is called
                Runnable cleanupAction = () -> {
                    if (permitAcquired) {
                        filePermits.release();
                    }
                };

                AgentRelayService.RelayResponse response = new AgentRelayService.RelayResponse(
                        status,
                        tempFile,
                        null,
                        encoding,
                        totalBytes,
                        chunkCount,
                        duration,
                        limitBytes,
                        cleanupAction
                );

                log.info("TALLY STREAM COMPLETE | requestId={} | status={} | encoding={} | bytes={} | chunks={} | duration={}ms",
                        requestId, status, encoding, totalBytes, chunkCount, duration.toMillis());

                state.future.complete(response);
            }
        }
    }

    // ============================================================
    // FAIL / CANCEL
    // ============================================================

    public void fail(String requestId, Throwable error) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }

        PendingStreamState state = pending.get(requestId);
        if (state != null) {
            synchronized (state) {
                if (state.completed.compareAndSet(false, true)) {
                    state.cancelTimeouts();
                    state.cleanupResources();
                    pending.remove(requestId, state);
                    log.warn("Tally request failed | requestId={} | error={}", requestId, error.getMessage());
                    state.future.completeExceptionally(error);
                }
            }
        }
    }

    public void failRequestsForAgent(String agentId, Throwable error) {
        if (agentId == null || agentId.isBlank()) {
            return;
        }

        pending.forEach((reqId, state) -> {
            if (agentId.equals(state.agentId)) {
                fail(reqId, error != null ? error : new AgentDisconnectedException("Tally Agent disconnected"));
            }
        });
    }

    private void scheduleIdleTimeout(PendingStreamState state) {
        if (state.idleTimeoutTask != null) {
            state.idleTimeoutTask.cancel(false);
        }
        state.idleTimeoutTask = scheduler.schedule(() -> {
            fail(state.requestId, new StreamIdleTimeoutException(
                    "Tally response stream was idle for more than " + streamIdleSeconds + " seconds"
            ));
        }, streamIdleSeconds, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        pending.forEach((reqId, state) -> {
            state.cleanupResources();
        });
        pending.clear();
    }

    public int getPendingCount() {
        return pending.size();
    }

    public int getAvailableFilePermits() {
        return filePermits.availablePermits();
    }

    // ============================================================
    // INTERNAL STATE CLASS
    // ============================================================

    private final class PendingStreamState {
        private final String requestId;
        private final String agentId;
        private final CompletableFuture<AgentRelayService.RelayResponse> future = new CompletableFuture<>();
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private final Instant startTime = Instant.now();

        private volatile Instant lastActivityTime = Instant.now();
        private volatile int status = 200;
        private volatile String encoding = null;
        private volatile long totalBytesReceived = 0;
        private volatile int chunkCount = 0;

        private Path tempFile;
        private OutputStream outputStream;
        private boolean permitAcquired = false;

        private ScheduledFuture<?> startTimeoutTask;
        private ScheduledFuture<?> idleTimeoutTask;
        private ScheduledFuture<?> overallTimeoutTask;

        private PendingStreamState(String requestId, String agentId) {
            this.requestId = requestId;
            this.agentId = agentId;
        }

        private void cancelTimeouts() {
            if (startTimeoutTask != null) {
                startTimeoutTask.cancel(false);
                startTimeoutTask = null;
            }
            if (idleTimeoutTask != null) {
                idleTimeoutTask.cancel(false);
                idleTimeoutTask = null;
            }
            if (overallTimeoutTask != null) {
                overallTimeoutTask.cancel(false);
                overallTimeoutTask = null;
            }
        }

        private void cleanupResources() {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Exception ignored) {}
                outputStream = null;
            }
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {}
                tempFile = null;
            }
            if (permitAcquired) {
                permitAcquired = false;
                filePermits.release();
            }
        }
    }
}