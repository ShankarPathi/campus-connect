package com.campusconnect.admin.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * ShedLock lock store (Story 10.4, NFR-5). Backs {@code @SchedulerLock} with a Mongo {@code shedLock}
 * collection so the admin-service {@code @Scheduled} jobs (offer-expiry, email-outbox-flush) fire
 * exactly once per cron tick even if the service ever runs more than one instance.
 *
 * <p>{@code shedLock} is an ops collection (one document per lock name) — not a tenant document, so it
 * (correctly) does not flow through {@code TenantAwareRepository}; ShedLock writes it directly via the
 * shared {@link MongoTemplate}'s database.
 */
@Configuration
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(MongoTemplate mongoTemplate) {
        return new MongoLockProvider(mongoTemplate.getDb());
    }
}
