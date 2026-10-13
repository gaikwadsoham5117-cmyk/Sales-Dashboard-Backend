package com.example.SalesDashboard.agent.model;

/**
 * Lightweight DTO representing a response from the Tally Agent.
 * Replaces wrapping large response strings inside Jackson JsonNode (TextNode)
 * to avoid duplicate in-memory string copies.
 */
public record AgentProxyResponse(
        boolean ok,
        int status,
        String body,
        String error
) {
    public static AgentProxyResponse success(int status, String body) {
        return new AgentProxyResponse(true, status, body, null);
    }

    public static AgentProxyResponse failure(int status, String error) {
        return new AgentProxyResponse(false, status, null, error);
    }
}
