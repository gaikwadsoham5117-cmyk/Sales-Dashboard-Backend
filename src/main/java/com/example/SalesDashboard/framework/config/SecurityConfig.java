package com.example.SalesDashboard.framework.config;

import com.example.SalesDashboard.framework.model.UserRoles;
import com.example.SalesDashboard.framework.security.JwtAuthenticationFilter;
import com.example.SalesDashboard.framework.security.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
        public DaoAuthenticationProvider authenticationProvider() {
            DaoAuthenticationProvider authProvider =
            new DaoAuthenticationProvider(userDetailsService);

            authProvider.setPasswordEncoder(passwordEncoder());

     return authProvider;
}

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(authenticationProvider());
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtUtil, userDetailsService);
    }

   @Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

    http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth

                    // Public APIs
                    .requestMatchers(
                            "/agent-ws",
                            "/auth/**",
                            "/error",
                            "/swagger-ui/**",
                            "/v3/api-docs/**",
                            "/swagger-resources/**",
                            "/api/user/login",
                            "/api/user/register",
                            "/api/admin/register",
                            "/api/otp/generate/email",
                            "/api/otp/validate",
                            "/api/user/reset-password",
                            "/api/user/forgot-password"
                    ).permitAll()

                    // User APIs
                    .requestMatchers("/api/user/lookup/all")
                    .hasAnyAuthority(UserRoles.ALL())

                    .requestMatchers("/api/user/lookup/all/active")
                    .hasAnyAuthority("USER")

                    .requestMatchers("/api/user/*/update/allfields")
                    .hasAnyAuthority(UserRoles.ALL())

                    .requestMatchers("/api/user/*/lookup")
                    .hasAnyAuthority(UserRoles.ALL())

                    .requestMatchers("/api/user/lookupall/active")
                    .hasAnyAuthority(UserRoles.ALL())

                    .requestMatchers("/api/user/forgot-password")
                    .hasAnyAuthority(UserRoles.ALL())

                    .requestMatchers("/api/user/lookup/status")
                    .hasAnyAuthority("USER", "ADMIN")

                    .requestMatchers("/api/user/lookup/all/users/*")
                    .hasAnyAuthority(UserRoles.ALL())

                    // Everything else requires authentication
                    .anyRequest()
                    .authenticated()
            )

            .addFilterBefore(
                    jwtAuthenticationFilter(),
                    UsernamePasswordAuthenticationFilter.class
            );

    return http.build();
}

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
