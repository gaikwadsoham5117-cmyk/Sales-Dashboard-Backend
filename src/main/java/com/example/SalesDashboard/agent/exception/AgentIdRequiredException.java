package com.example.SalesDashboard.agent.exception;

public class AgentIdRequiredException extends RuntimeException {
    public AgentIdRequiredException(String message) {
        super(message);
    }
}