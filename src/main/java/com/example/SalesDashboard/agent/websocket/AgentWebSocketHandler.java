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

        Integer chunkIndex = node.has("chunkIndex") ? node.path("chunkIndex").asInt() : null;
        Long bytes = node.has("bytes") ? node.path("bytes").asLong() : null;
        Long cumulativeBytes = node.has("cumulativeBytes") ? node.path("cumulativeBytes").asLong() : null;

        pendingRequestRegistry.appendChunk(
                requestId,
                chunk,
                chunkIndex,
                bytes,
                cumulativeBytes
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

        Integer totalChunks = node.has("totalChunks") ? node.path("totalChunks").asInt() : null;
        Long totalBytes = node.has("totalBytes") ? node.path("totalBytes").asLong() : null;

        log.info(
                "TALLY STREAM END | requestId={} | totalChunks={} | totalBytes={}",
                requestId,
                totalChunks,
                totalBytes
        );

        pendingRequestRegistry.completeStream(
                requestId,
                totalChunks,
                totalBytes
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
             * Fail any pending requests for this agent immediately instead of hanging until timeout.
             */
            pendingRequestRegistry.failAgentRequests(
                    agentId,
                    "WebSocket closed with status " + status
            );

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

        if (
                agentId != null
                        && !agentId.isBlank()
        ) {
            pendingRequestRegistry.failAgentRequests(
                    agentId,
                    "WebSocket transport error: " + exception.getMessage()
            );
        }

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