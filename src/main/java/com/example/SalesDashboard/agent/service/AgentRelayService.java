package com.example.SalesDashboard.agent.service;

import com.example.SalesDashboard.agent.entity.Agent;
import com.example.SalesDashboard.user.entity.User;
import com.example.SalesDashboard.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

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
    private final UserRepository userRepository;

    private final ObjectMapper mapper =
            new ObjectMapper();

    public record RelayResponse(
            int status,
            String body
    ) {
    }

    // ============================================================
    // MAIN RELAY - AUTOMATIC AGENT SELECTION
    // ============================================================
    /*
     * Used by APIs such as:
     *
     * GET /api/company/all
     *
     * Frontend sends ONLY JWT.
     *
     * JWT
     *   ↓
     * userId
     *   ↓
     * organizationId
     *   ↓
     * connected Tally Agent
     *   ↓
     * Tally PC
     */
    public RelayResponse relay(
            String userId,
            String method,
            Map<String, String> headers,
            String body
    ) {

        AgentSelection selection =
                findConnectedAgent(userId);

        return sendRequestToAgent(
                userId,
                selection.organizationId(),
                selection.agent(),
                selection.session(),
                method,
                headers,
                body
        );
    }

    // ============================================================
    // EXACT AGENT RELAY
    // ============================================================
    /*
     * This method is kept for APIs that specifically need
     * an agentId.
     *
     * It is NOT used by /api/company/all.
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
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "User not found"
                                )
                        );

        Agent agent =
                agentConnectionService
                        .getAgentsByOrganizationId(
                                user.getOrganizationId()
                        )
                        .stream()
                        .filter(a ->
                                agentId.equals(
                                        a.getAgentId()
                                )
                        )
                        .findFirst()
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Tally Agent not found"
                                )
                        );

        return sendRequestToAgent(
                userId,
                user.getOrganizationId(),
                agent,
                session,
                method,
                headers,
                body
        );
    }

    // ============================================================
    // SEND REQUEST TO AGENT
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
                    headersNode::put
            );
        }

        /*
         * Register BEFORE sending the request.
         */
        CompletableFuture<JsonNode> future =
                pendingRequestRegistry.register(
                        requestId
                );

        try {

            if (!session.isOpen()) {

                pendingRequestRegistry.fail(
                        requestId,
                        new IllegalStateException(
                                "Agent WebSocket is closed"
                        )
                );

                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Tally Agent is offline"
                );
            }

            log.info(
                    "TALLY ROUTING | userId={} | organizationId={} | agentId={} | agentName={}",
                    userId,
                    organizationId,
                    agentId,
                    agent.getAgentName()
            );

            session.sendMessage(
                    new TextMessage(
                            mapper.writeValueAsString(
                                    request
                            )
                    )
            );

        } catch (ResponseStatusException e) {

            throw e;

        } catch (Exception e) {

            pendingRequestRegistry.fail(
                    requestId,
                    e
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to send request to Tally Agent: "
                            + e.getMessage()
            );
        }

        JsonNode response;

        try {

            response =
                    future.get();

        } catch (ExecutionException e) {

            Throwable cause =
                    e.getCause();

            if (cause instanceof TimeoutException) {

                throw new ResponseStatusException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "Tally Agent did not respond "
                                + "within 20 seconds. "
                                + "Check TallyPrime and the "
                                + "Tally Agent on that PC."
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Error waiting for Tally Agent response: "
                            + cause.getMessage()
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Interrupted while waiting for "
                            + "Tally Agent response"
            );
        }

        // ========================================================
        // AGENT RESPONSE
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
                    "Agent relay failed | userId={} | organizationId={} | agentId={} | error={}",
                    userId,
                    organizationId,
                    agentId,
                    error
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
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

        return new RelayResponse(
                status,
                responseBody
        );
    }

    // ============================================================
    // AUTOMATIC AGENT SELECTION
    // ============================================================

    private AgentSelection findConnectedAgent(
            String userId
    ) {

        /*
         * 1. Find authenticated user.
         */
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "User not found"
                                )
                        );

        /*
         * 2. Get organization from user.
         */
        String organizationId =
                user.getOrganizationId();

        if (organizationId == null
                || organizationId.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User is not associated with an organization"
            );
        }

        /*
         * 3. Get all agents belonging to this organization.
         */
        List<Agent> agents =
                agentConnectionService
                        .getAgentsByOrganizationId(
                                organizationId
                        );

        if (agents == null
                || agents.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No Tally Agent found for your organization"
            );
        }

        /*
         * 4. Find a REAL connected agent.
         *
         * We do NOT trust the MongoDB status field here.
         * The WebSocket connection is the source of truth.
         */
        for (Agent agent : agents) {

            String agentId =
                    agent.getAgentId();

            if (agentId == null
                    || agentId.isBlank()) {

                continue;
            }

            if (!organizationId.equals(
                    agent.getOrganizationId()
            )) {

                continue;
            }

            if (agentConnectionService
                    .isAgentConnected(agentId)) {

                WebSocketSession session =
                        agentConnectionService
                                .getAgentSession(
                                        agentId
                                );

                if (session != null
                        && session.isOpen()) {

                    log.info(
                            "AUTO TALLY ROUTING | userId={} | organizationId={} | agentId={} | agentName={}",
                            userId,
                            organizationId,
                            agentId,
                            agent.getAgentName()
                    );

                    return new AgentSelection(
                            organizationId,
                            agent,
                            session
                    );
                }
            }
        }

        /*
         * No connected agent was found.
         */
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "No connected Tally Agent found for your organization. "
                        + "Start the Tally Agent on the required PC."
        );
    }

    // ============================================================
    // FIND EXACT CONNECTED AGENT
    // ============================================================

    private WebSocketSession findConnectedAgentSession(
            String userId,
            String agentId
    ) {

        if (agentId == null
                || agentId.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "agentId is required"
            );
        }

        /*
         * Find authenticated user.
         */
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "User not found"
                                )
                        );

        String organizationId =
                user.getOrganizationId();

        if (organizationId == null
                || organizationId.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User is not associated with an organization"
            );
        }

        /*
         * Find requested agent.
         */
        Agent agent =
                agentConnectionService
                        .getAgentsByOrganizationId(
                                organizationId
                        )
                        .stream()
                        .filter(a ->
                                agentId.equals(
                                        a.getAgentId()
                                )
                        )
                        .findFirst()
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Tally Agent not found "
                                                + "for your organization"
                                )
                        );

        /*
         * Security check:
         * Agent MUST belong to user's organization.
         */
        if (!organizationId.equals(
                agent.getOrganizationId()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Tally Agent not found"
            );
        }

        /*
         * Check REAL WebSocket state.
         */
        if (!agentConnectionService
                .isAgentConnected(
                        agent.getAgentId()
                )) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
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

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
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
    // AGENT SELECTION RECORD
    // ============================================================

    private record AgentSelection(
            String organizationId,
            Agent agent,
            WebSocketSession session
    ) {
    }
}