package com.example.SalesDashboard.agent.exception;

public class AgentAlreadyExistsException extends RuntimeException {

    public AgentAlreadyExistsException(String message) {
        super(message);
    }
}