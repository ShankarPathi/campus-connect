package com.campusconnect.admin;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * College-admin / TPO service. Also the host for scheduled jobs (architecture §10) — {@code @EnableScheduling}
 * activates the {@code @Scheduled} jobs (Story 7.2 offer-expiry is the first). {@code @EnableSchedulerLock}
 * (Story 10.4) makes those jobs single-fire across instances via a Mongo lock (see {@code ShedLockConfig}).
 * Scans {@code com.campusconnect} so shared {@code common-lib} beans are picked up.
 */
@SpringBootApplication(scanBasePackages = "com.campusconnect")
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class AdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }
}
