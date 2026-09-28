package com.example.SalesDashboard.agent.exception;

public class AgentUserNotFoundException extends RuntimeException {
    public AgentUserNotFoundException(String message) {
        super(message);
    }
}