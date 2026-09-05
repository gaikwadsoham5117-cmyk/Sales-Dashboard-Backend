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

        System.out.println(
                "[WEBSOCKET CONNECTED] Agent: " + agentId
        );

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

        String agentId =
                (String) session.getAttributes().get("agentId");

        String payload = message.getPayload();

        System.out.println(
                "[MESSAGE FROM " + agentId + "] " + payload
        );

        JsonNode node;

        try {
            node = mapper.readTree(payload);
        } catch (Exception e) {
            System.out.println(
                    "[WEBSOCKET] Ignoring non-JSON message from "
                            + agentId + ": " + e.getMessage()
            );
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
                    System.out.println(
                            "[WEBSOCKET] PROXY_RESPONSE from "
                                    + agentId + " missing requestId, dropping"
                    );
                    return;
                }

                pendingRequestRegistry.complete(requestId, node);
            }

            default -> System.out.println(
                    "[WEBSOCKET] Unknown message type from "
                            + agentId + ": " + type
            );
        }
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) throws Exception {

        String agentId =
                (String) session.getAttributes().get("agentId");

        System.out.println(
                "[WEBSOCKET DISCONNECTED] Agent: "
                        + agentId
        );

        if (agentId != null) {

            agentConnectionService.disconnectAgent(agentId);
        }
    }

    @Override
    public void handleTransportError(
            WebSocketSession session,
            Throwable exception
    ) throws Exception {

        System.out.println(
                "[WEBSOCKET ERROR] "
                        + exception.getMessage()
        );

        session.close();
    }

    private void sendJson(WebSocketSession session, ObjectNode node) {
        try {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(node)));
        } catch (Exception e) {
            System.out.println(
                    "[WEBSOCKET] Failed to send message: " + e.getMessage()
            );
        }
    }
}
