package com.example.SalesDashboard.agent.service;

import com.example.SalesDashboard.agent.exception.AgentDeadlineExceededException;
import com.example.SalesDashboard.agent.exception.AgentDisconnectedException;
import com.example.SalesDashboard.agent.exception.AgentStartTimeoutException;
import com.example.SalesDashboard.agent.exception.ResponseSizeLimitExceededException;
import com.example.SalesDashboard.agent.exception.StreamIdleTimeoutException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PendingRequestRegistryTest {

    private PendingRequestRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // maxResponse=1MB, maxCompressed=512KB, permits=5, start=2s, idle=1s, deadline=5s
        registry = new PendingRequestRegistry(1024 * 1024L, 512 * 1024L, 5, 2L, 1L, 5L);
    }

    @AfterEach
    void tearDown() {
        registry.shutdown();
    }

    @Test
    void testNonStreamingProxyResponse() throws Exception {
        CompletableFuture<AgentRelayService.RelayResponse> future =
                registry.register("req-1", "agent-1");

        ObjectNode responseJson = mapper.createObjectNode();
        responseJson.put("type", "PROXY_RESPONSE");
        responseJson.put("requestId", "req-1");
        responseJson.put("status", 200);
        responseJson.put("body", "{\"message\":\"hello\"}");

        registry.complete("req-1", responseJson);

        AgentRelayService.RelayResponse response = future.get(2, TimeUnit.SECONDS);
        assertNotNull(response);
        assertEquals(200, response.status());
        assertEquals("{\"message\":\"hello\"}", response.body());
        assertFalse(response.isFileBacked());
    }

    @Test
    void testNormalStreamedResponse() throws Exception {
        CompletableFuture<AgentRelayService.RelayResponse> future =
                registry.register("req-2", "agent-1");

        registry.startStream("req-2", 200, "identity");
        registry.appendChunk("req-2", "{\"status\":\"1\",");
        registry.appendChunk("req-2", "\"data\":{\"collection\":[]}}");
        registry.completeStream("req-2");

        AgentRelayService.RelayResponse response = future.get(2, TimeUnit.SECONDS);
        assertNotNull(response);
        assertEquals(200, response.status());
        assertTrue(response.isFileBacked());
        assertEquals("{\"status\":\"1\",\"data\":{\"collection\":[]}}", response.body());

        Path tempFile = response.tempFile();
        assertTrue(Files.exists(tempFile));

        response.close();
        assertFalse(Files.exists(tempFile), "Temp file should be deleted on close");
    }

    @Test
    void testUnicodePreservationAcrossChunks() throws Exception {
        CompletableFuture<AgentRelayService.RelayResponse> future =
                registry.register("req-unicode", "agent-1");

        String hindiText = "नमस्ते भारत - Tally Prime Enterprise 2026 ₹10,000";

        registry.startStream("req-unicode", 200, "identity");
        // Split text in half to ensure multi-byte UTF-8 characters are handled cleanly
        int mid = hindiText.length() / 2;
        registry.appendChunk("req-unicode", hindiText.substring(0, mid));
        registry.appendChunk("req-unicode", hindiText.substring(mid));
        registry.completeStream("req-unicode");

        try (AgentRelayService.RelayResponse response = future.get(2, TimeUnit.SECONDS)) {
            assertEquals(hindiText, response.body());
        }
    }

    @Test
    void testGzipCompressedStream() throws Exception {
        CompletableFuture<AgentRelayService.RelayResponse> future =
                registry.register("req-gzip", "agent-1");

        String jsonPayload = "{\"status\":\"1\",\"data\":{\"collection\":[{\"voucherNumber\":\"VCH-001\"}]}}";

        // Compress using GZIPOutputStream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
        }
        byte[] compressedBytes = baos.toByteArray();
        String base64 = Base64.getEncoder().encodeToString(compressedBytes);

        registry.startStream("req-gzip", 200, "gzip");
        registry.appendChunk("req-gzip", base64, "gzip", (long) compressedBytes.length);
        registry.completeStream("req-gzip");

        try (AgentRelayService.RelayResponse response = future.get(2, TimeUnit.SECONDS)) {
            assertEquals(200, response.status());
            assertEquals("gzip", response.encoding());
            try (InputStream stream = response.openStream()) {
                String decompressed = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                assertEquals(jsonPayload, decompressed);
            }
        }
    }

    @Test
    void testStreamIdleTimeout() {
        CompletableFuture<AgentRelayService.RelayResponse> future =
                registry.register("req-idle", "agent-1");

        registry.startStream("req-idle", 200, "identity");
        registry.appendChunk("req-idle", "chunk-1");
        // Don't complete, wait for idle timeout (configured to 1 second)

        ExecutionException exception = assertThrows(ExecutionException.class, () ->
                future.get(3, TimeUnit.SECONDS)
        );
        assertTrue(exception.getCause() instanceof StreamIdleTimeoutException);
    }

    @Test
    void testStartTimeout() {
        CompletableFuture<AgentRelayService.RelayResponse> future =
                registry.register("req-start-timeout", "agent-1");
        // No startStream sent, wait for start timeout (configured to 2 seconds)

        ExecutionException exception = assertThrows(ExecutionException.class, () ->
                future.get(4, TimeUnit.SECONDS)
        );
        assertTrue(exception.getCause() instanceof AgentStartTimeoutException);
    }

    @Test
    void testAgentDisconnectFailsInFlight() {
        CompletableFuture<AgentRelayService.RelayResponse> future =
                registry.register("req-disc", "agent-1");

        registry.startStream("req-disc", 200, "identity");
        registry.failRequestsForAgent("agent-1", new AgentDisconnectedException("Agent went offline"));

        ExecutionException exception = assertThrows(ExecutionException.class, () ->
                future.get(1, TimeUnit.SECONDS)
        );
        assertTrue(exception.getCause() instanceof AgentDisconnectedException);
    }

    @Test
    void testResponseSizeLimitExceeded() {
        // Registry configured with maxResp = 1MB
        CompletableFuture<AgentRelayService.RelayResponse> future =
                registry.register("req-large", "agent-1");

        registry.startStream("req-large", 200, "identity");
        // Create chunk larger than 1MB
        String largeChunk = "A".repeat(1024 * 1024 + 10);
        registry.appendChunk("req-large", largeChunk);

        ExecutionException exception = assertThrows(ExecutionException.class, () ->
                future.get(1, TimeUnit.SECONDS)
        );
        assertTrue(exception.getCause() instanceof ResponseSizeLimitExceededException);
    }
}
