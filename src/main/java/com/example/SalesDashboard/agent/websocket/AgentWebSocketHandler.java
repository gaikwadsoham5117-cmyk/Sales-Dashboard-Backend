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

        if (agentId == null
                || agentId.isBlank()) {

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

        if (payload == null
                || payload.isBlank()) {

            return;
        }

        JsonNode node;

        try {

            node =
                    mapper.readTree(
                            payload
                    );

        } catch (Exception e) {

            /*
             * Do not allow malformed JSON to break
             * the WebSocket connection.
             */
            return;
        }

        String agentId =
                (String) session
                        .getAttributes()
                        .get("agentId");

        /*
         * Every valid message proves that the
         * agent connection is alive.
         */
        if (agentId != null
                && !agentId.isBlank()) {

            agentConnectionService.touchAgent(
                    agentId
            );
        }

        String type =
                node.path("type")
                        .asText("");


        switch (type) {


            // ====================================================
            // AUTH
            // ====================================================

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


            // ====================================================
            // PING
            // ====================================================

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


            // ====================================================
            // PONG
            // ====================================================

            case "PONG" -> {

                /*
                 * Connection is alive.
                 */
            }


            // ====================================================
            // LEGACY PROXY RESPONSE
            // ====================================================

            case "PROXY_RESPONSE" -> {

                handleProxyResponse(
                        node
                );
            }


            // ====================================================
            // STREAM START
            // ====================================================

            case "PROXY_RESPONSE_START" -> {

                handleProxyResponseStart(
                        node
                );
            }


            // ====================================================
            // STREAM CHUNK
            // ====================================================

            case "PROXY_RESPONSE_CHUNK" -> {

                handleProxyResponseChunk(
                        node
                );
            }


            // ====================================================
            // STREAM END
            // ====================================================

            case "PROXY_RESPONSE_END" -> {

                handleProxyResponseEnd(
                        node
                );
            }


            // ====================================================
            // UNKNOWN MESSAGE
            // ====================================================

            default -> {

                /*
                 * Ignore unknown message types.
                 */
            }
        }
    }


    // ============================================================
    // LEGACY PROXY RESPONSE
    // ============================================================

    private void handleProxyResponse(
            JsonNode node
    ) {

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


    // ============================================================
    // STREAM START
    // ============================================================

    private void handleProxyResponseStart(
            JsonNode node
    ) {

        String requestId =
                node.path("requestId")
                        .asText("");

        if (requestId.isBlank()) {
            return;
        }

        boolean ok =
                node.path("ok")
                        .asBoolean(false);

        /*
         * If the Agent says the request failed,
         * don't start a successful stream.
         */
        if (!ok) {

            pendingRequestRegistry.complete(
                    requestId,
                    node
            );

            return;
        }

        int status =
                node.path("status")
                        .asInt(200);

        pendingRequestRegistry.startStream(
                requestId,
                status
        );
    }


    // ============================================================
    // STREAM CHUNK
    // ============================================================

    private void handleProxyResponseChunk(
            JsonNode node
    ) {

        String requestId =
                node.path("requestId")
                        .asText("");

        if (requestId.isBlank()) {
            return;
        }

        String chunk =
                node.path("body")
                        .asText("");

        if (chunk.isEmpty()) {
            return;
        }

        pendingRequestRegistry.appendChunk(
                requestId,
                chunk
        );
    }


    // ============================================================
    // STREAM END
    // ============================================================

    private void handleProxyResponseEnd(
            JsonNode node
    ) {

        String requestId =
                node.path("requestId")
                        .asText("");

        if (requestId.isBlank()) {
            return;
        }

        pendingRequestRegistry.completeStream(
                requestId
        );
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

        if (agentId != null
                && !agentId.isBlank()) {

            /*
             * Pass the actual closing session.
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

            try {

                session.close(
                        CloseStatus.SERVER_ERROR
                );

            } catch (Exception ignored) {
                // Cleanup handled by WebSocket lifecycle.
            }
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
                            mapper.writeValueAsString(
                                    node
                            )
                    )
            );

        } catch (Exception e) {

            /*
             * Connection cleanup will happen
             * through the WebSocket lifecycle.
             */
        }
    }
}