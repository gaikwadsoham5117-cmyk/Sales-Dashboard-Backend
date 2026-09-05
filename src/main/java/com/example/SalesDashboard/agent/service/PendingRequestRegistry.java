package com.example.SalesDashboard.agent.service;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Correlates an outgoing PROXY_REQUEST (sent to a specific agent over its
 * WebSocket session) with the PROXY_RESPONSE that eventually comes back on
 * that same session, asynchronously, on a different thread.
 *
 * Flow:
 *  1. AgentRelayService calls register(requestId) before sending the
 *     PROXY_REQUEST, and gets back a CompletableFuture.
 *  2. AgentRelayService blocks on future.get(timeout) waiting for a reply.
 *  3. When the agent's PROXY_RESPONSE arrives, AgentWebSocketHandler calls
 *     complete(requestId, payload), which resolves the future and unblocks
 *     step 2, wherever that thread was left waiting.
 */
@Component
public class PendingRequestRegistry {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    private final Map<String, CompletableFuture<JsonNode>> pending =
            new ConcurrentHashMap<>();

    /**
     * Registers a new pending request and returns a future that will
     * complete when the matching PROXY_RESPONSE arrives, or time out on
     * its own after DEFAULT_TIMEOUT if nothing ever comes back (e.g. the
     * agent died mid-request without closing the socket).
     */
    public CompletableFuture<JsonNode> register(String requestId) {

        CompletableFuture<JsonNode> future = new CompletableFuture<>();

        pending.put(requestId, future);

        // Always remove the entry once it settles, however it settles,
        // so the map never accumulates stale requests.
        future.whenComplete((result, error) -> pending.remove(requestId));

        return future.orTimeout(
                DEFAULT_TIMEOUT.toSeconds(),
                TimeUnit.SECONDS
        );
    }

    /**
     * Called from AgentWebSocketHandler when a PROXY_RESPONSE arrives.
     * No-op if the requestId is unknown (e.g. it already timed out).
     */
    public void complete(String requestId, JsonNode response) {

        CompletableFuture<JsonNode> future = pending.get(requestId);

        if (future != null) {
            future.complete(response);
        }
    }

    /**
     * Called if a request should fail immediately instead of waiting
     * (e.g. the agent disconnected while we were waiting on it).
     */
    public void fail(String requestId, Throwable error) {

        CompletableFuture<JsonNode> future = pending.get(requestId);

        if (future != null) {
            future.completeExceptionally(error);
        }
    }
}
