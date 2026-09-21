package com.example.SalesDashboard.agent.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import com.example.SalesDashboard.agent.entity.Agent;
import com.example.SalesDashboard.agent.repository.AgentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentConnectionService {

    private final AgentRepository agentRepository;

    /*
     * Runtime WebSocket connections.
     *
     * IMPORTANT:
     * MongoDB status is only persistent information.
     * This map represents the REAL active WebSocket connection.
     */
    private final Map<String, WebSocketSession> connectedAgents =
            new ConcurrentHashMap<>();


    // ============================================================
    // CONNECT
    // ============================================================

    public void connectAgent(
            String agentId,
            WebSocketSession session
    ) {

        if (agentId == null || agentId.isBlank()) {
            return;
        }

        /*
         * Replace the existing session for this agent.
         */
        connectedAgents.put(
                agentId,
                session
        );

        Agent agent =
                agentRepository
                        .findByAgentId(agentId)
                        .orElse(null);

        if (agent != null) {

            agent.setStatus("ONLINE");

            agent.setLastSeen(
                    LocalDateTime.now()
            );

            agentRepository.save(agent);
        }
    }


    // ============================================================
    // DISCONNECT
    // ============================================================

    /**
     * IMPORTANT:
     *
     * We remove the session ONLY if the session that is closing
     * is still the active session.
     *
     * This prevents:
     *
     * Old Session A closes
     *       ↓
     * accidentally removes newer Session B
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

                    /*
                     * Only remove if this exact session is closing.
                     */
                    if (currentSession != null
                            && closingSession != null
                            && currentSession.getId()
                                    .equals(closingSession.getId())) {

                        return null;
                    }

                    /*
                     * A newer connection already exists.
                     * Keep it.
                     */
                    return currentSession;
                }
        );

        /*
         * Check whether another active session still exists.
         */
        WebSocketSession activeSession =
                connectedAgents.get(agentId);

        Agent agent =
                agentRepository
                        .findByAgentId(agentId)
                        .orElse(null);

        if (agent != null) {

            if (activeSession != null
                    && activeSession.isOpen()) {

                /*
                 * Another/newer connection is active.
                 */
                agent.setStatus("ONLINE");

            } else {

                agent.setStatus("OFFLINE");

                agent.setLastSeen(
                        LocalDateTime.now()
                );
            }

            agentRepository.save(agent);
        }
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
    // UPDATE LAST SEEN
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

        Agent agent =
                agentRepository
                        .findByAgentId(agentId)
                        .orElse(null);

        if (agent != null) {

            agent.setStatus("ONLINE");

            agent.setLastSeen(
                    LocalDateTime.now()
            );

            agentRepository.save(agent);
        }
    }


    // ============================================================
    // USER-BASED LOOKUP
    // ============================================================

    public List<Agent> getAgentsByUserId(
            String userId
    ) {

        return agentRepository.findByUserId(
                userId
        );
    }


    // ============================================================
    // ORGANIZATION-BASED LOOKUP
    // ============================================================

    public List<Agent> getAgentsByOrganizationId(
            String organizationId
    ) {

        return agentRepository.findByOrganizationId(
                organizationId
        );
    }
}