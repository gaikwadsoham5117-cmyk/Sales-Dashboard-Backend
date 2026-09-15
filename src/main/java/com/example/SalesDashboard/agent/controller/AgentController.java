package com.example.SalesDashboard.agent.controller;

import com.example.SalesDashboard.agent.dto.CreateAgentRequest;
import com.example.SalesDashboard.agent.entity.Agent;
import com.example.SalesDashboard.agent.service.AgentConnectionService;
import com.example.SalesDashboard.agent.service.AgentService;
import com.example.SalesDashboard.framework.security.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    private final AgentConnectionService
            agentConnectionService;


    @PostMapping
    public Agent createAgent(

            @RequestBody
            CreateAgentRequest request,

            Authentication authentication
    ) {

        String userId =
                extractUserId(authentication);

        return agentService.createAgent(
                userId,
                request
        );
    }


    @GetMapping
    public List<Agent> getMyAgents(
            Authentication authentication
    ) {

        String userId =
                extractUserId(authentication);

        return agentConnectionService
                .getAgentsByUserId(userId);
    }


    /**
     * Builds the agent.properties file.
     *
     * BACKEND_WS_URL is generated dynamically from the
     * incoming HTTP request.
     *
     * Local:
     *   http://localhost:8088
     *   -> ws://localhost:8088/agent-ws
     *
     * Render:
     *   https://sales-dashboard-backend-lo96.onrender.com
     *   -> wss://sales-dashboard-backend-lo96.onrender.com/agent-ws
     */
    private String buildAgentProperties(
            Agent agent,
            HttpServletRequest request
    ) {

        String backendWsUrl =
                getBackendWebSocketUrl(request);

        return "# Generated for agent: "
                + agent.getAgentName()
                + "\n\n"
                + "BACKEND_WS_URL="
                + backendWsUrl
                + "\n\n"
                + "AGENT_ID="
                + agent.getAgentId()
                + "\n\n"
                + "AGENT_KEY="
                + agent.getAgentKey()
                + "\n\n"
                + "TALLY_LOCAL_URL=http://localhost:9000\n\n"
                + "AGENT_NAME="
                + agent.getAgentName()
                + "\n";
    }


    /**
     * Dynamically creates the WebSocket URL based on
     * the server URL used by the incoming request.
     *
     * Examples:
     *
     * Local:
     *   http://localhost:8088
     *   ->
     *   ws://localhost:8088/agent-ws
     *
     * Render:
     *   https://sales-dashboard-backend-lo96.onrender.com
     *   ->
     *   wss://sales-dashboard-backend-lo96.onrender.com/agent-ws
     *
     * X-Forwarded-Proto and X-Forwarded-Host are used because
     * Render works as a reverse proxy in front of the Spring Boot
     * application.
     */
    private String getBackendWebSocketUrl(
            HttpServletRequest request
    ) {

        String forwardedProto =
                request.getHeader("X-Forwarded-Proto");

        String forwardedHost =
                request.getHeader("X-Forwarded-Host");

        String scheme;
        String host;

        /*
         * Render normally sends X-Forwarded-Proto=https.
         *
         * If the application is running locally without a proxy,
         * fall back to request.getScheme().
         */
        if (forwardedProto != null
                && !forwardedProto.isBlank()) {

            scheme =
                    forwardedProto
                            .split(",")[0]
                            .trim();

        } else {

            scheme =
                    request.getScheme();
        }


        /*
         * Render sends the public hostname through
         * X-Forwarded-Host.
         *
         * Locally, fall back to the actual server name/port.
         */
        if (forwardedHost != null
                && !forwardedHost.isBlank()) {

            host =
                    forwardedHost
                            .split(",")[0]
                            .trim();

        } else {

            host =
                    request.getServerName();

            int port =
                    request.getServerPort();

            if (port != 80
                    && port != 443) {

                host =
                        host
                                + ":"
                                + port;
            }
        }


        /*
         * Convert HTTP protocol to WebSocket protocol.
         *
         * http  -> ws
         * https -> wss
         */
        String wsScheme =
                "https".equalsIgnoreCase(scheme)
                        ? "wss"
                        : "ws";


        return wsScheme
                + "://"
                + host
                + "/agent-ws";
    }


    /**
     * Kept for cases where a user (or a script) wants just the raw
     * config without the full jar - e.g. re-pairing an agent that's
     * already installed on that PC.
     */
    @GetMapping("/{agentId}/config")
    public ResponseEntity<byte[]> downloadAgentConfig(
            @PathVariable String agentId,
            Authentication authentication,
            HttpServletRequest request
    ) {

        String userId =
                extractUserId(authentication);

        Agent agent =
                agentService.getOwnedAgent(
                        userId,
                        agentId
                );

        byte[] body =
                buildAgentProperties(
                        agent,
                        request
                ).getBytes(
                        StandardCharsets.UTF_8
                );

        HttpHeaders headers =
                new HttpHeaders();

        headers.setContentDisposition(
                ContentDisposition
                        .attachment()
                        .filename("agent.properties")
                        .build()
        );

        return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
    }


    /**
     * authentication.getName() resolves to the user's EMAIL
     * (see UserDetailsImpl.getUsername()), not the Mongo _id.
     *
     * The real Mongo userId is carried separately as a JWT claim
     * and attached by JwtAuthenticationFilter via
     * JwtAuthenticationDetails - pull it from there instead so
     * Agent.userId stores the actual user id (what
     * AgentHandshakeInterceptor and AgentRelayService key off of).
     */
    private String extractUserId(
            Authentication authentication
    ) {

        Object details =
                authentication.getDetails();

        if (details instanceof
                JwtAuthenticationFilter.JwtAuthenticationDetails
                        jwtDetails) {

            String userId =
                    jwtDetails.getUserId();

            if (userId != null
                    && !userId.isBlank()) {

                return userId;
            }
        }

        // Fallback (should not normally happen):
        // at least don't NPE.
        // Falling back to the email keeps things self-consistent,
        // but should be treated as a sign the JWT is missing
        // the userId claim.
        return authentication.getName();
    }
}