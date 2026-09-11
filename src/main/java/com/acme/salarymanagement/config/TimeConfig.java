package com.acme.salarymanagement.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application's one clock.
 *
 * <p>A bean rather than a static call, so a test can hand the use case a fixed instant and assert
 * what was recorded. The domain never sees it: the application resolves it to an {@code Instant}
 * at its boundary and passes that inward.
 */
@Configuration
class TimeConfig {

    @Bean
    Clock clock() {
        // UTC, not the server's zone: a revision timestamp that means something different
        // depending on where it was written is not an audit trail.
        return Clock.systemUTC();
    }
}
