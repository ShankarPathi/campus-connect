package com.campusconnect.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the originating client IP for rate-limiting keys (Story 2.5). Prefers the first hop of the
 * {@code X-Forwarded-For} header (the gateway / Caddy proxy prepends the real client), else falls back
 * to {@code getRemoteAddr()}.
 *
 * <p><b>Security:</b> {@code X-Forwarded-For} is client-spoofable, so in production it must be trusted
 * only when the request arrives from the known reverse proxy (configured in Epic 10 / Caddy). Behind an
 * untrusted hop an attacker could rotate this header to evade the per-IP login throttle.
 */
public final class ClientIp {

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private ClientIp() {
    }

    public static String from(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
