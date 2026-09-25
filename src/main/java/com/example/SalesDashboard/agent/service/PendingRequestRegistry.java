package com.example.SalesDashboard.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Correlates outgoing PROXY_REQUEST messages with responses coming
 * back from the Tally Agent.
 *
 * Supports both:
 *
 * 1. Legacy single-message response:
 *      PROXY_RESPONSE
 *
 * 2. New streamed response:
 *      PROXY_RESPONSE_START
 *      PROXY_RESPONSE_CHUNK
 *      PROXY_RESPONSE_END
 *
 * The streamed protocol prevents one very large Tally response from
 * being sent as a single WebSocket frame.
 */
@Component
public class PendingRequestRegistry {

    private static final Duration DEFAULT_TIMEOUT =
            Duration.ofSeconds(20);

    private final Map<String, PendingResponse> pending =
            new ConcurrentHashMap<>();

    private final ObjectMapper mapper =
            new ObjectMapper();


    // ============================================================
    // REGISTER
    // ============================================================

    /**
     * Registers a request before PROXY_REQUEST is sent to the agent.
     */
    public CompletableFuture<JsonNode> register(
            String requestId
    ) {

        PendingResponse pendingResponse =
                new PendingResponse();

        pending.put(
                requestId,
                pendingResponse
        );

        CompletableFuture<JsonNode> future =
                pendingResponse.future;

        /*
         * Always remove the request when it completes,
         * fails or times out.
         */
        future.whenComplete(
                (result, error) ->
                        pending.remove(
                                requestId,
                                pendingResponse
                        )
        );

        return future.orTimeout(
                DEFAULT_TIMEOUT.toSeconds(),
                TimeUnit.SECONDS
        );
    }


    // ============================================================
    // LEGACY COMPLETE
    // ============================================================

    /**
     * Handles the old single PROXY_RESPONSE format.
     *
     * This is kept for backward compatibility and for agent errors.
     */
    public void complete(
            String requestId,
            JsonNode response
    ) {

        PendingResponse pendingResponse =
                pending.get(requestId);

        if (pendingResponse != null) {

            pendingResponse.future.complete(
                    response
            );
        }
    }


    // ============================================================
    // STREAM START
    // ============================================================

    /**
     * Starts a streamed response.
     *
     * Example:
     *
     * {
     *   "type": "PROXY_RESPONSE_START",
     *   "requestId": "...",
     *   "ok": true,
     *   "status": 200
     * }
     */
    public void startStream(
            String requestId,
            int status
    ) {

        PendingResponse pendingResponse =
                pending.get(requestId);

        if (pendingResponse == null) {
            return;
        }

        pendingResponse.status = status;
        pendingResponse.streaming = true;
    }


    // ============================================================
    // STREAM CHUNK
    // ============================================================

    /**
     * Appends one response chunk.
     *
     * IMPORTANT:
     * We append chunks directly into one StringBuilder.
     *
     * We do NOT create a new String containing the complete
     * response for every chunk.
     */
    public void appendChunk(
            String requestId,
            String chunk
    ) {

        if (chunk == null || chunk.isEmpty()) {
            return;
        }

        PendingResponse pendingResponse =
                pending.get(requestId);

        if (pendingResponse == null) {
            return;
        }

        synchronized (pendingResponse) {

            pendingResponse.body.append(
                    chunk
            );
        }
    }


    // ============================================================
    // STREAM END
    // ============================================================

    /**
     * Completes a streamed response.
     *
     * At this point the backend converts the accumulated response
     * into the same JsonNode structure that the old PROXY_RESPONSE
     * used.
     */
    public void completeStream(
            String requestId
    ) {

        PendingResponse pendingResponse =
                pending.get(requestId);

        if (pendingResponse == null) {
            return;
        }

        String responseBody;

        synchronized (pendingResponse) {

            responseBody =
                    pendingResponse.body.toString();
        }

        ObjectNode response =
                mapper.createObjectNode();

        response.put(
                "type",
                "PROXY_RESPONSE"
        );

        response.put(
                "requestId",
                requestId
        );

        response.put(
                "ok",
                true
        );

        response.put(
                "status",
                pendingResponse.status
        );

        response.put(
                "body",
                responseBody
        );

        pendingResponse.future.complete(
                response
        );
    }


    // ============================================================
    // FAIL
    // ============================================================

    /**
     * Fails a pending request immediately.
     */
    public void fail(
            String requestId,
            Throwable error
    ) {

        PendingResponse pendingResponse =
                pending.get(requestId);

        if (pendingResponse != null) {

            pendingResponse.future
                    .completeExceptionally(
                            error
                    );
        }
    }


    // ============================================================
    // INTERNAL STATE
    // ============================================================

    private static final class PendingResponse {

        private final CompletableFuture<JsonNode> future =
                new CompletableFuture<>();

        private final StringBuilder body =
                new StringBuilder();

        private volatile int status = 200;

        private volatile boolean streaming = false;
    }
}