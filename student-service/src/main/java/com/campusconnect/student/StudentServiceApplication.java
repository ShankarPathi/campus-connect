package com.campusconnect.student;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Student portal + platform-admin provisioning service.
 * Scans {@code com.campusconnect} so shared {@code common-lib} beans are picked up in later stories.
 */
@SpringBootApplication(scanBasePackages = "com.campusconnect")
public class StudentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudentServiceApplication.class, args);
    }
}
