package com.example.SalesDashboard.framework.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;




public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            JwtUtil jwtUtil,
            UserDetailsService userDetailsService) {

        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        try {

            String authHeader = request.getHeader("Authorization");

            // No JWT token → continue normally
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            // Extract JWT token
            String token = authHeader.substring(7);

            // Validate token first
            Claims claims;

            try {
                claims = jwtUtil.extractClaims(token);

            } catch (io.jsonwebtoken.ExpiredJwtException e) {

                logger.warn("JWT expired for subject {}: token expired at {}",
                        e.getClaims().getSubject(), e.getClaims().getExpiration());

                filterChain.doFilter(request, response);
                return;

            } catch (Exception e) {

                logger.warn("Invalid JWT token: {} - {}",
                        e.getClass().getSimpleName(), e.getMessage());

                filterChain.doFilter(request, response);
                return;
            }

            String email = claims.getSubject();
            String userId = claims.get("userId", String.class);
            String role = claims.get("role", String.class);

            // Make sure email exists
            if (email == null || email.isBlank()) {
                logger.warn("JWT token does not contain email");

                filterChain.doFilter(request, response);
                return;
            }

            // Only authenticate if no authentication already exists
            if (SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(email);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                // Store additional details
                authentication.setDetails(
                        new JwtAuthenticationDetails(userId, role)
                );

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(authentication);

                logger.debug(
                        "JWT authentication successful for user: {} with role: {}",
                        email,
                        role
                );
            }

        } catch (Exception e) {

            logger.error(
                    "JWT authentication failed: {}",
                    e.getMessage(),
                    e
            );

            // Clear authentication if JWT processing fails
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Simple object to keep JWT-specific information.
     */
    public static class JwtAuthenticationDetails {

        private final String userId;
        private final String role;

        public JwtAuthenticationDetails(String userId, String role) {
            this.userId = userId;
            this.role = role;
        }

        public String getUserId() {
            return userId;
        }

        public String getRole() {
            return role;
        }
    }
}