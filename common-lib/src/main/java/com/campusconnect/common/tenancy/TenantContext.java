package com.campusconnect.common.tenancy;

/**
 * Request-scoped holder for the current tenant (and the authenticated user/role), backed by a
 * {@link ThreadLocal}. Populated per request by the JWT security filter (Story 1.4) and read by the
 * tenant-aware repositories to stamp and filter {@code tenantId}.
 *
 * <p>{@code PLATFORM_ADMIN} requests carry no tenant — they operate outside the tenant filter
 * (e.g. managing the {@code tenants} collection via the non-tenant-aware repository).
 *
 * <p>Always {@link #clear()} in a {@code finally} block: a {@link ThreadLocal} left set on a pooled
 * thread would leak the tenant into the next request handled by that thread.
 */
public final class TenantContext {

    private record Holder(String tenantId, String userId, String role) {
    }

    private static final ThreadLocal<Holder> CONTEXT = new ThreadLocal<>();

    private TenantContext() {
    }

    /** Binds the current tenant/user/role to this thread. Any argument may be null (e.g. platform admin has no tenant). */
    public static void set(String tenantId, String userId, String role) {
        CONTEXT.set(new Holder(tenantId, userId, role));
    }

    /** The current tenant id, or {@code null} if none is bound. */
    public static String getTenantId() {
        Holder h = CONTEXT.get();
        return h != null ? h.tenantId() : null;
    }

    /** The current user id, or {@code null} if none is bound. */
    public static String getUserId() {
        Holder h = CONTEXT.get();
        return h != null ? h.userId() : null;
    }

    /** The current role, or {@code null} if none is bound. */
    public static String getRole() {
        Holder h = CONTEXT.get();
        return h != null ? h.role() : null;
    }

    /**
     * The current tenant id, or throws if none is bound. Used by tenant-scoped repository operations:
     * a tenant-scoped read/write with no tenant in context is a programming/security error, never a
     * normal client condition.
     */
    public static String requireTenantId() {
        String tenantId = getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("No tenant bound to the current context for a tenant-scoped operation");
        }
        return tenantId;
    }

    /** Clears the thread-bound context. MUST be called in a finally block per request. */
    public static void clear() {
        CONTEXT.remove();
    }
}
