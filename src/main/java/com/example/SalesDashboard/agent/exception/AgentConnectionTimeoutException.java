package com.example.SalesDashboard.agent.exception;

public class AgentConnectionTimeoutException extends RuntimeException {
    public AgentConnectionTimeoutException(String message) {
        super(message);
    }
}
