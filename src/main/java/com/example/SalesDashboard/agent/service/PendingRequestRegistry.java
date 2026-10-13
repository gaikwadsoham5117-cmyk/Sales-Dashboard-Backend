package com.example.SalesDashboard.agent.service;

import com.example.SalesDashboard.agent.exception.AgentConnectionTimeoutException;
import com.example.SalesDashboard.agent.exception.AgentDisconnectedException;
import com.example.SalesDashboard.agent.exception.OperationDeadlineException;
import com.example.SalesDashboard.agent.exception.ResponseSizeLimitExceededException;
import com.example.SalesDashboard.agent.exception.StreamIdleTimeoutException;
import com.example.SalesDashboard.agent.model.AgentProxyResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class PendingRequestRegistry {

    private final long connectTimeoutSeconds;
    private final long streamIdleTimeoutSeconds;
    private final long operationDeadlineSeconds;
    private final long maxResponseSizeBytes;

    private final Map<String, PendingResponse> pending = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    private final ScheduledExecutorService timeoutScheduler = Executors.newScheduledThreadPool(
            2,
            runnable -> {
                Thread t = new Thread(runnable, "pending-request-timeout-scheduler");
                t.setDaemon(true);
                return t;
            }
    );

    public PendingRequestRegistry(
            @Value("${agent.relay.connect-timeout-seconds:180}") long connectTimeoutSeconds,
            @Value("${agent.relay.stream-idle-timeout-seconds:45}") long streamIdleTimeoutSeconds,
            @Value("${agent.relay.operation-deadline-seconds:300}") long operationDeadlineSeconds,
            @Value("${agent.relay.max-response-size-bytes:52428800}") long maxResponseSizeBytes
    ) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.streamIdleTimeoutSeconds = streamIdleTimeoutSeconds;
        this.operationDeadlineSeconds = operationDeadlineSeconds;
        this.maxResponseSizeBytes = maxResponseSizeBytes;

        log.info(
                "PendingRequestRegistry initialized with timeouts: connectTimeout={}s, streamIdleTimeout={}s, deadline={}s, maxSize={} bytes",
                connectTimeoutSeconds,
                streamIdleTimeoutSeconds,
                operationDeadlineSeconds,
                maxResponseSizeBytes
        );
    }

    @PreDestroy
    public void shutdown() {
        timeoutScheduler.shutdownNow();
    }

    // ============================================================
    // REGISTER
    // ============================================================

    public CompletableFuture<AgentProxyResponse> register(String requestId, String agentId) {
        if (requestId == null || requestId.isBlank()) {
            CompletableFuture<AgentProxyResponse> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("Request ID is required"));
            return failed;
        }

        PendingResponse pendingResponse = new PendingResponse(requestId, agentId);
        pending.put(requestId, pendingResponse);

        log.info(
                "[TRACE][{}] Registered pending request for agentId={} at {} ms",
                requestId,
                agentId,
                pendingResponse.createdAt
        );

        // Schedule overall operation deadline
        ScheduledFuture<?> deadlineTask = timeoutScheduler.schedule(
                () -> handleOperationDeadline(requestId),
                operationDeadlineSeconds,
                TimeUnit.SECONDS
        );
        pendingResponse.deadlineTask = deadlineTask;

        // Schedule initial connection timeout (until stream start or complete)
        ScheduledFuture<?> connectTask = timeoutScheduler.schedule(
                () -> handleConnectTimeout(requestId),
                connectTimeoutSeconds,
                TimeUnit.SECONDS
        );
        pendingResponse.activityTask = connectTask;

        CompletableFuture<AgentProxyResponse> future = pendingResponse.future;

        future.whenComplete((result, error) -> {
            pending.remove(requestId, pendingResponse);
            pendingResponse.cancelTimers();

            long totalElapsed = System.currentTimeMillis() - pendingResponse.createdAt;
            if (error != null) {
                log.warn(
                        "[TRACE][{}] Request completed exceptionally after {} ms | error={}",
                        requestId,
                        totalElapsed,
                        error.getMessage()
                );
            } else {
                log.info(
                        "[TRACE][{}] Request completed successfully after {} ms | chunksReceived={} | cumulativeBytes={}",
                        requestId,
                        totalElapsed,
                        pendingResponse.chunkCount.get(),
                        pendingResponse.cumulativeBytes.get()
                );
            }
        });

        return future;
    }

    // ============================================================
    // NON-STREAMING PROXY RESPONSE
    // ============================================================

    public void complete(String requestId, JsonNode response) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }

        PendingResponse pendingResponse = pending.get(requestId);
        if (pendingResponse == null) {
            log.warn("[TRACE][{}] Discarding late non-streaming PROXY_RESPONSE (not found or timed out)", requestId);
            return;
        }

        boolean ok = response.path("ok").asBoolean(false);
        int status = response.path("status").asInt(200);
        String body = response.path("body").asText("");
        String error = response.path("error").asText(null);

        if (!ok) {
            pendingResponse.future.complete(AgentProxyResponse.failure(status, error != null ? error : body));
        } else {
            pendingResponse.future.complete(AgentProxyResponse.success(status, body));
        }
    }

    // ============================================================
    // STREAM START
    // ============================================================

    public void startStream(String requestId, int status) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }

        PendingResponse pendingResponse = pending.get(requestId);
        if (pendingResponse == null) {
            log.warn("[TRACE][{}] Discarding late PROXY_RESPONSE_START (not found or timed out)", requestId);
            return;
        }

        pendingResponse.status = status;
        pendingResponse.streaming = true;

        log.info(
                "[TRACE][{}] Backend stream start received: status={} | elapsed={} ms",
                requestId,
                status,
                System.currentTimeMillis() - pendingResponse.createdAt
        );

        // Cancel initial connect timer and start stream idle timer
        resetStreamIdleTimer(pendingResponse);
    }

    // ============================================================
    // STREAM CHUNK
    // ============================================================

    public void appendChunk(String requestId, String chunk, Integer chunkIndex, Long bytes, Long cumulativeBytes) {
        if (requestId == null || requestId.isBlank() || chunk == null || chunk.isEmpty()) {
            return;
        }

        PendingResponse pendingResponse = pending.get(requestId);
        if (pendingResponse == null) {
            log.debug("[TRACE][{}] Discarding late PROXY_RESPONSE_CHUNK (not found or timed out)", requestId);
            return;
        }

        int chunkLengthBytes = chunk.getBytes(StandardCharsets.UTF_8).length;
        long newTotalBytes = pendingResponse.cumulativeBytes.addAndGet(chunkLengthBytes);
        long currentCount = pendingResponse.chunkCount.incrementAndGet();

        // Check size limit immediately
        if (newTotalBytes > maxResponseSizeBytes) {
            log.error(
                    "[TRACE][{}] Response size limit exceeded: {} bytes > max {} bytes",
                    requestId,
                    newTotalBytes,
                    maxResponseSizeBytes
            );
            pendingResponse.future.completeExceptionally(
                    new ResponseSizeLimitExceededException(
                            "Tally response size limit exceeded: "
                                    + (newTotalBytes / (1024 * 1024))
                                    + " MB > max allowed "
                                    + (maxResponseSizeBytes / (1024 * 1024))
                                    + " MB"
                    )
            );
            return;
        }

        synchronized (pendingResponse) {
            pendingResponse.body.append(chunk);
        }

        if (currentCount % 50 == 0 || currentCount == 1) {
            log.info(
                    "[TRACE][{}] Chunk count and cumulative bytes received: chunkCount={} (reported={}), cumulativeBytesReceived={} (reported={})",
                    requestId,
                    currentCount,
                    chunkIndex != null ? chunkIndex : currentCount,
                    newTotalBytes,
                    cumulativeBytes != null ? cumulativeBytes : newTotalBytes
            );
        }

        // Reset stream idle timer since active progress was made
        resetStreamIdleTimer(pendingResponse);
    }

    // ============================================================
    // STREAM END
    // ============================================================

    public void completeStream(String requestId, Integer totalChunks, Long totalBytes) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }

        PendingResponse pendingResponse = pending.get(requestId);
        if (pendingResponse == null) {
            log.warn("[TRACE][{}] Discarding late PROXY_RESPONSE_END (not found or already timed out)", requestId);
            return;
        }

        String responseBody;
        synchronized (pendingResponse) {
            responseBody = pendingResponse.body.toString();
            // Clear builder memory immediately
            pendingResponse.body.setLength(0);
        }

        log.info(
                "[TRACE][{}] Backend stream end received: totalChunksReceived={} (reported={}), cumulativeBytesReceived={} (reported={}) | elapsed={} ms",
                requestId,
                pendingResponse.chunkCount.get(),
                totalChunks != null ? totalChunks : pendingResponse.chunkCount.get(),
                pendingResponse.cumulativeBytes.get(),
                totalBytes != null ? totalBytes : pendingResponse.cumulativeBytes.get(),
                System.currentTimeMillis() - pendingResponse.createdAt
        );

        pendingResponse.future.complete(AgentProxyResponse.success(pendingResponse.status, responseBody));
    }

    // ============================================================
    // FAIL SPECIFIC REQUEST
    // ============================================================

    public void fail(String requestId, Throwable error) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }

        PendingResponse pendingResponse = pending.get(requestId);
        if (pendingResponse != null) {
            pendingResponse.future.completeExceptionally(error);
        }
    }

    // ============================================================
    // FAIL ALL REQUESTS FOR AN AGENT (e.g. on disconnect)
    // ============================================================

    public void failAgentRequests(String agentId, String reason) {
        if (agentId == null || agentId.isBlank()) {
            return;
        }

        pending.forEach((requestId, pendingResponse) -> {
            if (agentId.equals(pendingResponse.agentId)) {
                log.warn(
                        "[TRACE][{}] Failing pending request because agent disconnected: agentId={}, reason={}",
                        requestId,
                        agentId,
                        reason
                );
                pendingResponse.future.completeExceptionally(
                        new AgentDisconnectedException(
                                "Tally Agent disconnected during request: " + reason
                        )
                );
            }
        });
    }

    // ============================================================
    // TIMEOUT HANDLERS
    // ============================================================

    private void resetStreamIdleTimer(PendingResponse pendingResponse) {
        synchronized (pendingResponse) {
            if (pendingResponse.activityTask != null) {
                pendingResponse.activityTask.cancel(false);
            }
            if (!pendingResponse.future.isDone()) {
                pendingResponse.activityTask = timeoutScheduler.schedule(
                        () -> handleStreamIdleTimeout(pendingResponse.requestId),
                        streamIdleTimeoutSeconds,
                        TimeUnit.SECONDS
                );
            }
        }
    }

    private void handleConnectTimeout(String requestId) {
        PendingResponse pendingResponse = pending.get(requestId);
        if (pendingResponse != null && !pendingResponse.future.isDone() && !pendingResponse.streaming) {
            log.error(
                    "[TRACE][{}] Initial connection timeout after {}s (no stream start received from Agent)",
                    requestId,
                    connectTimeoutSeconds
            );
            pendingResponse.future.completeExceptionally(
                    new AgentConnectionTimeoutException(
                            "Tally Agent did not begin response within "
                                    + connectTimeoutSeconds
                                    + " seconds. Check TallyPrime and local Agent."
                    )
            );
        }
    }

    private void handleStreamIdleTimeout(String requestId) {
        PendingResponse pendingResponse = pending.get(requestId);
        if (pendingResponse != null && !pendingResponse.future.isDone()) {
            log.error(
                    "[TRACE][{}] Stream idle timeout after {}s without response chunk (cumulativeBytes={})",
                    requestId,
                    streamIdleTimeoutSeconds,
                    pendingResponse.cumulativeBytes.get()
            );
            pendingResponse.future.completeExceptionally(
                    new StreamIdleTimeoutException(
                            "Tally stream stalled: no data received for "
                                    + streamIdleTimeoutSeconds
                                    + " seconds."
                    )
            );
        }
    }

    private void handleOperationDeadline(String requestId) {
        PendingResponse pendingResponse = pending.get(requestId);
        if (pendingResponse != null && !pendingResponse.future.isDone()) {
            log.error(
                    "[TRACE][{}] Overall operation deadline exceeded after {}s (chunksReceived={}, bytesReceived={})",
                    requestId,
                    operationDeadlineSeconds,
                    pendingResponse.chunkCount.get(),
                    pendingResponse.cumulativeBytes.get()
            );
            pendingResponse.future.completeExceptionally(
                    new OperationDeadlineException(
                            "Total operation duration exceeded deadline of "
                                    + operationDeadlineSeconds
                                    + " seconds."
                    )
            );
        }
    }

    // ============================================================
    // INTERNAL PENDING RESPONSE RECORD
    // ============================================================

    private static final class PendingResponse {
        private final String requestId;
        private final String agentId;
        private final long createdAt = System.currentTimeMillis();
        private final CompletableFuture<AgentProxyResponse> future = new CompletableFuture<>();
        private final StringBuilder body = new StringBuilder();
        private final AtomicLong cumulativeBytes = new AtomicLong(0);
        private final AtomicLong chunkCount = new AtomicLong(0);

        private volatile int status = 200;
        private volatile boolean streaming = false;

        private volatile ScheduledFuture<?> activityTask;
        private volatile ScheduledFuture<?> deadlineTask;

        private PendingResponse(String requestId, String agentId) {
            this.requestId = requestId;
            this.agentId = agentId;
        }

        private synchronized void cancelTimers() {
            if (activityTask != null) {
                activityTask.cancel(false);
                activityTask = null;
            }
            if (deadlineTask != null) {
                deadlineTask.cancel(false);
                deadlineTask = null;
            }
        }
    }
}