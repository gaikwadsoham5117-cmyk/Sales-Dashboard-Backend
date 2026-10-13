package com.example.SalesDashboard.agent.service;

import com.example.SalesDashboard.agent.entity.Agent;
import com.example.SalesDashboard.agent.repository.AgentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentConnectionServiceTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private WebSocketSession rawSession;

    private AgentConnectionService connectionService;

    @BeforeEach
    void setUp() {
        connectionService = new AgentConnectionService(agentRepository, 5000, 1024 * 1024);
        lenient().when(rawSession.getId()).thenReturn("sess-1");
        lenient().when(rawSession.isOpen()).thenReturn(true);
    }

    @Test
    void testRegisterAgentWrapsSessionAndMarksOnline() {
        Agent agent = Agent.builder()
                .agentId("agent-100")
                .status("OFFLINE")
                .build();
        when(agentRepository.findByAgentId("agent-100")).thenReturn(Optional.of(agent));
        when(agentRepository.save(any(Agent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        connectionService.connectAgent("agent-100", rawSession);

        assertTrue(connectionService.isAgentConnected("agent-100"));
        WebSocketSession session = connectionService.getAgentSession("agent-100");
        assertNotNull(session);
        assertTrue(session instanceof ConcurrentWebSocketSessionDecorator);

        assertEquals("ONLINE", agent.getStatus());
        verify(agentRepository, times(1)).save(agent);
    }

    @Test
    void testTouchAgentIsThrottledToOncePerSixtySeconds() {
        Agent agent = Agent.builder()
                .agentId("agent-throttle")
                .status("ONLINE")
                .build();
        when(agentRepository.findByAgentId("agent-throttle")).thenReturn(Optional.of(agent));
        when(agentRepository.save(any(Agent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Connect agent so session is active
        connectionService.connectAgent("agent-throttle", rawSession);
        verify(agentRepository, times(1)).save(agent);

        // Immediate subsequent touches -> should be throttled, 0 extra DB calls
        for (int i = 0; i < 10; i++) {
            connectionService.touchAgent("agent-throttle");
        }
        verify(agentRepository, times(1)).save(agent);
    }

    @Test
    void testStaleSessionCannotEvictNewerSession() {
        WebSocketSession newSession = mock(WebSocketSession.class);
        when(newSession.getId()).thenReturn("sess-new");
        when(newSession.isOpen()).thenReturn(true);

        Agent agent = Agent.builder()
                .agentId("agent-stale")
                .status("OFFLINE")
                .build();
        when(agentRepository.findByAgentId("agent-stale")).thenReturn(Optional.of(agent));
        when(agentRepository.save(any(Agent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Register new session
        connectionService.connectAgent("agent-stale", newSession);
        assertTrue(connectionService.isAgentConnected("agent-stale"));

        // Stale session disconnect attempt (using old session with id sess-1)
        connectionService.disconnectAgent("agent-stale", rawSession);

        // Agent should STILL be connected with sess-new!
        assertTrue(connectionService.isAgentConnected("agent-stale"));
        assertEquals("sess-new", connectionService.getAgentSession("agent-stale").getId());
    }

    @Test
    void testDisconnectMarksAgentOffline() {
        Agent agent = Agent.builder()
                .agentId("agent-disc")
                .status("ONLINE")
                .build();
        when(agentRepository.findByAgentId("agent-disc")).thenReturn(Optional.of(agent));
        when(agentRepository.save(any(Agent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        connectionService.connectAgent("agent-disc", rawSession);
        assertTrue(connectionService.isAgentConnected("agent-disc"));

        connectionService.disconnectAgent("agent-disc", rawSession);
        assertFalse(connectionService.isAgentConnected("agent-disc"));
        assertEquals("OFFLINE", agent.getStatus());
    }
}
