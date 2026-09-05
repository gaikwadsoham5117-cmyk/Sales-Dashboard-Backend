package com.example.SalesDashboard.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessage {

    private String type;

    private String agentId;

    private String userId;

    private String requestId;

    private Object data;

    private String error;
}