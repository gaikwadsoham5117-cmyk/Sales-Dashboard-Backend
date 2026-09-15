package com.example.SalesDashboard.agent.websocket;

import com.example.SalesDashboard.agent.service.AgentConnectionService;
import com.example.SalesDashboard.agent.service.PendingRequestRegistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private final AgentConnectionService agentConnectionService;
    private final PendingRequestRegistry pendingRequestRegistry;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(
            WebSocketSession session
    ) throws Exception {

        String agentId =
                (String) session.getAttributes().get("agentId");

        agentConnectionService.connectAgent(
                agentId,
                session
        );
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) throws Exception {

        String payload = message.getPayload();

        JsonNode node;

        try {
            node = mapper.readTree(payload);
        } catch (Exception e) {
            return;
        }

        String type = node.path("type").asText("");

        switch (type) {

            case "AUTH" -> {
                // Real authentication already happened during the WS
                // handshake (AgentHandshakeInterceptor). This is just
                // an acknowledgement so the agent's logs show a clean
                // "accepted" state instead of waiting on nothing.
                ObjectNode ack = mapper.createObjectNode();
                ack.put("type", "AUTH_OK");
                sendJson(session, ack);
            }

            case "PING" -> {
                ObjectNode pong = mapper.createObjectNode();
                pong.put("type", "PONG");
                sendJson(session, pong);
            }

            case "PONG" -> {
                // Ack to our own PING, if/when the backend starts
                // sending heartbeats to agents. Nothing to do.
            }

            case "PROXY_RESPONSE" -> {
                String requestId = node.path("requestId").asText("");

                if (requestId.isBlank()) {
                    return;
                }

                pendingRequestRegistry.complete(requestId, node);
            }

            default -> {
                // Unknown message type; nothing to do.
            }
        }
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) throws Exception {

        String agentId =
                (String) session.getAttributes().get("agentId");

        if (agentId != null) {

            agentConnectionService.disconnectAgent(agentId);
        }
    }

    @Override
    public void handleTransportError(
            WebSocketSession session,
            Throwable exception
    ) throws Exception {

        session.close();
    }

    private void sendJson(WebSocketSession session, ObjectNode node) {
        try {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(node)));
        } catch (Exception e) {
            // Send failed; connection will be cleaned up via
            // afterConnectionClosed / handleTransportError.
        }
    }
}