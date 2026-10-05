package com.example.SalesDashboard.agent.websocket;

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
                (String)
                        session
                                .getAttributes()
                                .get("agentId");

        if (
                agentId == null
                        || agentId.isBlank()
        ) {

            log.warn(
                    "Agent WebSocket connected without agentId"
            );

            session.close(
                    CloseStatus.POLICY_VIOLATION
            );

            return;
        }

        log.info(
                "TALLY AGENT CONNECTED | agentId={}",
                agentId
        );

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

        if (
                payload == null
                        || payload.isBlank()
        ) {

            return;
        }

        JsonNode node;

        try {

            node =
                    mapper.readTree(
                            payload
                    );

        } catch (Exception e) {

            log.warn(
                    "Invalid JSON received from Tally Agent"
            );

            return;
        }

        String agentId =
                (String)
                        session
                                .getAttributes()
                                .get("agentId");

        /*
         * Every valid message proves that the Agent is alive.
         */
        if (
                agentId != null
                        && !agentId.isBlank()
        ) {

            agentConnectionService.touchAgent(
                    agentId
            );
        }

        String type =
                node.path("type")
                        .asText("");

        if (type.isBlank()) {

            log.warn(
                    "Received WebSocket message without type | agentId={}",
                    agentId
            );

            return;
        }

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

                log.info(
                        "TALLY AGENT AUTH OK | agentId={}",
                        agentId
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
                 * Agent heartbeat response.
                 *
                 * touchAgent() was already called above.
                 */
            }

            // ====================================================
            // NON-STREAMING PROXY RESPONSE
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
            // UNKNOWN
            // ====================================================

            default -> {

                log.debug(
                        "Ignoring unknown Agent WebSocket message | agentId={} | type={}",
                        agentId,
                        type
                );
            }
        }
    }

    // ============================================================
    // NORMAL PROXY RESPONSE
    // ============================================================

    private void handleProxyResponse(
            JsonNode node
    ) {

        String requestId =
                node.path("requestId")
                        .asText("");

        if (
                requestId == null
                        || requestId.isBlank()
        ) {

            log.warn(
                    "PROXY_RESPONSE received without requestId"
            );

            return;
        }

        log.debug(
                "TALLY PROXY RESPONSE COMPLETE | requestId={}",
                requestId
        );

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

        if (
                requestId == null
                        || requestId.isBlank()
        ) {

            log.warn(
                    "PROXY_RESPONSE_START received without requestId"
            );

            return;
        }

        int status =
                node.path("status")
                        .asInt(200);

        log.info(
                "TALLY STREAM START | requestId={} | status={}",
                requestId,
                status
        );

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

        if (
                requestId == null
                        || requestId.isBlank()
        ) {

            return;
        }

        String chunk =
                node.path("chunk")
                        .asText("");

        if (
                chunk == null
                        || chunk.isEmpty()
        ) {

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

        if (
                requestId == null
                        || requestId.isBlank()
        ) {

            log.warn(
                    "PROXY_RESPONSE_END received without requestId"
            );

            return;
        }

        log.info(
                "TALLY STREAM END | requestId={}",
                requestId
        );

        /*
         * This converts all received chunks into the final object:
         *
         * {
         *     ok: true,
         *     status: 200,
         *     body: "complete Tally JSON..."
         * }
         *
         * AgentRelayService can then process it normally.
         */
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
                (String)
                        session
                                .getAttributes()
                                .get("agentId");

        log.info(
                "TALLY AGENT DISCONNECTED | agentId={} | status={}",
                agentId,
                status
        );

        if (
                agentId != null
                        && !agentId.isBlank()
        ) {

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

        String agentId =
                (String)
                        session
                                .getAttributes()
                                .get("agentId");

        log.warn(
                "TALLY AGENT WEBSOCKET TRANSPORT ERROR | agentId={} | error={}",
                agentId,
                exception.getMessage()
        );

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

        if (
                session == null
                        || !session.isOpen()
        ) {

            return;
        }

        try {

            session.sendMessage(
                    new TextMessage(
                            mapper.writeValueAsString(
                                    node
                            )
                    )
            );

        } catch (Exception e) {

            log.warn(
                    "Failed to send WebSocket message to Tally Agent: {}",
                    e.getMessage()
            );
        }
    }
}