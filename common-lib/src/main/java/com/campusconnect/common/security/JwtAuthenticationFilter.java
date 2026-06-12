package com.campusconnect.common.security;

import com.campusconnect.common.tenancy.TenantContext;
import com.campusconnect.common.web.ApiError;
import com.campusconnect.common.web.ApiResponse;
import com.campusconnect.common.web.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Validates a {@code Authorization: Bearer <jwt>} header on each request: on success it populates
 * {@link TenantContext} and the Spring {@code SecurityContext}, then <b>clears them in a finally</b>
 * (so a pooled thread never retains a previous request's tenant). A present-but-invalid token is
 * rejected with 401 in the {@link ApiResponse} envelope; a missing token is passed through (the
 * authorization rules then decide).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response); // no token — let authorization decide
            return;
        }

        JwtService.AuthToken token;
        try {
            token = jwtService.parse(header.substring(BEARER_PREFIX.length()).trim());
        } catch (InvalidTokenException e) {
            writeUnauthorized(response);
            return;
        }

        try {
            TenantContext.set(token.tenantId(), token.userId(), token.role().name());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    token.userId(), null, List.of(new SimpleGrantedAuthority(token.role().authority())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.error(
                ApiError.of(ErrorCode.INVALID_TOKEN.name(), "Invalid or expired token"));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
