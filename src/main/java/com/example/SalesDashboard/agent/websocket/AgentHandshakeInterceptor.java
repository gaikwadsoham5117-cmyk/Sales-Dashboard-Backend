package com.example.SalesDashboard.agent.websocket;

import com.example.SalesDashboard.agent.entity.Agent;
import com.example.SalesDashboard.agent.repository.AgentRepository;

import jakarta.servlet.http.HttpServletRequest;

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


        if (agentId == null ||
                agentId.isBlank()) {

            return false;
        }


        if (agentKey == null ||
                agentKey.isBlank()) {

            return false;
        }


        Optional<Agent> agentOptional =

                agentRepository
                        .findByAgentIdAndAgentKey(
                                agentId,
                                agentKey
                        );


        if (agentOptional.isEmpty()) {

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