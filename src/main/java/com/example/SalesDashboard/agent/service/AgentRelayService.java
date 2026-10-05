package com.example.SalesDashboard.agent.service;

import com.example.SalesDashboard.agent.entity.Agent;
import com.example.SalesDashboard.agent.exception.AgentCommunicationException;
import com.example.SalesDashboard.agent.exception.AgentIdRequiredException;
import com.example.SalesDashboard.agent.exception.AgentNotFoundException;
import com.example.SalesDashboard.agent.exception.AgentOfflineException;
import com.example.SalesDashboard.agent.exception.AgentOrganizationNotAssignedException;
import com.example.SalesDashboard.agent.exception.AgentResponseException;
import com.example.SalesDashboard.agent.exception.AgentTimeoutException;
import com.example.SalesDashboard.agent.exception.AgentUserNotFoundException;
import com.example.SalesDashboard.user.entity.User;
import com.example.SalesDashboard.user.repository.UserRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRelayService {

    private final AgentConnectionService agentConnectionService;

    private final PendingRequestRegistry pendingRequestRegistry;

    private final AgentRequestQueueService agentRequestQueueService;

    private final UserRepository userRepository;

    private final ObjectMapper mapper =
            new ObjectMapper();

    // ============================================================
    // RELAY RESPONSE
    // ============================================================

    public record RelayResponse(
            int status,
            String body
    ) {
    }

    // ============================================================
    // MAIN RELAY
    // AUTOMATIC AGENT SELECTION
    // + PER-AGENT QUEUE
    // ============================================================

    /**
     * Normal application APIs MUST use this method.
     *
     * Frontend sends:
     *
     * Authorization: Bearer <JWT>
     *
     * Frontend does NOT send agentId.
     *
     * Flow:
     *
     * JWT
     *   ↓
     * User
     *   ↓
     * Organization
     *   ↓
     * Connected Agent
     *   ↓
     * Agent Queue
     *   ↓
     * Tally Agent
     *   ↓
     * TallyPrime
     */
    public RelayResponse relay(
            String userId,
            String method,
            Map<String, String> headers,
            String body
    ) {

        AgentSelection selection =
                findConnectedAgent(
                        userId
                );

        String agentId =
                selection.agent().getAgentId();

        String description =
                buildRequestDescription(
                        method,
                        body
                );

        CompletableFuture<RelayResponse> future;

        try {

            future =
                    agentRequestQueueService.submit(
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
                    "Failed to submit request to Tally Agent queue: "
                            + e.getMessage()
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

            Throwable cause =
                    e.getCause();

            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            throw new AgentCommunicationException(
                    "Tally Agent queue request failed: "
                            + (
                            cause == null
                                    ? "Unknown error"
                                    : cause.getMessage()
                    )
            );
        }
    }

    // ============================================================
    // EXACT AGENT RELAY
    // BACKWARD COMPATIBILITY ONLY
    // ALSO USES QUEUE
    // ============================================================

    /**
     * Kept for older backend code.
     *
     * IMPORTANT:
     *
     * Normal frontend APIs should NOT use this overload.
     *
     * It still requires agentId internally, but the frontend
     * architecture does not expose agentId.
     */
    public RelayResponse relay(
            String userId,
            String agentId,
            String method,
            Map<String, String> headers,
            String body
    ) {

        WebSocketSession session =
                findConnectedAgentSession(
                        userId,
                        agentId
                );

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new AgentUserNotFoundException(
                                                "User not found"
                                        )
                        );

        String organizationId =
                user.getOrganizationId();

        Agent agent =
                agentConnectionService
                        .getAgentsByOrganizationId(
                                organizationId
                        )
                        .stream()
                        .filter(
                                a ->
                                        a != null
                                                && agentId.equals(
                                                a.getAgentId()
                                        )
                        )
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AgentNotFoundException(
                                                "Tally Agent not found"
                                        )
                        );

        String description =
                buildRequestDescription(
                        method,
                        body
                );

        CompletableFuture<RelayResponse> future =
                agentRequestQueueService.submit(
                        agentId,
                        description,
                        () ->
                                sendRequestToAgent(
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

            Throwable cause =
                    e.getCause();

            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            throw new AgentCommunicationException(
                    "Tally Agent queue request failed: "
                            + (
                            cause == null
                                    ? "Unknown error"
                                    : cause.getMessage()
                    )
            );
        }
    }

    // ============================================================
    // ACTUAL REQUEST
    // THIS METHOD RUNS INSIDE THE AGENT QUEUE
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

        String agentId =
                agent.getAgentId();

        String requestId =
                UUID.randomUUID().toString();

        // ========================================================
        // BUILD PROXY REQUEST
        // ========================================================

        ObjectNode request =
                mapper.createObjectNode();

        request.put(
                "type",
                "PROXY_REQUEST"
        );

        request.put(
                "requestId",
                requestId
        );

        request.put(
                "method",
                method == null
                        || method.isBlank()
                        ? "POST"
                        : method
        );

        request.put(
                "body",
                body == null
                        ? ""
                        : body
        );

        ObjectNode headersNode =
                request.putObject(
                        "headers"
                );

        if (headers != null) {

            headers.forEach(
                    (
                            key,
                            value
                    ) -> {

                        if (key != null
                                && !key.isBlank()) {

                            headersNode.put(
                                    key,
                                    value == null
                                            ? ""
                                            : value
                            );
                        }
                    }
            );
        }

        // ========================================================
        // REGISTER BEFORE SEND
        //
        // IMPORTANT:
        //
        // This method itself runs only after the request
        // reaches the front of the Agent queue.
        //
        // Therefore the 60-second PendingRequest timeout
        // does NOT start while the request is waiting in queue.
        // ========================================================

        CompletableFuture<JsonNode> future =
                pendingRequestRegistry.register(
                        requestId
                );

        // ========================================================
        // CHECK WEBSOCKET
        // ========================================================

        try {

            if (session == null
                    || !session.isOpen()) {

                pendingRequestRegistry.fail(
                        requestId,
                        new IllegalStateException(
                                "Agent WebSocket is closed"
                        )
                );

                throw new AgentOfflineException(
                        "Tally Agent is offline"
                );
            }

            // ====================================================
            // ROUTING LOG
            // ====================================================

            log.info(
                    "TALLY QUEUE ROUTING | userId={} | organizationId={} | agentId={} | agentName={} | requestId={}",
                    userId,
                    organizationId,
                    agentId,
                    agent.getAgentName(),
                    requestId
            );

            // ====================================================
            // SERIALIZE
            // ====================================================

            String requestJson =
                    mapper.writeValueAsString(
                            request
                    );

            // ====================================================
            // SEND
            // ====================================================

            session.sendMessage(
                    new TextMessage(
                            requestJson
                    )
            );

            log.info(
                    "TALLY REQUEST SENT | requestId={} | agentId={}",
                    requestId,
                    agentId
            );

        } catch (AgentOfflineException e) {

            throw e;

        } catch (Exception e) {

            pendingRequestRegistry.fail(
                    requestId,
                    e
            );

            throw new AgentCommunicationException(
                    "Failed to send request to Tally Agent: "
                            + e.getMessage()
            );
        }

        // ========================================================
        // WAIT FOR RESPONSE
        //
        // PendingRequestRegistry supports:
        //
        // PROXY_RESPONSE
        //
        // and
        //
        // PROXY_RESPONSE_START
        // PROXY_RESPONSE_CHUNK
        // PROXY_RESPONSE_END
        // ========================================================

        JsonNode response;

        try {

            response =
                    future.get();

        } catch (ExecutionException e) {

            Throwable cause =
                    e.getCause();

            if (cause instanceof TimeoutException) {

                throw new AgentTimeoutException(
                        "Tally Agent did not respond "
                                + "within 60 seconds. "
                                + "Check TallyPrime and the "
                                + "Tally Agent on that PC."
                );
            }

            String message =
                    cause == null
                            ? "Unknown error"
                            : cause.getMessage();

            throw new AgentCommunicationException(
                    "Error waiting for Tally Agent response: "
                            + message
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            pendingRequestRegistry.fail(
                    requestId,
                    e
            );

            throw new AgentCommunicationException(
                    "Interrupted while waiting for "
                            + "Tally Agent response"
            );
        }

        // ========================================================
        // PROCESS AGENT RESPONSE
        // ========================================================

        boolean ok =
                response.path("ok")
                        .asBoolean(false);

        if (!ok) {

            String error =
                    response.path("error")
                            .asText(
                                    "Unknown agent error"
                            );

            log.warn(
                    "Agent relay failed | userId={} | organizationId={} | agentId={} | requestId={} | error={}",
                    userId,
                    organizationId,
                    agentId,
                    requestId,
                    error
            );

            throw new AgentResponseException(
                    "Tally Agent error: "
                            + error
            );
        }

        int status =
                response.path("status")
                        .asInt(200);

        String responseBody =
                response.path("body")
                        .asText("");

        log.info(
                "TALLY RESPONSE RECEIVED | requestId={} | agentId={} | status={} | bodyLength={}",
                requestId,
                agentId,
                status,
                responseBody.length()
        );

        return new RelayResponse(
                status,
                responseBody
        );
    }

    // ============================================================
    // AUTOMATIC AGENT SELECTION
    // ============================================================

    /**
     * Finds a connected Agent belonging to the user's organization.
     *
     * When multiple Agents are connected:
     *
     * - choose the Agent with the lowest current queue load
     * - different Agents can process in parallel
     *
     * Frontend still does NOT send agentId.
     */
    private AgentSelection findConnectedAgent(
            String userId
    ) {

        // ========================================================
        // 1. FIND USER
        // ========================================================

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new AgentUserNotFoundException(
                                                "User not found"
                                        )
                        );

        // ========================================================
        // 2. ORGANIZATION
        // ========================================================

        String organizationId =
                user.getOrganizationId();

        if (organizationId == null
                || organizationId.isBlank()) {

            throw new AgentOrganizationNotAssignedException(
                    "User is not associated with an organization"
            );
        }

        // ========================================================
        // 3. GET ORGANIZATION AGENTS
        // ========================================================

        List<Agent> agents =
                agentConnectionService
                        .getAgentsByOrganizationId(
                                organizationId
                        );

        if (agents == null
                || agents.isEmpty()) {

            throw new AgentNotFoundException(
                    "No Tally Agent found for your organization"
            );
        }

        // ========================================================
        // 4. FIND CONNECTED AGENTS
        // ========================================================

        List<AgentSelection> connectedAgents =
                agents.stream()

                        .filter(
                                agent ->
                                        agent != null
                        )

                        .filter(
                                agent ->
                                        agent.getAgentId() != null
                                                && !agent.getAgentId().isBlank()
                        )

                        .filter(
                                agent ->
                                        organizationId.equals(
                                                agent.getOrganizationId()
                                        )
                        )

                        .filter(
                                agent ->
                                        agentConnectionService
                                                .isAgentConnected(
                                                        agent.getAgentId()
                                                )
                        )

                        .map(
                                agent -> {

                                    WebSocketSession session =
                                            agentConnectionService
                                                    .getAgentSession(
                                                            agent.getAgentId()
                                                    );

                                    if (session == null
                                            || !session.isOpen()) {

                                        return null;
                                    }

                                    return new AgentSelection(
                                            organizationId,
                                            agent,
                                            session
                                    );
                                }
                        )

                        .filter(
                                selection ->
                                        selection != null
                        )

                        .toList();

        if (connectedAgents.isEmpty()) {

            throw new AgentOfflineException(
                    "No connected Tally Agent found for your organization. "
                            + "Start the Tally Agent on the required PC."
            );
        }

        // ========================================================
        // 5. LOWEST QUEUE LOAD
        // ========================================================

        AgentSelection selected =
                connectedAgents.stream()
                        .min(
                                Comparator.comparingInt(
                                        selection ->
                                                agentRequestQueueService
                                                        .getLoad(
                                                                selection.agent()
                                                                        .getAgentId()
                                                        )
                                )
                        )
                        .orElseThrow(
                                () ->
                                        new AgentOfflineException(
                                                "No usable Tally Agent found"
                                        )
                        );

        int selectedLoad =
                agentRequestQueueService.getLoad(
                        selected.agent().getAgentId()
                );

        log.info(
                "AUTO TALLY ROUTING | userId={} | organizationId={} | agentId={} | agentName={} | queueLoad={}",
                userId,
                organizationId,
                selected.agent().getAgentId(),
                selected.agent().getAgentName(),
                selectedLoad
        );

        return selected;
    }

    // ============================================================
    // FIND EXACT CONNECTED AGENT
    // BACKWARD COMPATIBILITY
    // ============================================================

    private WebSocketSession findConnectedAgentSession(
            String userId,
            String agentId
    ) {

        if (agentId == null
                || agentId.isBlank()) {

            throw new AgentIdRequiredException(
                    "agentId is required"
            );
        }

        // ========================================================
        // FIND USER
        // ========================================================

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new AgentUserNotFoundException(
                                                "User not found"
                                        )
                        );

        String organizationId =
                user.getOrganizationId();

        if (organizationId == null
                || organizationId.isBlank()) {

            throw new AgentOrganizationNotAssignedException(
                    "User is not associated with an organization"
            );
        }

        // ========================================================
        // FIND AGENT
        // ========================================================

        Agent agent =
                agentConnectionService
                        .getAgentsByOrganizationId(
                                organizationId
                        )
                        .stream()
                        .filter(
                                a ->
                                        a != null
                                                && agentId.equals(
                                                a.getAgentId()
                                        )
                        )
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AgentNotFoundException(
                                                "Tally Agent not found "
                                                        + "for your organization"
                                        )
                        );

        // ========================================================
        // SECURITY
        // ========================================================

        if (!organizationId.equals(
                agent.getOrganizationId()
        )) {

            throw new AgentNotFoundException(
                    "Tally Agent not found"
            );
        }

        // ========================================================
        // CHECK ACTUAL WEBSOCKET STATE
        // ========================================================

        if (!agentConnectionService
                .isAgentConnected(
                        agent.getAgentId()
                )) {

            throw new AgentOfflineException(
                    "Selected Tally Agent is offline. "
                            + "Start the Tally Agent on the "
                            + "selected PC."
            );
        }

        WebSocketSession session =
                agentConnectionService
                        .getAgentSession(
                                agent.getAgentId()
                        );

        if (session == null
                || !session.isOpen()) {

            throw new AgentOfflineException(
                    "Selected Tally Agent WebSocket is not connected"
            );
        }

        log.info(
                "EXACT TALLY ROUTING | userId={} | organizationId={} | agentId={} | agentName={}",
                userId,
                organizationId,
                agent.getAgentId(),
                agent.getAgentName()
        );

        return session;
    }

    // ============================================================
    // REQUEST DESCRIPTION
    // ============================================================

    private String buildRequestDescription(
            String method,
            String body
    ) {

        String actualMethod =
                method == null
                        || method.isBlank()
                        ? "POST"
                        : method;

        int bodyLength =
                body == null
                        ? 0
                        : body.length();

        return actualMethod
                + " Tally request"
                + " (bodyLength="
                + bodyLength
                + ")";
    }

    // ============================================================
    // RECORD
    // ============================================================

    private record AgentSelection(
            String organizationId,
            Agent agent,
            WebSocketSession session
    ) {
    }
}