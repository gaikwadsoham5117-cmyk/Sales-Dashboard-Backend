package com.example.SalesDashboard.agent.config;

import com.example.SalesDashboard.agent.websocket.AgentHandshakeInterceptor;
import com.example.SalesDashboard.agent.websocket.AgentWebSocketHandler;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class AgentWebSocketConfig implements WebSocketConfigurer {

    private final AgentWebSocketHandler agentWebSocketHandler;

    private final AgentHandshakeInterceptor agentHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(
            WebSocketHandlerRegistry registry
    ) {

        registry.addHandler(
                        agentWebSocketHandler,
                        "/agent-ws"
                )
                .addInterceptors(agentHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }

    /**
     * Tomcat's embedded WebSocket container defaults to an 8KB max
     * text-message buffer. A PROXY_RESPONSE carrying a full Tally
     * sales-voucher or company-list JSON body is routinely bigger
     * than that, so the default silently rejects it and closes the
     * connection with code 1009 ("Message Too Big") the moment the
     * agent tries to relay a real answer back.
     *
     * Raised to 10MB here; adjust upward if a single company's raw
     * export ever exceeds that.
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {

        ServletServerContainerFactoryBean container =
                new ServletServerContainerFactoryBean();

        container.setMaxTextMessageBufferSize(10 * 1024 * 1024);
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024);

        return container;
    }
}