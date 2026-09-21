package com.example.SalesDashboard.agent.repository;

import com.example.SalesDashboard.agent.entity.Agent;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AgentRepository
        extends MongoRepository<Agent, String> {

    Optional<Agent> findByAgentId(String agentId);

    Optional<Agent> findByAgentIdAndAgentKey(
            String agentId,
            String agentKey
    );

    // Existing user-based mapping
    List<Agent> findByUserId(String userId);

    // New organization-based mapping
    List<Agent> findByOrganizationId(String organizationId);

    boolean existsByAgentId(String agentId);
}