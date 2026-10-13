package com.example.SalesDashboard.agent.service;

import com.example.SalesDashboard.agent.exception.AgentConnectionTimeoutException;
import com.example.SalesDashboard.agent.exception.AgentDisconnectedException;
import com.example.SalesDashboard.agent.exception.OperationDeadlineException;
import com.example.SalesDashboard.agent.exception.ResponseSizeLimitExceededException;
import com.example.SalesDashboard.agent.exception.StreamIdleTimeoutException;
import com.example.SalesDashboard.agent.model.AgentProxyResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

public class PendingRequestRegistryTest {

    private PendingRequestRegistry registry;

    @BeforeEach
    void setUp() {
        // connectTimeout = 2s, streamIdleTimeout = 1s, deadline = 4s, maxSize = 100 KB
        registry = new PendingRequestRegistry(2, 1, 4, 100 * 1024);
    }

    @AfterEach
    void tearDown() {
        registry.shutdown();
    }

    @Test
    void testNormalSmallStream() throws Exception {
        String reqId = "req-small-1";
        CompletableFuture<AgentProxyResponse> future = registry.register(reqId, "agent-1");

        registry.startStream(reqId, 200);
        registry.appendChunk(reqId, "{\"status\":", 1, 10L, 10L);
        registry.appendChunk(reqId, "\"1\",\"data\":{\"collection\":[]}}", 2, 29L, 39L);
        registry.completeStream(reqId, 2, 39L);

        AgentProxyResponse resp = future.get();
        assertTrue(resp.ok());
        assertEquals(200, resp.status());
        assertEquals("{\"status\":\"1\",\"data\":{\"collection\":[]}}", resp.body());
    }

    @Test
    void testLargeSimulatedResponseAcrossChunks() throws Exception {
        String reqId = "req-large-1";
        CompletableFuture<AgentProxyResponse> future = registry.register(reqId, "agent-1");

        registry.startStream(reqId, 200);

        // Build a large chunk (~10 KB) repeated 5 times = ~50 KB (within 100 KB limit)
        String chunkPiece = "X".repeat(10 * 1024);
        for (int i = 1; i <= 5; i++) {
            registry.appendChunk(reqId, chunkPiece, i, 10240L, (long) i * 10240L);
        }
        registry.completeStream(reqId, 5, 51200L);

        AgentProxyResponse resp = future.get();
        assertTrue(resp.ok());
        assertEquals(50 * 1024, resp.body().length());
    }

    @Test
    void testResponseSizeLimitExceeded() {
        String reqId = "req-size-limit";
        CompletableFuture<AgentProxyResponse> future = registry.register(reqId, "agent-1");

        registry.startStream(reqId, 200);
        // Exceed 100 KB limit with 120 KB chunk
        String hugeChunk = "Y".repeat(120 * 1024);
        registry.appendChunk(reqId, hugeChunk, 1, 120 * 1024L, 120 * 1024L);

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(ResponseSizeLimitExceededException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("size limit exceeded"));
    }

    @Test
    void testInitialConnectTimeout() {
        String reqId = "req-timeout-connect";
        // Connect timeout is 2 seconds, no startStream sent
        CompletableFuture<AgentProxyResponse> future = registry.register(reqId, "agent-1");

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(AgentConnectionTimeoutException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("did not begin response"));
    }

    @Test
    void testStreamIdleTimeoutWhenStreamStalls() throws Exception {
        String reqId = "req-timeout-idle";
        CompletableFuture<AgentProxyResponse> future = registry.register(reqId, "agent-1");

        // Start stream immediately so connect timeout is superseded by idle timeout
        registry.startStream(reqId, 200);
        registry.appendChunk(reqId, "chunk1", 1, 6L, 6L);

        // Idle timeout is 1s, don't send any more chunks
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(StreamIdleTimeoutException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("stream stalled"));
    }

    @Test
    void testAgentDisconnectFailsInFlightRequestImmediately() {
        String reqId = "req-disconnect";
        CompletableFuture<AgentProxyResponse> future = registry.register(reqId, "agent-1");

        registry.startStream(reqId, 200);
        registry.failAgentRequests("agent-1", "WebSocket connection drop");

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(AgentDisconnectedException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("disconnected"));
    }

    @Test
    void testLargeVoucherJsonStreamParsing() throws Exception {
        // Test parsing 3,500 vouchers through Jackson streaming parser
        StringBuilder sb = new StringBuilder();
        sb.append("{\"status\":\"1\",\"data\":{\"collection\":[");
        for (int i = 1; i <= 3500; i++) {
            if (i > 1) sb.append(",");
            sb.append("{\"date\":\"20261101\",\"vouchernumber\":\"VCH-")
              .append(i)
              .append("\",\"vouchertypename\":\"Sales\",\"partyledgername\":\"Customer ")
              .append(i)
              .append("\",\"amount\":1000.00,\"guid\":\"GUID-")
              .append(i)
              .append("\",\"masterid\":")
              .append(i)
              .append("}");
        }
        sb.append("]}}");

        String simulatedJson = sb.toString();
        // Verify size of 3,500 vouchers is ~500 KB
        assertTrue(simulatedJson.length() > 300_000);

        ObjectMapper om = new ObjectMapper();
        int count = 0;
        try (var parser = om.createParser(simulatedJson)) {
            while (parser.nextToken() != null) {
                if ("collection".equals(parser.currentName()) && parser.currentToken() == com.fasterxml.jackson.core.JsonToken.START_ARRAY) {
                    while (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.END_ARRAY) {
                        var node = parser.readValueAsTree();
                        assertNotNull(node);
                        count++;
                    }
                    break;
                }
            }
        }
        assertEquals(3500, count);
    }
}

