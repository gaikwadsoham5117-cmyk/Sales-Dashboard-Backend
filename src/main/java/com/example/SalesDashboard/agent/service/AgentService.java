package com.example.SalesDashboard.agent.service;

import com.example.SalesDashboard.agent.dto.CreateAgentRequest;
import com.example.SalesDashboard.agent.entity.Agent;
import com.example.SalesDashboard.agent.repository.AgentRepository;
import com.example.SalesDashboard.user.entity.User;
import com.example.SalesDashboard.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;

    private final UserRepository userRepository;


    // ============================================================
    // EXISTING FLOW
    // ============================================================

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

        Agent agent =
                Agent.builder()
                        .agentId(agentId)

                        // Existing user mapping
                        .userId(userId)

                        .agentKey(agentKey)

                        .agentName(
                                request.getAgentName() != null
                                        ? request.getAgentName()
                                        : "Tally Agent"
                        )

                        .status("OFFLINE")

                        .createdAt(
                                LocalDateTime.now()
                        )

                        .lastSeen(null)

                        .build();

        return agentRepository.save(agent);
    }


    // ============================================================
    // NEW ORGANIZATION FLOW
    // ============================================================

    /**
     * Creates an agent for the organization
     * belonging to the currently logged-in user.
     *
     * organizationId is NEVER received from frontend.
     *
     * Flow:
     *
     * JWT
     *   ↓
     * userId
     *   ↓
     * User
     *   ↓
     * organizationId
     *   ↓
     * Agent
     */
    public Agent createAgentForUserOrganization(
            String userId,
            CreateAgentRequest request
    ) {

        User user =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "User not found"
                                )
                        );

        String organizationId =
                user.getOrganizationId();

        if (organizationId == null
                || organizationId.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User is not associated with an organization"
            );
        }

        return createAgentForOrganization(
                organizationId,
                request
        );
    }


    /**
     * Creates the actual Agent using organizationId.
     */
    private Agent createAgentForOrganization(
            String organizationId,
            CreateAgentRequest request
    ) {

        String agentId =
                "agent-" +
                UUID.randomUUID()
                        .toString()
                        .substring(0, 12);

        String agentKey =
                generateAgentKey();

        Agent agent =
                Agent.builder()

                        .agentId(agentId)

                        // New organization mapping
                        .organizationId(
                                organizationId
                        )

                        .agentKey(agentKey)

                        .agentName(
                                request.getAgentName() != null
                                        ? request.getAgentName()
                                        : "Tally Agent"
                        )

                        .status("OFFLINE")

                        .createdAt(
                                LocalDateTime.now()
                        )

                        .lastSeen(null)

                        .build();

        return agentRepository.save(agent);
    }


    // ============================================================
    // EXISTING CONFIG API SUPPORT
    // ============================================================

    /**
     * Existing user-based agent ownership check.
     *
     * DO NOT CHANGE this method.
     *
     * It keeps the existing:
     *
     * GET /api/agents/{agentId}/config
     *
     * flow working.
     */
    public Agent getOwnedAgent(
            String userId,
            String agentId
    ) {

        Agent agent =
                agentRepository
                        .findByAgentId(agentId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Agent not found"
                                )
                        );

        if (!userId.equals(agent.getUserId())) {

            // 404 instead of 403
            // so we don't reveal that the agent exists
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Agent not found"
            );
        }

        return agent;
    }


    // ============================================================
    // NEW ORGANIZATION CONFIG API SUPPORT
    // ============================================================

    /**
     * Gets an agent only if it belongs to the
     * organization of the currently logged-in user.
     *
     * Flow:
     *
     * JWT
     *   ↓
     * userId
     *   ↓
     * User.organizationId
     *   ↓
     * Agent.organizationId
     */
    public Agent getOrganizationAgentForUser(
            String userId,
            String agentId
    ) {

        User user =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "User not found"
                                )
                        );

        String organizationId =
                user.getOrganizationId();

        if (organizationId == null
                || organizationId.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User is not associated with an organization"
            );
        }

        Agent agent =
                agentRepository
                        .findByAgentId(agentId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Agent not found"
                                )
                        );

        if (!organizationId.equals(
                agent.getOrganizationId()
        )) {

            // Do not reveal another organization's agent
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Agent not found"
            );
        }

        return agent;
    }


    // ============================================================
    // AGENT KEY GENERATION
    // ============================================================

    private String generateAgentKey() {

        byte[] bytes =
                new byte[32];

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

    public List<Agent> getOrganizationAgentsForUser(
        String userId
) {

    User user =
            userRepository.findById(userId)
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "User not found"
                            )
                    );

    String organizationId =
            user.getOrganizationId();

    if (organizationId == null
            || organizationId.isBlank()) {

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "User is not associated with an organization"
        );
    }

    return agentRepository.findByOrganizationId(
            organizationId
    );
}
}