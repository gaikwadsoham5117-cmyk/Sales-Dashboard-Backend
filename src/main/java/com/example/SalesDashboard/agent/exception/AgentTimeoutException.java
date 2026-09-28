package com.example.SalesDashboard.agent.exception;

public class AgentTimeoutException extends RuntimeException {
    public AgentTimeoutException(String message) {
        super(message);
    }
}