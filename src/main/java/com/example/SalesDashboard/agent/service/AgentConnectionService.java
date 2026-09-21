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

    private final Map<String, WebSocketSession> connectedAgents =
            new ConcurrentHashMap<>();


    // ============================================================
    // AGENT CONNECT
    // ============================================================

    public void connectAgent(
            String agentId,
            WebSocketSession session
    ) {

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
    // AGENT DISCONNECT
    // ============================================================

    public void disconnectAgent(
            String agentId
    ) {

        connectedAgents.remove(
                agentId
        );

        Agent agent =
                agentRepository
                        .findByAgentId(agentId)
                        .orElse(null);

        if (agent != null) {

            agent.setStatus("OFFLINE");

            agent.setLastSeen(
                    LocalDateTime.now()
            );

            agentRepository.save(agent);
        }
    }


    // ============================================================
    // GET SESSION
    // ============================================================

    public WebSocketSession getAgentSession(
            String agentId
    ) {

        return connectedAgents.get(
                agentId
        );
    }


    // ============================================================
    // CHECK CONNECTION
    // ============================================================

    public boolean isAgentConnected(
            String agentId
    ) {

        WebSocketSession session =
                connectedAgents.get(
                        agentId
                );

        return session != null
                && session.isOpen();
    }


    // ============================================================
    // EXISTING USER-BASED LOOKUP
    // ============================================================

    public List<Agent> getAgentsByUserId(
            String userId
    ) {

        return agentRepository.findByUserId(
                userId
        );
    }


    // ============================================================
    // NEW ORGANIZATION-BASED LOOKUP
    // ============================================================

    public List<Agent> getAgentsByOrganizationId(
            String organizationId
    ) {

        return agentRepository.findByOrganizationId(
                organizationId
        );
    }
}