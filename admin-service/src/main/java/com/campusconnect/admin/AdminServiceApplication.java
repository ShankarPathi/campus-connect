package com.campusconnect.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * College-admin / TPO service. Also the host for scheduled jobs in later epics.
 * Scans {@code com.campusconnect} so shared {@code common-lib} beans are picked up in later stories.
 */
@SpringBootApplication(scanBasePackages = "com.campusconnect")
public class AdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }
}
