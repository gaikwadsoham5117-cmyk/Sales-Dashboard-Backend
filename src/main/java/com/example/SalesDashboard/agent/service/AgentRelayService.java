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

/**
 * Relays Tally requests from the authenticated user to the
 * Tally Agent belonging to that user's organization.
 *
 * Flow:
 *
 * Owner/Employee
 *      ↓
 * JWT
 *      ↓
 * userId
 *      ↓
 * User.organizationId
 *      ↓
 * Agent.organizationId
 *      ↓
 * Connected Agent WebSocket
 *      ↓
 * Tally Agent
 *      ↓
 * TallyPrime
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRelayService {

    private final AgentConnectionService agentConnectionService;
    private final PendingRequestRegistry pendingRequestRegistry;
    private final UserRepository userRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    public record RelayResponse(int status, String body) {}

    /**
     * Relay a Tally request to the connected Agent
     * belonging to the authenticated user's organization.
     *
     * @param userId  MongoDB ID of authenticated user
     * @param method  HTTP method to use against Tally
     * @param headers headers to forward to Tally
     * @param body    raw Tally request body
     */
    public RelayResponse relay(
            String userId,
            String method,
            Map<String, String> headers,
            String body
    ) {

        /*
         * Find Agent using:
         *
         * userId
         *    ↓
         * organizationId
         *    ↓
         * Agent
         *    ↓
         * WebSocket session
         */
        WebSocketSession session =
                findConnectedAgentSession(userId);

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
                method == null ? "POST" : method
        );

        request.put(
                "body",
                body
        );

        ObjectNode headersNode =
                request.putObject("headers");

        if (headers != null) {
            headers.forEach(headersNode::put);
        }

        /*
         * Register request before sending it.
         *
         * Agent response will complete this Future
         * using the same requestId.
         */
        CompletableFuture<JsonNode> future =
                pendingRequestRegistry.register(requestId);

        try {

            session.sendMessage(
                    new TextMessage(
                            mapper.writeValueAsString(request)
                    )
            );

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

            /*
             * Wait for the Agent response.
             */
            response = future.get();

        } catch (ExecutionException e) {

            if (e.getCause() instanceof TimeoutException) {

                throw new ResponseStatusException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "Tally Agent did not respond in time. "
                                + "Ensure TallyPrime and "
                                + "TallyAPIConnectorV2.0.exe "
                                + "are running on the client PC."
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Error waiting for Tally Agent response: "
                            + e.getMessage()
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Interrupted while waiting for "
                            + "Tally Agent response"
            );
        }

        /*
         * Check Agent response.
         */
        boolean ok =
                response.path("ok").asBoolean(false);

        if (!ok) {

            String error =
                    response.path("error")
                            .asText("Unknown agent error");

            log.warn(
                    "Agent relay failed for organization user {}: {}",
                    userId,
                    error
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Tally Agent error: " + error
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

    /**
     * Finds the connected Tally Agent through the
     * authenticated user's organization.
     *
     * Flow:
     *
     * userId
     *    ↓
     * User
     *    ↓
     * organizationId
     *    ↓
     * Agents
     *    ↓
     * connected Agent
     *    ↓
     * WebSocket session
     */
    private WebSocketSession findConnectedAgentSession(
            String userId
    ) {

        /*
         * 1. Find authenticated user.
         */
        User user =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "User not found"
                                )
                        );

        /*
         * 2. Get organization ID from user.
         *
         * IMPORTANT:
         * We do NOT accept organizationId from
         * the frontend/request.
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
         * 3. Find all Agents belonging to
         *    this organization.
         */
        List<Agent> agents =
                agentConnectionService
                        .getAgentsByOrganizationId(
                                organizationId
                        );

        if (agents.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No Tally Agent has been set up "
                            + "for this organization yet. "
                            + "Download and run the agent first."
            );
        }

        /*
         * 4. Find a connected Agent.
         *
         * If an organization has multiple Agents,
         * the first currently connected Agent is used.
         */
        for (Agent agent : agents) {

            if (agentConnectionService
                    .isAgentConnected(
                            agent.getAgentId()
                    )) {

                WebSocketSession session =
                        agentConnectionService
                                .getAgentSession(
                                        agent.getAgentId()
                                );

                if (session != null
                        && session.isOpen()) {

                    log.debug(
                            "Routing Tally request | userId={} | organizationId={} | agentId={}",
                            userId,
                            organizationId,
                            agent.getAgentId()
                    );

                    return session;
                }
            }
        }

        /*
         * 5. Organization has Agent(s),
         *    but none are connected.
         */
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Your organization's Tally Agent is "
                        + "currently offline. "
                        + "Make sure it is running on "
                        + "the PC with TallyPrime."
        );
    }
}