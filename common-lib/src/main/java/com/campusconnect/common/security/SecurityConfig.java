package com.campusconnect.common.security;

import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.web.ApiError;
import com.campusconnect.common.web.ApiResponse;
import com.campusconnect.common.web.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Shared stateless security for every service: JWT auth filter + BCrypt(12) encoder + a default
 * {@link SecurityFilterChain}. The chain is {@code @ConditionalOnMissingBean} so a service may define
 * its own. Actuator, Swagger and the per-service auth endpoints are public; everything else needs
 * auth. Spring Security's own rejections are rendered in the {@link ApiResponse} envelope.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/api/*/auth/**"
    };

    // Dedicated mapper for rendering the error envelope — independent of the app's ObjectMapper bean
    // (avoids an auto-config ordering dependency). @JsonInclude on the envelope is honored regardless.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   UserRepository userRepository) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService, objectMapper, userRepository);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()));
        return http.build();
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) ->
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        ErrorCode.UNAUTHORIZED, "Authentication required");
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) ->
                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                        ErrorCode.FORBIDDEN, "Access denied");
    }

    private void writeError(HttpServletResponse response,
                            int status, ErrorCode code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(ApiError.of(code.name(), message)));
    }
}
