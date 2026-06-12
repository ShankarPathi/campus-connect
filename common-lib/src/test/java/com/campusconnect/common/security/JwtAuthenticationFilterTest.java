package com.campusconnect.common.security;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.User;
import com.campusconnect.common.repository.UserRepository;
import com.campusconnect.common.tenancy.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-0123456789-0123456789-abcd";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 30, 15, 7));
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtService, objectMapper, userRepository);

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private static User userWithStatus(AccountStatus status) {
        User u = new User();
        u.setAccountStatus(status);
        return u;
    }

    @Test
    void activeUserToken_setsTenantContextDuringChain_andClearsAfter() throws Exception {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWithStatus(AccountStatus.ACTIVE)));
        String token = jwtService.issueAccessToken("user-1", Role.STUDENT, "tenant-a");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        HttpServletResponse response = mock(HttpServletResponse.class);

        AtomicReference<String> tenantDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> tenantDuringChain.set(TenantContext.getTenantId());

        filter.doFilterInternal(request, response, chain);

        assertThat(tenantDuringChain.get()).isEqualTo("tenant-a"); // bound during the chain
        assertThat(TenantContext.getTenantId()).isNull();          // cleared afterward (finally)
    }

    @Test
    void deactivatedUserToken_returns403AccountInactive_andDoesNotProceed() throws Exception {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWithStatus(AccountStatus.DEACTIVATED)));
        String token = jwtService.issueAccessToken("user-1", Role.STUDENT, "tenant-a");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(403);
        verify(chain, never()).doFilter(any(), any());
        assertThat(body.toString()).contains("ACCOUNT_INACTIVE").contains("\"success\":false");
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void unknownUserToken_returns403AccountInactive() throws Exception {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());
        String token = jwtService.issueAccessToken("ghost", Role.STUDENT, "tenant-a");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(403);
        verify(chain, never()).doFilter(any(), any());
        assertThat(body.toString()).contains("ACCOUNT_INACTIVE");
    }

    @Test
    void platformAdminToken_passesWithoutAnyDbLookup() throws Exception {
        String token = jwtService.issueAccessToken("platform-admin", Role.PLATFORM_ADMIN, null);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        HttpServletResponse response = mock(HttpServletResponse.class);

        AtomicReference<String> roleDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> roleDuringChain.set(TenantContext.getRole());

        filter.doFilterInternal(request, response, chain);

        assertThat(roleDuringChain.get()).isEqualTo("PLATFORM_ADMIN");
        verifyNoInteractions(userRepository); // status gate is skipped for the platform admin
    }

    @Test
    void invalidToken_returns401Envelope_andDoesNotProceed() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer not-a-real-token");
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
        assertThat(body.toString()).contains("INVALID_TOKEN").contains("\"success\":false");
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void missingToken_passesThroughUnauthenticated() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(TenantContext.getTenantId()).isNull();
    }
}
