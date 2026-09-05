package com.example.SalesDashboard.agent.service;

import com.example.SalesDashboard.agent.entity.Agent;

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
 * The missing bridge: given a userId (from the JWT of the person calling
 * /api/tally/**), find that user's connected Tally Agent, forward the
 * request to it over the agent's WebSocket session as a PROXY_REQUEST, and
 * block until the matching PROXY_RESPONSE arrives (or times out).
 *
 * Backend -> agent -> localhost:9000 -> back to backend -> back to this
 * user's dashboard, all keyed by userId -> agentId -> requestId.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRelayService {

    private final AgentConnectionService agentConnectionService;
    private final PendingRequestRegistry pendingRequestRegistry;
    private final ObjectMapper mapper = new ObjectMapper();

    public record RelayResponse(int status, String body) {}

    /**
     * @param userId  the Mongo _id of the logged-in user (from the JWT)
     * @param method  HTTP method to use against Tally ("POST" for the
     *                Tally XML/JSON-over-HTTP protocol)
     * @param headers headers to forward to Tally (e.g. "Content-Type")
     * @param body    the raw Tally request body
     */
    public RelayResponse relay(
            String userId,
            String method,
            Map<String, String> headers,
            String body
    ) {

        WebSocketSession session = findConnectedAgentSession(userId);

        String requestId = UUID.randomUUID().toString();

        ObjectNode request = mapper.createObjectNode();
        request.put("type", "PROXY_REQUEST");
        request.put("requestId", requestId);
        request.put("method", method == null ? "POST" : method);
        request.put("body", body);

        ObjectNode headersNode = request.putObject("headers");
        if (headers != null) {
            headers.forEach(headersNode::put);
        }

        CompletableFuture<JsonNode> future =
                pendingRequestRegistry.register(requestId);

        try {

            session.sendMessage(
                    new TextMessage(mapper.writeValueAsString(request))
            );

        } catch (Exception e) {

            pendingRequestRegistry.fail(requestId, e);

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to send request to Tally Agent: " + e.getMessage()
            );
        }

        JsonNode response;

        try {

            response = future.get();

        } catch (ExecutionException e) {

            if (e.getCause() instanceof TimeoutException) {

                throw new ResponseStatusException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "Tally Agent did not respond in time. "
                                + "Ensure TallyPrime and TallyAPIConnectorV2.0.exe are running on the client PC."
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Error waiting for Tally Agent response: " + e.getMessage()
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Interrupted while waiting for Tally Agent response"
            );
        }

        boolean ok = response.path("ok").asBoolean(false);

        if (!ok) {

            String error = response.path("error").asText("Unknown agent error");

            log.warn("Agent relay failed for user {}: {}", userId, error);

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Tally Agent error: " + error
            );
        }

        int status = response.path("status").asInt(200);
        String responseBody = response.path("body").asText("");

        return new RelayResponse(status, responseBody);
    }

    /**
     * Finds the caller's Tally Agent and returns its live WebSocket
     * session. A user can technically own multiple agents (multiple
     * client PCs); this picks the first one that is currently ONLINE
     * and connected.
     */
    private WebSocketSession findConnectedAgentSession(String userId) {

        List<Agent> agents =
                agentConnectionService.getAgentsByUserId(userId);

        if (agents.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No Tally Agent has been set up for this account yet. "
                            + "Download and run the agent first."
            );
        }

        for (Agent agent : agents) {

            if (agentConnectionService.isAgentConnected(agent.getAgentId())) {

                WebSocketSession session =
                        agentConnectionService.getAgentSession(agent.getAgentId());

                if (session != null && session.isOpen()) {
                    return session;
                }
            }
        }

        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Your Tally Agent is currently offline. "
                        + "Make sure it is running on the PC with TallyPrime."
        );
    }
}
