package com.campusconnect.common.tenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void setAndGet_returnBoundValues() {
        TenantContext.set("tenant-a", "user-1", "STUDENT");

        assertThat(TenantContext.getTenantId()).isEqualTo("tenant-a");
        assertThat(TenantContext.getUserId()).isEqualTo("user-1");
        assertThat(TenantContext.getRole()).isEqualTo("STUDENT");
        assertThat(TenantContext.requireTenantId()).isEqualTo("tenant-a");
    }

    @Test
    void clear_removesBinding() {
        TenantContext.set("tenant-a", "user-1", "STUDENT");
        TenantContext.clear();

        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void requireTenantId_throwsWhenUnbound() {
        assertThatThrownBy(TenantContext::requireTenantId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tenant bound");
    }

    @Test
    void requireTenantId_throwsForPlatformAdminWithNoTenant() {
        TenantContext.set(null, "platform-admin", "PLATFORM_ADMIN");

        assertThat(TenantContext.getRole()).isEqualTo("PLATFORM_ADMIN");
        assertThatThrownBy(TenantContext::requireTenantId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void context_isIsolatedPerThread() throws InterruptedException {
        TenantContext.set("tenant-main", "u", "STUDENT");
        String[] otherThreadTenant = new String[1];

        Thread t = new Thread(() -> otherThreadTenant[0] = TenantContext.getTenantId());
        t.start();
        t.join();

        assertThat(otherThreadTenant[0]).isNull();              // the other thread saw no tenant
        assertThat(TenantContext.getTenantId()).isEqualTo("tenant-main"); // this thread unaffected
    }
}
