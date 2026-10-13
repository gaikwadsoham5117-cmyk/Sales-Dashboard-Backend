package com.example.SalesDashboard.agent.service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import com.example.SalesDashboard.agent.entity.Agent;
import com.example.SalesDashboard.agent.exception.AgentOfflineException;
import com.example.SalesDashboard.agent.repository.AgentRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AgentConnectionService {

    private final AgentRepository agentRepository;
    private final int sendTimeLimitMs;
    private final int bufferSizeLimitBytes;

    /*
     * Runtime WebSocket connections wrapped in ConcurrentWebSocketSessionDecorator.
     *
     * IMPORTANT:
     * MongoDB status is only persistent information.
     * This map represents the REAL active concurrency-safe WebSocket connection.
     */
    private final Map<String, WebSocketSession> connectedAgents =
            new ConcurrentHashMap<>();

    /*
     * Throttle MongoDB lastSeen updates to at most once per 60 seconds per agent.
     */
    private final Map<String, Instant> lastDbTouchTimes =
            new ConcurrentHashMap<>();

    private static final Duration LAST_SEEN_THROTTLE =
            Duration.ofSeconds(60);

    @Autowired
    public AgentConnectionService(
            AgentRepository agentRepository,
            @Value("${agent.ws.send-time-limit-ms:10000}") int sendTimeLimitMs,
            @Value("${agent.ws.send-buffer-size-limit-bytes:10485760}") int bufferSizeLimitBytes
    ) {
        this.agentRepository = agentRepository;
        this.sendTimeLimitMs = Math.max(1000, sendTimeLimitMs);
        this.bufferSizeLimitBytes = Math.max(65536, bufferSizeLimitBytes);
    }

    public AgentConnectionService(AgentRepository agentRepository) {
        this(agentRepository, 10000, 10485760);
    }

    // ============================================================
    // CONNECT
    // ============================================================

    public void connectAgent(
            String agentId,
            WebSocketSession session
    ) {

        if (agentId == null || agentId.isBlank() || session == null) {
            return;
        }

        /*
         * Wrap the raw session once in ConcurrentWebSocketSessionDecorator
         * to guarantee serialized, concurrency-safe sends across all threads.
         */
        WebSocketSession safeSession = session instanceof ConcurrentWebSocketSessionDecorator
                ? session
                : new ConcurrentWebSocketSessionDecorator(session, sendTimeLimitMs, bufferSizeLimitBytes);

        connectedAgents.put(agentId, safeSession);
        lastDbTouchTimes.put(agentId, Instant.now());

        Agent agent =
                agentRepository
                        .findByAgentId(agentId)
                        .orElse(null);

        if (agent != null) {
            agent.setStatus("ONLINE");
            agent.setLastSeen(LocalDateTime.now());
            agentRepository.save(agent);
            log.info("Agent marked ONLINE in MongoDB | agentId={}", agentId);
        }
    }

    // ============================================================
    // DISCONNECT
    // ============================================================

    /**
     * Remove the session ONLY if the session that is closing
     * matches the active session. This prevents older closing sessions
     * from disconnecting newer connections.
     */
    public void disconnectAgent(
            String agentId,
            WebSocketSession closingSession
    ) {

        if (agentId == null || agentId.isBlank()) {
            return;
        }

        connectedAgents.computeIfPresent(
                agentId,
                (id, currentSession) -> {
                    if (currentSession != null
                            && closingSession != null
                            && currentSession.getId().equals(closingSession.getId())) {
                        return null;
                    }
                    return currentSession;
                }
        );

        WebSocketSession activeSession = connectedAgents.get(agentId);

        if (activeSession == null || !activeSession.isOpen()) {
            lastDbTouchTimes.remove(agentId);

            Agent agent =
                    agentRepository
                            .findByAgentId(agentId)
                            .orElse(null);

            if (agent != null) {
                agent.setStatus("OFFLINE");
                agent.setLastSeen(LocalDateTime.now());
                agentRepository.save(agent);
                log.info("Agent marked OFFLINE in MongoDB | agentId={}", agentId);
            }
        }
    }

    // ============================================================
    // SAFE SEND
    // ============================================================

    public void send(String agentId, TextMessage message) throws IOException {
        WebSocketSession session = connectedAgents.get(agentId);
        if (session == null || !session.isOpen()) {
            throw new AgentOfflineException("Tally Agent is offline: " + agentId);
        }
        session.sendMessage(message);
    }

    public void send(WebSocketSession session, TextMessage message) throws IOException {
        if (session == null || !session.isOpen()) {
            throw new AgentOfflineException("Tally Agent session is closed");
        }
        session.sendMessage(message);
    }

    // ============================================================
    // GET SESSION
    // ============================================================

    public WebSocketSession getAgentSession(
            String agentId
    ) {
        return connectedAgents.get(agentId);
    }

    // ============================================================
    // CHECK CONNECTION
    // ============================================================

    public boolean isAgentConnected(
            String agentId
    ) {

        WebSocketSession session =
                connectedAgents.get(agentId);

        if (session == null) {
            return false;
        }

        if (!session.isOpen()) {
            connectedAgents.remove(
                    agentId,
                    session
            );
            return false;
        }

        return true;
    }

    // ============================================================
    // UPDATE LAST SEEN (THROTTLED)
    // ============================================================

    public void touchAgent(
            String agentId
    ) {

        if (agentId == null || agentId.isBlank()) {
            return;
        }

        WebSocketSession session =
                connectedAgents.get(agentId);

        if (session == null || !session.isOpen()) {
            return;
        }

        Instant now = Instant.now();
        Instant lastTouch = lastDbTouchTimes.get(agentId);

        if (lastTouch != null && Duration.between(lastTouch, now).compareTo(LAST_SEEN_THROTTLE) < 0) {
            // Throttled: do not query or save MongoDB
            return;
        }

        lastDbTouchTimes.put(agentId, now);

        Agent agent =
                agentRepository
                        .findByAgentId(agentId)
                        .orElse(null);

        if (agent != null) {
            agent.setStatus("ONLINE");
            agent.setLastSeen(LocalDateTime.now());
            agentRepository.save(agent);
            log.debug("Throttled lastSeen updated in MongoDB | agentId={}", agentId);
        }
    }

    // ============================================================
    // USER-BASED LOOKUP
    // ============================================================

    public List<Agent> getAgentsByUserId(
            String userId
    ) {
        return agentRepository.findByUserId(userId);
    }

    // ============================================================
    // ORGANIZATION-BASED LOOKUP
    // ============================================================

    public List<Agent> getAgentsByOrganizationId(
            String organizationId
    ) {
        return agentRepository.findByOrganizationId(organizationId);
    }
}