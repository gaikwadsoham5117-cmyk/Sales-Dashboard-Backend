package com.example.SalesDashboard.agent.controller;

import com.example.SalesDashboard.agent.dto.CreateAgentRequest;
import com.example.SalesDashboard.agent.entity.Agent;
import com.example.SalesDashboard.agent.service.AgentConnectionService;
import com.example.SalesDashboard.agent.service.AgentService;
import com.example.SalesDashboard.framework.security.JwtAuthenticationFilter;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "Agent")
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    private final AgentConnectionService
            agentConnectionService;


    // ============================================================
    // EXISTING CREATE AGENT API
    // ============================================================

    @PostMapping("/create")
    public Agent createAgent(
            @RequestBody CreateAgentRequest request,
            Authentication authentication
    ) {

        String userId =
                extractUserId(authentication);

        return agentService.createAgent(
                userId,
                request
        );
    }

    @PostMapping("/create/organization")
    public Agent createOrganizationAgent(
            @RequestBody CreateAgentRequest request,
            Authentication authentication
    ) {

        String userId =
                extractUserId(authentication);

        return agentService
                .createAgentForUserOrganization(
                        userId,
                        request
                );
    }


    // ============================================================
    // EXISTING GET MY AGENTS API
    // ============================================================

    @GetMapping("/lookup/my")
    public List<Agent> getMyAgents(
            Authentication authentication
    ) {

        String userId =
                extractUserId(authentication);

        return agentConnectionService
                .getAgentsByUserId(userId);
    }


    // ============================================================
    // NEW ORGANIZATION AGENTS API
    // ============================================================

    /**
     * Returns agents belonging to the
     * logged-in user's organization.
     */
    @GetMapping("/organization/lookup/my")
    public List<Agent> getMyOrganizationAgents(
            Authentication authentication
    ) {

        String userId =
                extractUserId(authentication);

        Agent dummy = null;
        return agentService
                .getOrganizationAgentsForUser(
                        userId
                );
    }


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
                        .filename(
                                "agent.properties"
                        )
                        .build()
        );

        return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(
                        MediaType.TEXT_PLAIN
                )
                .body(body);
    }


    // ============================================================
    // NEW ORGANIZATION CONFIG API
    // ============================================================

    /**
     * NEW API.
     *
     * GET /api/agents/organization/{agentId}/config
     *
     * The organization is resolved from
     * the authenticated user.
     */
    @GetMapping(
            "/organization/{agentId}/config"
    )
    public ResponseEntity<byte[]>
    downloadOrganizationAgentConfig(
            @PathVariable String agentId,
            Authentication authentication,
            HttpServletRequest request
    ) {

        String userId =
                extractUserId(authentication);

        Agent agent =
                agentService
                        .getOrganizationAgentForUser(
                                userId,
                                agentId
                        );

        byte[] body =
                buildOrganizationAgentProperties(
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
                        .filename(
                                "agent.properties"
                        )
                        .build()
        );

        return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(
                        MediaType.TEXT_PLAIN
                )
                .body(body);
    }


    // ============================================================
    // EXISTING AGENT PROPERTIES
    // ============================================================

    private String buildAgentProperties(
            Agent agent,
            HttpServletRequest request
    ) {

        String backendWsUrl =
                getBackendWebSocketUrl(
                        request
                );

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


    // ============================================================
    // NEW ORGANIZATION AGENT PROPERTIES
    // ============================================================

    private String buildOrganizationAgentProperties(
            Agent agent,
            HttpServletRequest request
    ) {

        String backendWsUrl =
                getBackendWebSocketUrl(
                        request
                );

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

                + "ORGANIZATION_ID="
                + agent.getOrganizationId()
                + "\n\n"

                + "TALLY_LOCAL_URL=http://localhost:9000\n\n"

                + "AGENT_NAME="
                + agent.getAgentName()
                + "\n";
    }


    // ============================================================
    // WEBSOCKET URL
    // ============================================================

    private String getBackendWebSocketUrl(
            HttpServletRequest request
    ) {

        String forwardedProto =
                request.getHeader(
                        "X-Forwarded-Proto"
                );

        String forwardedHost =
                request.getHeader(
                        "X-Forwarded-Host"
                );

        String scheme;

        String host;


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


        String wsScheme =
                "https".equalsIgnoreCase(
                        scheme
                )
                        ? "wss"
                        : "ws";


        return wsScheme
                + "://"
                + host
                + "/agent-ws";
    }


    // ============================================================
    // JWT USER ID
    // ============================================================

    private String extractUserId(
            Authentication authentication
    ) {

        Object details =
                authentication.getDetails();

        if (details instanceof
                JwtAuthenticationFilter
                        .JwtAuthenticationDetails
                        jwtDetails) {

            String userId =
                    jwtDetails.getUserId();

            if (userId != null
                    && !userId.isBlank()) {

                return userId;
            }
        }

        return authentication.getName();
    }
}