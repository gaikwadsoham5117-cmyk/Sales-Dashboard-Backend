package com.example.SalesDashboard.agent.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class AgentQueueFullException
        extends RuntimeException {

    public AgentQueueFullException(
            String message
    ) {
        super(message);
    }
}