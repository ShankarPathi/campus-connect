package com.campusconnect.admin.jobs;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * ShedLock store wiring (Story 10.4, NFR-5). Proves the Mongo-backed {@link LockProvider} enforces a
 * single holder per lock name — the guarantee behind {@code @SchedulerLock} that makes the admin-service
 * {@code @Scheduled} jobs fire exactly once per tick even across multiple instances. Deterministic (no
 * scheduler timing): acquire → second acquire is rejected while held → released → re-acquirable.
 */
@SpringBootTest
@Testcontainers
class ShedLockIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @Autowired
    LockProvider lockProvider;

    // The real OfferExpiryJob bean (ShedLock-proxied); its delegate is mocked so we can assert
    // whether the body actually ran.
    @Autowired
    OfferExpiryJob offerExpiryJob;

    @MockitoBean
    OfferExpiryService offerExpiryService;

    @Test
    void onlyOneHolderPerLockName() {
        LockConfiguration cfg = new LockConfiguration(
                Instant.now(), "offer-expiry", Duration.ofMinutes(10), Duration.ZERO);

        Optional<SimpleLock> first = lockProvider.lock(cfg);
        assertThat(first).as("first acquisition succeeds").isPresent();

        // A concurrent instance trying the same lock while it's held must be turned away.
        assertThat(lockProvider.lock(cfg)).as("second acquisition is rejected while held").isEmpty();

        first.get().unlock();

        // Once released, the lock is acquirable again (next tick / other instance).
        Optional<SimpleLock> afterRelease = lockProvider.lock(cfg);
        assertThat(afterRelease).as("re-acquirable after unlock").isPresent();
        afterRelease.get().unlock();
    }

    /**
     * The AOP-path proof: {@code @SchedulerLock} actually engages on the proxied job bean under this
     * Spring Boot version. While the {@code offer-expiry} lock is held externally, invoking the job
     * must SKIP its body (delegate not called); once released, the next invocation runs it. A silent
     * no-op of the lock annotation would fail the first assertion.
     */
    @Test
    void schedulerLockSkipsTheJobBodyWhileTheLockIsHeld() {
        LockConfiguration cfg = new LockConfiguration(
                Instant.now(), "offer-expiry", Duration.ofMinutes(10), Duration.ZERO);

        SimpleLock held = lockProvider.lock(cfg).orElseThrow();
        try {
            offerExpiryJob.expireOverdueOffers(); // @SchedulerLock can't acquire → body skipped
            verify(offerExpiryService, never()).expireOverdueOffers(any());
        } finally {
            held.unlock();
        }

        offerExpiryJob.expireOverdueOffers(); // lock free → body runs once
        verify(offerExpiryService, times(1)).expireOverdueOffers(any());
    }
}
