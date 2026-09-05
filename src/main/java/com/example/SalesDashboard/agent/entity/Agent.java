package com.example.SalesDashboard.agent.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "agents")
public class Agent {

    @Id
    private String id;

    // Unique ID generated for this installed agent
    private String agentId;

    // MongoDB User ID who owns this agent
    private String userId;

    // Secret key used to authenticate agent
    private String agentKey;

    // Friendly name
    private String agentName;

    // ONLINE / OFFLINE
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime lastSeen;
}