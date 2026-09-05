package com.example.SalesDashboard.agent.service;

import com.example.SalesDashboard.agent.dto.CreateAgentRequest;
import com.example.SalesDashboard.agent.entity.Agent;
import com.example.SalesDashboard.agent.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;

    public Agent createAgent(
            String userId,
            CreateAgentRequest request
    ) {

        String agentId =
                "agent-" +
                UUID.randomUUID()
                        .toString()
                        .substring(0, 12);

        String agentKey =
                generateAgentKey();

        Agent agent = Agent.builder()
                .agentId(agentId)
                .userId(userId)
                .agentKey(agentKey)
                .agentName(
                        request.getAgentName() != null
                                ? request.getAgentName()
                                : "Tally Agent"
                )
                .status("OFFLINE")
                .createdAt(LocalDateTime.now())
                .lastSeen(null)
                .build();

        return agentRepository.save(agent);
    }

    /**
     * Fetches an agent, but only if it actually belongs to the
     * requesting user. Used by the config-download endpoint so one
     * user can never download another user's agentKey by guessing
     * an agentId in the URL.
     */
    public Agent getOwnedAgent(String userId, String agentId) {

        Agent agent =
                agentRepository
                        .findByAgentId(agentId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Agent not found"
                        ));

        if (!agent.getUserId().equals(userId)) {

            // 404, not 403 - don't reveal that the agentId exists
            // but belongs to someone else.
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Agent not found"
            );
        }

        return agent;
    }

    private String generateAgentKey() {

        byte[] bytes = new byte[32];

        new SecureRandom()
                .nextBytes(bytes);

        StringBuilder sb =
                new StringBuilder();

        for (byte b : bytes) {

            sb.append(
                    String.format(
                            "%02x",
                            b
                    )
            );
        }

        return sb.toString();
    }
}