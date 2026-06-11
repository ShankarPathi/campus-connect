package com.campusconnect.recruiter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the Spring application context starts cleanly.
 * MongoDB client creation is lazy, so this passes without a running database.
 */
@SpringBootTest
class RecruiterServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
