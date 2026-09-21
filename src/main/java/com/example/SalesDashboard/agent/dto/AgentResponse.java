package com.example.SalesDashboard.agent.dto;

import com.example.SalesDashboard.agent.entity.AgentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {

    private String agentId;

    private String agentName;

    private AgentStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime lastSeen;
}