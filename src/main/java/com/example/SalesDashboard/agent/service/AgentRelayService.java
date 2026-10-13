package com.example.SalesDashboard.agent.service;

import com.example.SalesDashboard.agent.entity.Agent;
import com.example.SalesDashboard.agent.exception.AgentCommunicationException;
import com.example.SalesDashboard.agent.exception.AgentDeadlineExceededException;
import com.example.SalesDashboard.agent.exception.AgentDisconnectedException;
import com.example.SalesDashboard.agent.exception.AgentIdRequiredException;
import com.example.SalesDashboard.agent.exception.AgentNotFoundException;
import com.example.SalesDashboard.agent.exception.AgentOfflineException;
import com.example.SalesDashboard.agent.exception.AgentOrganizationNotAssignedException;
import com.example.SalesDashboard.agent.exception.AgentQueueTimeoutException;
import com.example.SalesDashboard.agent.exception.AgentResponseException;
import com.example.SalesDashboard.agent.exception.AgentStartTimeoutException;
import com.example.SalesDashboard.agent.exception.AgentTimeoutException;
import com.example.SalesDashboard.agent.exception.AgentUserNotFoundException;
import com.example.SalesDashboard.agent.exception.ResponseSizeLimitExceededException;
import com.example.SalesDashboard.agent.exception.StreamIdleTimeoutException;
import com.example.SalesDashboard.user.entity.User;
import com.example.SalesDashboard.user.repository.UserRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRelayService {

    private final AgentConnectionService agentConnectionService;
    private final PendingRequestRegistry pendingRequestRegistry;
    private final AgentRequestQueueService agentRequestQueueService;
    private final UserRepository userRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    // ============================================================
    // RELAY RESPONSE ABSTRACTION
    // ============================================================

    public static final class RelayResponse implements AutoCloseable {
        private final int status;
        private final Path tempFile;
        private final String memoryBody;
        private final String encoding;
        private final long totalBytes;
        private final int chunkCount;
        private final Duration duration;
        private final long maxDecompressedBytes;
        private final Runnable cleanupAction;
        private final List<Runnable> cleanupActions = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final AtomicBoolean closed = new AtomicBoolean(false);

        public RelayResponse(
                int status,
                Path tempFile,
                String memoryBody,
                String encoding,
                long totalBytes,
                int chunkCount,
                Duration duration,
                long maxDecompressedBytes,
                Runnable cleanupAction
        ) {
            this.status = status;
            this.tempFile = tempFile;
            this.memoryBody = memoryBody;
            this.encoding = encoding;
            this.totalBytes = totalBytes;
            this.chunkCount = chunkCount;
            this.duration = duration != null ? duration : Duration.ZERO;
            this.maxDecompressedBytes = maxDecompressedBytes;
            this.cleanupAction = cleanupAction;
        }

        public static RelayResponse inMemory(int status, String body) {
            return new RelayResponse(
                    status,
                    null,
                    body != null ? body : "",
                    null,
                    body != null ? body.length() : 0,
                    1,
                    Duration.ZERO,
                    Long.MAX_VALUE,
                    null
            );
        }

        public static RelayResponse fileBacked(
                int status,
                Path tempFile,
                String encoding,
                long totalBytes,
                int chunkCount,
                Duration duration,
                long maxDecompressedBytes,
                Runnable cleanupAction
        ) {
            return new RelayResponse(
                    status,
                    tempFile,
                    null,
                    encoding,
                    totalBytes,
                    chunkCount,
                    duration,
                    maxDecompressedBytes,
                    cleanupAction
            );
        }

        public int status() {
            return status;
        }

        public int getStatus() {
            return status;
        }

        public String encoding() {
            return encoding;
        }

        public long totalBytes() {
            return totalBytes;
        }

        public int chunkCount() {
            return chunkCount;
        }

        public Duration duration() {
            return duration;
        }

        public Path tempFile() {
            return tempFile;
        }

        public boolean isFileBacked() {
            return tempFile != null;
        }

        public InputStream openStream() throws IOException {
            if (memoryBody != null) {
                return new ByteArrayInputStream(memoryBody.getBytes(StandardCharsets.UTF_8));
            }
            if (tempFile != null) {
                InputStream rawStream = Files.newInputStream(tempFile);
                InputStream stream = "gzip".equalsIgnoreCase(encoding)
                        ? new GZIPInputStream(rawStream)
                        : rawStream;
                return new BoundedInputStream(stream, maxDecompressedBytes > 0 ? maxDecompressedBytes : Long.MAX_VALUE);
            }
            return InputStream.nullInputStream();
        }

        public String body() {
            if (memoryBody != null) {
                return memoryBody;
            }
            try (InputStream is = openStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read response body: " + e.getMessage(), e);
            }
        }

        public void addCleanupAction(Runnable action) {
            if (action != null) {
                if (closed.get()) {
                    action.run();
                } else {
                    cleanupActions.add(action);
                }
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                for (Runnable action : cleanupActions) {
                    try {
                        action.run();
                    } catch (Exception ignored) {}
                }
                if (cleanupAction != null) {
                    try {
                        cleanupAction.run();
                    } catch (Exception ignored) {}
                }
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException ignored) {}
                }
            }
        }
    }

    // ============================================================
    // MAIN RELAY
    // AUTOMATIC AGENT SELECTION + PER-AGENT QUEUE
    // ============================================================

    public RelayResponse relay(
            String userId,
            String method,
            Map<String, String> headers,
            String body
    ) {

        AgentSelection selection = findConnectedAgent(userId);
        String agentId = selection.agent().getAgentId();
        String description = buildRequestDescription(method, body);

        CompletableFuture<RelayResponse> future;

        try {
            future = agentRequestQueueService.submit(
                    agentId,
                    description,
                    () -> sendRequestToAgent(
                            userId,
                            selection.organizationId(),
                            selection.agent(),
                            selection.session(),
                            method,
                            headers,
                            body
                    )
            );
        } catch (Exception e) {
            throw new AgentCommunicationException(
                    "Failed to submit request to Tally Agent queue: " + e.getMessage()
            );
        }

        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentCommunicationException(
                    "Interrupted while waiting for Tally Agent queue"
            );
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AgentCommunicationException(
                    "Tally Agent queue request failed: "
                            + (cause == null ? "Unknown error" : cause.getMessage())
            );
        }
    }

    // ============================================================
    // EXACT AGENT RELAY (BACKWARD COMPATIBILITY)
    // ============================================================

    public RelayResponse relay(
            String userId,
            String agentId,
            String method,
            Map<String, String> headers,
            String body
    ) {

        WebSocketSession session = findConnectedAgentSession(userId, agentId);

        User user = userRepository.findById(userId).orElseThrow(
                () -> new AgentUserNotFoundException("User not found")
        );

        String organizationId = user.getOrganizationId();

        Agent agent = agentConnectionService.getAgentsByOrganizationId(organizationId)
                .stream()
                .filter(a -> a != null && agentId.equals(a.getAgentId()))
                .findFirst()
                .orElseThrow(() -> new AgentNotFoundException("Tally Agent not found"));

        String description = buildRequestDescription(method, body);

        CompletableFuture<RelayResponse> future = agentRequestQueueService.submit(
                agentId,
                description,
                () -> sendRequestToAgent(
                        userId,
                        organizationId,
                        agent,
                        session,
                        method,
                        headers,
                        body
                )
        );

        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentCommunicationException(
                    "Interrupted while waiting for Tally Agent queue"
            );
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AgentCommunicationException(
                    "Tally Agent queue request failed: "
                            + (cause == null ? "Unknown error" : cause.getMessage())
            );
        }
    }

    // ============================================================
    // ACTUAL REQUEST (RUNS INSIDE THE AGENT QUEUE)
    // ============================================================

    private RelayResponse sendRequestToAgent(
            String userId,
            String organizationId,
            Agent agent,
            WebSocketSession session,
            String method,
            Map<String, String> headers,
            String body
    ) {

        String agentId = agent.getAgentId();
        String requestId = UUID.randomUUID().toString();

        // 1. Build request node
        ObjectNode request = mapper.createObjectNode();
        request.put("type", "PROXY_REQUEST");
        request.put("requestId", requestId);
        request.put("method", method == null || method.isBlank() ? "POST" : method);
        request.put("body", body == null ? "" : body);

        ObjectNode headersNode = request.putObject("headers");
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (key != null && !key.isBlank()) {
                    headersNode.put(key, value == null ? "" : value);
                }
            });
        }

        // 2. Register before send
        CompletableFuture<RelayResponse> future =
                pendingRequestRegistry.register(requestId, agentId);

        // 3. Concurrency-safe send
        try {
            WebSocketSession safeSession = agentConnectionService.getAgentSession(agentId);
            if (safeSession == null || !safeSession.isOpen()) {
                safeSession = session;
            }

            if (safeSession == null || !safeSession.isOpen()) {
                pendingRequestRegistry.fail(
                        requestId,
                        new AgentOfflineException("Tally Agent is offline")
                );
                throw new AgentOfflineException("Tally Agent is offline");
            }

            log.info("TALLY QUEUE ROUTING | userId={} | organizationId={} | agentId={} | agentName={} | requestId={}",
                    userId, organizationId, agentId, agent.getAgentName(), requestId);

            String requestJson = mapper.writeValueAsString(request);

            agentConnectionService.send(safeSession, new TextMessage(requestJson));

            log.info("TALLY REQUEST SENT | requestId={} | agentId={}", requestId, agentId);

        } catch (AgentOfflineException e) {
            throw e;
        } catch (Exception e) {
            pendingRequestRegistry.fail(requestId, e);
            throw new AgentCommunicationException(
                    "Failed to send request to Tally Agent: " + e.getMessage()
            );
        }

        // 4. Await response
        try {
            RelayResponse response = future.get();
            log.info("TALLY RESPONSE RECEIVED | requestId={} | agentId={} | status={} | isFileBacked={}",
                    requestId, agentId, response.status(), response.isFileBacked());
            return response;

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();

            if (cause instanceof AgentStartTimeoutException timeoutException) {
                throw timeoutException;
            }
            if (cause instanceof StreamIdleTimeoutException timeoutException) {
                throw timeoutException;
            }
            if (cause instanceof AgentDeadlineExceededException timeoutException) {
                throw timeoutException;
            }
            if (cause instanceof AgentTimeoutException timeoutException) {
                throw timeoutException;
            }
            if (cause instanceof TimeoutException) {
                throw new AgentTimeoutException(
                        "Tally Agent did not respond within deadline. Check TallyPrime and the Tally Agent on that PC."
                );
            }
            if (cause instanceof ResponseSizeLimitExceededException limitException) {
                throw limitException;
            }
            if (cause instanceof AgentDisconnectedException disconnectedException) {
                throw disconnectedException;
            }
            if (cause instanceof AgentOfflineException offlineException) {
                throw offlineException;
            }
            if (cause instanceof AgentResponseException responseException) {
                throw responseException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            String message = cause == null ? "Unknown error" : cause.getMessage();
            throw new AgentCommunicationException("Error waiting for Tally Agent response: " + message);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pendingRequestRegistry.fail(requestId, e);
            throw new AgentCommunicationException("Interrupted while waiting for Tally Agent response");
        }
    }

    // ============================================================
    // AUTOMATIC AGENT SELECTION
    // ============================================================

    private AgentSelection findConnectedAgent(String userId) {
        User user = userRepository.findById(userId).orElseThrow(
                () -> new AgentUserNotFoundException("User not found")
        );

        String organizationId = user.getOrganizationId();
        if (organizationId == null || organizationId.isBlank()) {
            throw new AgentOrganizationNotAssignedException("User is not associated with an organization");
        }

        List<Agent> agents = agentConnectionService.getAgentsByOrganizationId(organizationId);
        if (agents == null || agents.isEmpty()) {
            throw new AgentNotFoundException("No Tally Agent found for your organization");
        }

        List<AgentSelection> connectedAgents = agents.stream()
                .filter(agent -> agent != null && agent.getAgentId() != null && !agent.getAgentId().isBlank())
                .filter(agent -> organizationId.equals(agent.getOrganizationId()))
                .filter(agent -> agentConnectionService.isAgentConnected(agent.getAgentId()))
                .map(agent -> {
                    WebSocketSession session = agentConnectionService.getAgentSession(agent.getAgentId());
                    if (session == null || !session.isOpen()) {
                        return null;
                    }
                    return new AgentSelection(organizationId, agent, session);
                })
                .filter(selection -> selection != null)
                .toList();

        if (connectedAgents.isEmpty()) {
            throw new AgentOfflineException(
                    "No connected Tally Agent found for your organization. Start the Tally Agent on the required PC."
            );
        }

        AgentSelection selected = connectedAgents.stream()
                .min(Comparator.comparingInt(
                        selection -> agentRequestQueueService.getLoad(selection.agent().getAgentId())
                ))
                .orElseThrow(() -> new AgentOfflineException("No usable Tally Agent found"));

        int selectedLoad = agentRequestQueueService.getLoad(selected.agent().getAgentId());

        log.info("AUTO TALLY ROUTING | userId={} | organizationId={} | agentId={} | agentName={} | queueLoad={}",
                userId, organizationId, selected.agent().getAgentId(), selected.agent().getAgentName(), selectedLoad);

        return selected;
    }

    // ============================================================
    // FIND EXACT CONNECTED AGENT (BACKWARD COMPATIBILITY)
    // ============================================================

    private WebSocketSession findConnectedAgentSession(String userId, String agentId) {
        if (agentId == null || agentId.isBlank()) {
            throw new AgentIdRequiredException("agentId is required");
        }

        User user = userRepository.findById(userId).orElseThrow(
                () -> new AgentUserNotFoundException("User not found")
        );

        String organizationId = user.getOrganizationId();
        if (organizationId == null || organizationId.isBlank()) {
            throw new AgentOrganizationNotAssignedException("User is not associated with an organization");
        }

        Agent agent = agentConnectionService.getAgentsByOrganizationId(organizationId).stream()
                .filter(a -> a != null && agentId.equals(a.getAgentId()))
                .findFirst()
                .orElseThrow(() -> new AgentNotFoundException("Tally Agent not found for your organization"));

        if (!organizationId.equals(agent.getOrganizationId())) {
            throw new AgentNotFoundException("Tally Agent not found");
        }

        if (!agentConnectionService.isAgentConnected(agent.getAgentId())) {
            throw new AgentOfflineException(
                    "Selected Tally Agent is offline. Start the Tally Agent on the selected PC."
            );
        }

        WebSocketSession session = agentConnectionService.getAgentSession(agent.getAgentId());
        if (session == null || !session.isOpen()) {
            throw new AgentOfflineException("Selected Tally Agent WebSocket is not connected");
        }

        log.info("EXACT TALLY ROUTING | userId={} | organizationId={} | agentId={} | agentName={}",
                userId, organizationId, agent.getAgentId(), agent.getAgentName());

        return session;
    }

    // ============================================================
    // REQUEST DESCRIPTION
    // ============================================================

    private String buildRequestDescription(String method, String body) {
        String actualMethod = method == null || method.isBlank() ? "POST" : method;
        int bodyLength = body == null ? 0 : body.length();
        return actualMethod + " Tally request (bodyLength=" + bodyLength + ")";
    }

    private record AgentSelection(
            String organizationId,
            Agent agent,
            WebSocketSession session
    ) {}
}