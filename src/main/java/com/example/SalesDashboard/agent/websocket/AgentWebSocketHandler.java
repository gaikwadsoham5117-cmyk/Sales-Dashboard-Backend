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
public class AgentWebSocketHandler
        extends TextWebSocketHandler {

    private final AgentConnectionService agentConnectionService;

    private final PendingRequestRegistry pendingRequestRegistry;

    private final ObjectMapper mapper =
            new ObjectMapper();


    // ============================================================
    // CONNECTION ESTABLISHED
    // ============================================================

    @Override
    public void afterConnectionEstablished(
            WebSocketSession session
    ) throws Exception {

        String agentId =
                (String) session
                        .getAttributes()
                        .get("agentId");

        if (agentId == null || agentId.isBlank()) {

            session.close(
                    CloseStatus.POLICY_VIOLATION
            );

            return;
        }

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

        String payload =
                message.getPayload();

        JsonNode node;

        try {

            node =
                    mapper.readTree(payload);

        } catch (Exception e) {

            return;
        }

        String agentId =
                (String) session
                        .getAttributes()
                        .get("agentId");

        /*
         * Every valid message proves that the agent is alive.
         */
        if (agentId != null) {

            agentConnectionService.touchAgent(
                    agentId
            );
        }

        String type =
                node.path("type")
                        .asText("");


        switch (type) {

            // ----------------------------------------------------
            // AUTH
            // ----------------------------------------------------

            case "AUTH" -> {

                ObjectNode ack =
                        mapper.createObjectNode();

                ack.put(
                        "type",
                        "AUTH_OK"
                );

                sendJson(
                        session,
                        ack
                );
            }


            // ----------------------------------------------------
            // PING
            // ----------------------------------------------------

            case "PING" -> {

                ObjectNode pong =
                        mapper.createObjectNode();

                pong.put(
                        "type",
                        "PONG"
                );

                sendJson(
                        session,
                        pong
                );
            }


            // ----------------------------------------------------
            // PONG
            // ----------------------------------------------------

            case "PONG" -> {
                // Connection is alive.
            }


            // ----------------------------------------------------
            // PROXY RESPONSE
            // ----------------------------------------------------

            case "PROXY_RESPONSE" -> {

                String requestId =
                        node.path("requestId")
                                .asText("");

                if (requestId.isBlank()) {
                    return;
                }

                pendingRequestRegistry.complete(
                        requestId,
                        node
                );
            }


            // ----------------------------------------------------
            // UNKNOWN
            // ----------------------------------------------------

            default -> {
                // Ignore unknown message.
            }
        }
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
                (String) session
                        .getAttributes()
                        .get("agentId");

        if (agentId != null) {

            /*
             * VERY IMPORTANT:
             * pass the actual closing session.
             */
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

        if (session.isOpen()) {
            session.close();
        }
    }


    // ============================================================
    // SEND JSON
    // ============================================================

    private void sendJson(
            WebSocketSession session,
            ObjectNode node
    ) {

        try {

            session.sendMessage(
                    new TextMessage(
                            mapper.writeValueAsString(node)
                    )
            );

        } catch (Exception e) {

            // Connection cleanup will happen automatically.
        }
    }
}