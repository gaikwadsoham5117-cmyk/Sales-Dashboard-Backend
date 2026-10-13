package com.example.SalesDashboard.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class PendingRequestRegistry {

    /*
     * ============================================================
     * RESPONSE TIMEOUT
     * ============================================================
     *
     * This timeout starts when the request actually reaches the
     * front of the Agent queue and is sent to the Agent.
     *
     * It does NOT include time spent waiting inside the queue.
     */
    private static final Duration DEFAULT_TIMEOUT =
            Duration.ofSeconds(240);

    /*
     * ============================================================
     * PENDING REQUESTS
     * ============================================================
     */
    private final Map<String, PendingResponse> pending =
            new ConcurrentHashMap<>();

    /*
     * ============================================================
     * OBJECT MAPPER
     * ============================================================
     */
    private final ObjectMapper mapper =
            new ObjectMapper();

    // ============================================================
    // REGISTER
    // ============================================================

    /**
     * Register a request and return a Future that will be completed
     * when the complete Tally response has been received.
     */
    public CompletableFuture<JsonNode> register(
            String requestId
    ) {

        if (
                requestId == null
                        || requestId.isBlank()
        ) {

            CompletableFuture<JsonNode> failed =
                    new CompletableFuture<>();

            failed.completeExceptionally(
                    new IllegalArgumentException(
                            "Request ID is required"
                    )
            );

            return failed;
        }

        PendingResponse pendingResponse =
                new PendingResponse();

        pending.put(
                requestId,
                pendingResponse
        );

        CompletableFuture<JsonNode> future =
                pendingResponse.future;

        /*
         * Remove from registry whenever the request finishes,
         * successfully or exceptionally.
         */
        future.whenComplete(
                (result, error) ->
                        pending.remove(
                                requestId,
                                pendingResponse
                        )
        );

        /*
         * Timeout applies to this pending request.
         */
        return future.orTimeout(
                DEFAULT_TIMEOUT.toSeconds(),
                TimeUnit.SECONDS
        );
    }

    // ============================================================
    // NORMAL COMPLETE
    // ============================================================

    /**
     * Completes a request that returned one complete
     * PROXY_RESPONSE message.
     *
     * Kept for compatibility with non-streaming responses.
     */
    public void complete(
            String requestId,
            JsonNode response
    ) {

        if (
                requestId == null
                        || requestId.isBlank()
        ) {

            return;
        }

        PendingResponse pendingResponse =
                pending.get(
                        requestId
                );

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
     * Called when Agent sends:
     *
     * PROXY_RESPONSE_START
     *
     * Example:
     *
     * {
     *   "type": "PROXY_RESPONSE_START",
     *   "requestId": "...",
     *   "status": 200
     * }
     */
    public void startStream(
            String requestId,
            int status
    ) {

        if (
                requestId == null
                        || requestId.isBlank()
        ) {

            return;
        }

        PendingResponse pendingResponse =
                pending.get(
                        requestId
                );

        if (pendingResponse == null) {

            return;
        }

        pendingResponse.status =
                status;

        pendingResponse.streaming =
                true;
    }

    // ============================================================
    // STREAM CHUNK
    // ============================================================

    /**
     * Called for every:
     *
     * PROXY_RESPONSE_CHUNK
     */
    public void appendChunk(
            String requestId,
            String chunk
    ) {

        if (
                requestId == null
                        || requestId.isBlank()
        ) {

            return;
        }

        if (
                chunk == null
                        || chunk.isEmpty()
        ) {

            return;
        }

        PendingResponse pendingResponse =
                pending.get(
                        requestId
                );

        if (pendingResponse == null) {

            return;
        }

        /*
         * StringBuilder is only accessed by one request at a time,
         * but synchronize here for safety.
         */
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
     * Called when Agent sends:
     *
     * PROXY_RESPONSE_END
     *
     * This creates the final response structure expected by
     * AgentRelayService:
     *
     * {
     *   "type": "PROXY_RESPONSE",
     *   "requestId": "...",
     *   "ok": true,
     *   "status": 200,
     *   "body": "..."
     * }
     */
    public void completeStream(
            String requestId
    ) {

        if (
                requestId == null
                        || requestId.isBlank()
        ) {

            return;
        }

        PendingResponse pendingResponse =
                pending.get(
                        requestId
                );

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

        /*
         * VERY IMPORTANT:
         *
         * This MUST be true.
         *
         * AgentRelayService checks:
         *
         * response.path("ok").asBoolean(false)
         */
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

    public void fail(
            String requestId,
            Throwable error
    ) {

        if (
                requestId == null
                        || requestId.isBlank()
        ) {

            return;
        }

        PendingResponse pendingResponse =
                pending.get(
                        requestId
                );

        if (pendingResponse != null) {

            pendingResponse.future.completeExceptionally(
                    error
            );
        }
    }

    // ============================================================
    // INTERNAL PENDING RESPONSE
    // ============================================================

    private static final class PendingResponse {

        private final CompletableFuture<JsonNode> future =
                new CompletableFuture<>();

        private final StringBuilder body =
                new StringBuilder();

        private volatile int status =
                200;

        private volatile boolean streaming =
                false;
    }
}