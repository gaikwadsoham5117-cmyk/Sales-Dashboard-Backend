package com.example.SalesDashboard.agent.websocket;

import com.example.SalesDashboard.agent.exception.AgentCommunicationException;
import com.example.SalesDashboard.agent.exception.AgentDisconnectedException;
import com.example.SalesDashboard.agent.service.AgentConnectionService;
import com.example.SalesDashboard.agent.service.PendingRequestRegistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private final AgentConnectionService agentConnectionService;
    private final PendingRequestRegistry pendingRequestRegistry;
    private final ObjectMapper mapper = new ObjectMapper();

    // ============================================================
    // CONNECTION ESTABLISHED
    // ============================================================

    @Override
    public void afterConnectionEstablished(
            WebSocketSession session
    ) throws Exception {

        String agentId =
                (String) session.getAttributes().get("agentId");

        if (agentId == null || agentId.isBlank()) {
            log.warn("Agent WebSocket connected without agentId");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        log.info("TALLY AGENT CONNECTED | agentId={}", agentId);

        agentConnectionService.connectAgent(
                agentId,
                session
        );
    }

    // ============================================================
    // MESSAGE
    // ============================================================

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) throws Exception {

        String payload = message.getPayload();

        if (payload == null || payload.isBlank()) {
            return;
        }

        JsonNode node;

        try {
            node = mapper.readTree(payload);
        } catch (Exception e) {
            log.warn("Invalid JSON received from Tally Agent");
            return;
        }

        String agentId =
                (String) session.getAttributes().get("agentId");

        String type =
                node.path("type").asText("");

        if (type.isBlank()) {
            log.warn("Received WebSocket message without type | agentId={}", agentId);
            return;
        }

        switch (type) {

            // ====================================================
            // AUTH
            // ====================================================

            case "AUTH" -> {
                touchAgentIfPresent(agentId);

                ObjectNode ack = mapper.createObjectNode();
                ack.put("type", "AUTH_OK");

                sendJson(agentId, session, ack);

                log.info("TALLY AGENT AUTH OK | agentId={}", agentId);
            }

            // ====================================================
            // PING
            // ====================================================

            case "PING" -> {
                touchAgentIfPresent(agentId);

                ObjectNode pong = mapper.createObjectNode();
                pong.put("type", "PONG");

                sendJson(agentId, session, pong);
            }

            // ====================================================
            // PONG
            // ====================================================

            case "PONG" -> {
                // Heartbeat response keeps agent ONLINE
                touchAgentIfPresent(agentId);
            }

            // ====================================================
            // NON-STREAMING PROXY RESPONSE
            // ====================================================

            case "PROXY_RESPONSE" -> {
                touchAgentIfPresent(agentId);
                handleProxyResponse(node);
            }

            // ====================================================
            // STREAM START
            // ====================================================

            case "PROXY_RESPONSE_START" -> {
                touchAgentIfPresent(agentId);
                handleProxyResponseStart(node);
            }

            // ====================================================
            // STREAM CHUNK
            // NOTE: Never call touchAgent() for chunks!
            // ====================================================

            case "PROXY_RESPONSE_CHUNK" -> {
                handleProxyResponseChunk(node);
            }

            // ====================================================
            // STREAM END
            // ====================================================

            case "PROXY_RESPONSE_END" -> {
                touchAgentIfPresent(agentId);
                handleProxyResponseEnd(node);
            }

            // ====================================================
            // UNKNOWN
            // ====================================================

            default -> {
                log.debug("Ignoring unknown Agent WebSocket message | agentId={} | type={}", agentId, type);
            }
        }
    }

    private void touchAgentIfPresent(String agentId) {
        if (agentId != null && !agentId.isBlank()) {
            agentConnectionService.touchAgent(agentId);
        }
    }

    // ============================================================
    // NORMAL PROXY RESPONSE
    // ============================================================

    private void handleProxyResponse(JsonNode node) {
        String requestId = node.path("requestId").asText("");

        if (requestId == null || requestId.isBlank()) {
            log.warn("PROXY_RESPONSE received without requestId");
            return;
        }

        log.debug("TALLY PROXY RESPONSE COMPLETE | requestId={}", requestId);

        pendingRequestRegistry.complete(requestId, node);
    }

    // ============================================================
    // STREAM START
    // ============================================================

    private void handleProxyResponseStart(JsonNode node) {
        String requestId = node.path("requestId").asText("");

        if (requestId == null || requestId.isBlank()) {
            log.warn("PROXY_RESPONSE_START received without requestId");
            return;
        }

        int status = node.path("status").asInt(200);
        String encoding = node.has("encoding") && !node.path("encoding").isNull()
                ? node.path("encoding").asText(null)
                : null;

        log.info("TALLY STREAM START | requestId={} | status={} | encoding={}", requestId, status, encoding);

        pendingRequestRegistry.startStream(requestId, status, encoding);
    }

    // ============================================================
    // STREAM CHUNK
    // ============================================================

    private void handleProxyResponseChunk(JsonNode node) {
        String requestId = node.path("requestId").asText("");

        if (requestId == null || requestId.isBlank()) {
            return;
        }

        String chunk = node.path("chunk").asText("");

        if (chunk == null || chunk.isEmpty()) {
            return;
        }

        String encoding = node.has("encoding") && !node.path("encoding").isNull()
                ? node.path("encoding").asText(null)
                : null;

        Long compressedBytes = node.has("compressedBytes") && !node.path("compressedBytes").isNull()
                ? node.path("compressedBytes").asLong()
                : null;

        pendingRequestRegistry.appendChunk(requestId, chunk, encoding, compressedBytes);
    }

    // ============================================================
    // STREAM END
    // ============================================================

    private void handleProxyResponseEnd(JsonNode node) {
        String requestId = node.path("requestId").asText("");

        if (requestId == null || requestId.isBlank()) {
            log.warn("PROXY_RESPONSE_END received without requestId");
            return;
        }

        log.info("TALLY STREAM END | requestId={}", requestId);

        pendingRequestRegistry.completeStream(requestId);
    }

    // ============================================================
    // CONNECTION CLOSED
    // ============================================================

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) throws Exception {

        String agentId =
                (String) session.getAttributes().get("agentId");

        log.info("TALLY AGENT DISCONNECTED | agentId={} | status={}", agentId, status);

        if (agentId != null && !agentId.isBlank()) {
            // Fail in-flight requests immediately
            pendingRequestRegistry.failRequestsForAgent(
                    agentId,
                    new AgentDisconnectedException("Tally Agent disconnected: " + status)
            );

            agentConnectionService.disconnectAgent(
                    agentId,
                    session
            );
        }
    }

    // ============================================================
    // TRANSPORT ERROR
    // ============================================================

    @Override
    public void handleTransportError(
            WebSocketSession session,
            Throwable exception
    ) throws Exception {

        String agentId =
                (String) session.getAttributes().get("agentId");

        log.warn("TALLY AGENT WEBSOCKET TRANSPORT ERROR | agentId={} | error={}", agentId, exception.getMessage());

        if (agentId != null && !agentId.isBlank()) {
            pendingRequestRegistry.failRequestsForAgent(
                    agentId,
                    new AgentCommunicationException("Agent WebSocket transport error: " + exception.getMessage())
            );
        }

        if (session.isOpen()) {
            session.close();
        }
    }

    // ============================================================
    // CONCURRENCY-SAFE SEND JSON
    // ============================================================

    private void sendJson(
            String agentId,
            WebSocketSession session,
            ObjectNode node
    ) {
        try {
            TextMessage textMessage = new TextMessage(mapper.writeValueAsString(node));
            if (agentId != null && !agentId.isBlank()) {
                WebSocketSession safeSession = agentConnectionService.getAgentSession(agentId);
                if (safeSession != null && safeSession.isOpen()) {
                    safeSession.sendMessage(textMessage);
                    return;
                }
            }
            if (session != null && session.isOpen()) {
                session.sendMessage(textMessage);
            }
        } catch (Exception e) {
            log.warn("Failed to send WebSocket message to Tally Agent: {}", e.getMessage());
        }
    }
}