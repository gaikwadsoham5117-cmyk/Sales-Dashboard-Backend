package com.example.SalesDashboard.agent.websocket;

import com.example.SalesDashboard.agent.entity.Agent;
import com.example.SalesDashboard.agent.repository.AgentRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import org.springframework.http.server.ServletServerHttpRequest;

import org.springframework.stereotype.Component;

import org.springframework.web.socket.WebSocketHandler;

import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AgentHandshakeInterceptor
        implements HandshakeInterceptor {

    private final AgentRepository agentRepository;


    @Override
    public boolean beforeHandshake(

            ServerHttpRequest request,

            ServerHttpResponse response,

            WebSocketHandler wsHandler,

            Map<String, Object> attributes

    ) throws Exception {


        if (!(request instanceof ServletServerHttpRequest)) {

            return false;
        }


        HttpServletRequest servletRequest =

                ((ServletServerHttpRequest) request)
                        .getServletRequest();


        String agentId =

                servletRequest
                        .getParameter("agentId");


        String agentKey =

                servletRequest
                        .getParameter("agentKey");


        System.out.println(
                "[AGENT HANDSHAKE] agentId="
                        + agentId
        );


        if (agentId == null ||
                agentId.isBlank()) {

            System.out.println(
                    "[AGENT HANDSHAKE FAILED] Missing agentId"
            );

            return false;
        }


        if (agentKey == null ||
                agentKey.isBlank()) {

            System.out.println(
                    "[AGENT HANDSHAKE FAILED] Missing agentKey"
            );

            return false;
        }


        Optional<Agent> agentOptional =

                agentRepository
                        .findByAgentIdAndAgentKey(
                                agentId,
                                agentKey
                        );


        if (agentOptional.isEmpty()) {

            System.out.println(
                    "[AGENT HANDSHAKE FAILED] Invalid agent credentials"
            );

            return false;
        }


        Agent agent =
                agentOptional.get();


        attributes.put(
                "agentId",
                agent.getAgentId()
        );


        attributes.put(
                "userId",
                agent.getUserId()
        );


        System.out.println(
                "[AGENT HANDSHAKE SUCCESS] "
                        + agent.getAgentId()
                        + " User: "
                        + agent.getUserId()
        );


        return true;
    }


    @Override
    public void afterHandshake(

            ServerHttpRequest request,

            ServerHttpResponse response,

            WebSocketHandler wsHandler,

            Exception exception

    ) {

    }
}