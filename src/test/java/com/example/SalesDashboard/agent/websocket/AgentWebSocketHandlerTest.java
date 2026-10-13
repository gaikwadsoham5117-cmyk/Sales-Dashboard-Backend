package com.example.SalesDashboard.agent.websocket;

import com.example.SalesDashboard.agent.service.AgentConnectionService;
import com.example.SalesDashboard.agent.service.PendingRequestRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentWebSocketHandlerTest {

    @Mock
    private AgentConnectionService connectionService;

    @Mock
    private PendingRequestRegistry pendingRequestRegistry;

    @Mock
    private WebSocketSession session;

    private AgentWebSocketHandler handler;
    private final Map<String, Object> attributes = new HashMap<>();

    @BeforeEach
    void setUp() {
        handler = new AgentWebSocketHandler(connectionService, pendingRequestRegistry);
        attributes.put("agentId", "agent-abc");
        lenient().when(session.getAttributes()).thenReturn(attributes);
        lenient().when(session.isOpen()).thenReturn(true);
    }

    @Test
    void testChunkMessageDoesNotTouchDatabase() throws Exception {
        String chunkMessage = """
                {
                    "type": "PROXY_RESPONSE_CHUNK",
                    "requestId": "req-999",
                    "chunk": "eyJkYXRhIjp7fX0=",
                    "encoding": "gzip",
                    "compressedBytes": 15
                }
                """;

        handler.handleTextMessage(session, new TextMessage(chunkMessage));

        // Must append chunk
        verify(pendingRequestRegistry, times(1)).appendChunk(eq("req-999"), eq("eyJkYXRhIjp7fX0="), eq("gzip"), eq(15L));

        // CRITICAL CHECK: touchAgent MUST NOT be called for chunks!
        verify(connectionService, never()).touchAgent(any());
    }

    @Test
    void testStartMessageCallsTouchAgentAndStartsStream() throws Exception {
        String startMessage = """
                {
                    "type": "PROXY_RESPONSE_START",
                    "requestId": "req-999",
                    "status": 200,
                    "encoding": "gzip"
                }
                """;

        handler.handleTextMessage(session, new TextMessage(startMessage));

        verify(connectionService, times(1)).touchAgent("agent-abc");
        verify(pendingRequestRegistry, times(1)).startStream("req-999", 200, "gzip");
    }

    @Test
    void testAfterConnectionClosedFailsInFlightRequests() throws Exception {
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(pendingRequestRegistry, times(1)).failRequestsForAgent(eq("agent-abc"), any());
        verify(connectionService, times(1)).disconnectAgent("agent-abc", session);
    }

    @Test
    void testHandleTransportErrorFailsInFlightRequests() throws Exception {
        handler.handleTransportError(session, new RuntimeException("Network error"));

        verify(pendingRequestRegistry, times(1)).failRequestsForAgent(eq("agent-abc"), any());
        verify(session, times(1)).close();
    }
}
