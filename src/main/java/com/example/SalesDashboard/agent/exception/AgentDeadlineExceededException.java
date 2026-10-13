package com.example.SalesDashboard.agent.exception;

public class AgentDeadlineExceededException extends AgentTimeoutException {
    public AgentDeadlineExceededException(String message) {
        super(message);
    }
}
