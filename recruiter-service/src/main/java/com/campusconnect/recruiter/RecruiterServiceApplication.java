package com.campusconnect.recruiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Recruiter portal service.
 * Scans {@code com.campusconnect} so shared {@code common-lib} beans are picked up in later stories.
 */
@SpringBootApplication(scanBasePackages = "com.campusconnect")
public class RecruiterServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecruiterServiceApplication.class, args);
    }
}
