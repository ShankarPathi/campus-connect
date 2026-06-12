package com.campusconnect.common.ratelimit;

import com.campusconnect.common.exception.RateLimitException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    private final RateLimiter limiter = new RateLimiter();

    @Test
    void allowsExactlyLimitCalls_thenThrowsOnTheNext() {
        Duration window = Duration.ofMinutes(1);
        for (int i = 0; i < 5; i++) {
            assertThatCode(() -> limiter.check("k", 5, window)).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> limiter.check("k", 5, window))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    void windowResetsAfterItElapses() throws InterruptedException {
        Duration window = Duration.ofMillis(50);
        limiter.check("k", 1, window);
        assertThatThrownBy(() -> limiter.check("k", 1, window)).isInstanceOf(RateLimitException.class);

        Thread.sleep(70); // let the window elapse

        assertThatCode(() -> limiter.check("k", 1, window)).doesNotThrowAnyException();
    }

    @Test
    void distinctKeysAreThrottledIndependently() {
        Duration window = Duration.ofMinutes(1);
        limiter.check("a", 1, window);
        assertThatCode(() -> limiter.check("b", 1, window)).doesNotThrowAnyException(); // different key
        assertThatThrownBy(() -> limiter.check("a", 1, window)).isInstanceOf(RateLimitException.class);
    }

    @Test
    void atTheCap_elapsedEntriesAreSwept_soTheKeyspaceStaysBounded() throws InterruptedException {
        RateLimiter capped = new RateLimiter(2); // tiny cap to make the sweep observable
        Duration shortWindow = Duration.ofMillis(30);
        capped.check("a", 5, shortWindow);
        capped.check("b", 5, shortWindow);
        assertThat(capped.activeKeys()).isEqualTo(2); // at the cap

        Thread.sleep(50); // a and b windows elapse

        // adding a 3rd key triggers the sweep, which drops the two elapsed entries before inserting "c"
        capped.check("c", 5, Duration.ofMinutes(1));
        assertThat(capped.activeKeys()).isEqualTo(1);
    }

    @Test
    void longWindowEntryIsNotSweptByAShortWindowSweep() throws InterruptedException {
        RateLimiter capped = new RateLimiter(2);
        capped.check("otp", 5, Duration.ofMinutes(1)); // long-lived entry
        capped.check("login", 5, Duration.ofMillis(30));
        assertThat(capped.activeKeys()).isEqualTo(2);

        Thread.sleep(50); // only the short "login" window has elapsed

        capped.check("another", 5, Duration.ofMillis(30)); // triggers the sweep at the cap
        // the long-window "otp" entry survives; the elapsed "login" entry is gone
        assertThat(capped.activeKeys()).isEqualTo(2); // otp + another
    }
}
