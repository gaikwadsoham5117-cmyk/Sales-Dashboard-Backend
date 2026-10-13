package com.example.SalesDashboard.agent.exception;

public class AgentDisconnectedException extends AgentCommunicationException {
    public AgentDisconnectedException(String message) {
        super(message);
    }
}
