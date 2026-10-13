package com.example.SalesDashboard.agent.exception;

public class StreamIdleTimeoutException extends AgentTimeoutException {
    public StreamIdleTimeoutException(String message) {
        super(message);
    }
}
