package com.campusconnect.common.security;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.repository.UserRepository;
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
import java.util.Optional;

/**
 * Validates a {@code Authorization: Bearer <jwt>} header on each request: on success it populates
 * {@link TenantContext} and the Spring {@code SecurityContext}, then <b>clears them in a finally</b>
 * (so a pooled thread never retains a previous request's tenant). A present-but-invalid token is
 * rejected with 401 in the {@link ApiResponse} envelope; a missing token is passed through (the
 * authorization rules then decide).
 *
 * <p>Story 2.5 adds a per-request active-status gate: after a token parses, the user's
 * {@code accountStatus} is re-checked against the DB and a non-{@code ACTIVE} (or vanished) account is
 * rejected with 403 {@code ACCOUNT_INACTIVE} — closing the window where an unexpired token outlives a
 * deactivation. {@code PLATFORM_ADMIN} has no DB user row and is skipped. The status gate runs before
 * the SecurityContext is set, so a wrong-role but ACTIVE user still reaches (and is rejected by)
 * method-level {@code @PreAuthorize} with 403 {@code FORBIDDEN}.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
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

        // Per-request status gate (2.5): a college user must still exist and be ACTIVE. PLATFORM_ADMIN
        // has no tenant/DB row, so it is skipped — or every Epic-1 platform endpoint would 403.
        if (token.role() != Role.PLATFORM_ADMIN) {
            Optional<User> user = userRepository.findById(token.userId());
            if (user.isEmpty() || user.get().getAccountStatus() != AccountStatus.ACTIVE) {
                writeForbidden(response);
                return;
            }
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
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                ErrorCode.INVALID_TOKEN, "Invalid or expired token");
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        writeError(response, HttpServletResponse.SC_FORBIDDEN,
                ErrorCode.ACCOUNT_INACTIVE, "Account is not active");
    }

    private void writeError(HttpServletResponse response, int status, ErrorCode code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.error(ApiError.of(code.name(), message));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
