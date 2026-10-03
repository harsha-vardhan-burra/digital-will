package com.digitalwill.config;

import com.digitalwill.common.TestTimeProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

@TestConfiguration
public class TestTimeConfig {

    @Bean
    @Primary
    public TestTimeProvider testTimeProvider() {
        return new TestTimeProvider(Instant.parse("2026-10-01T12:00:00Z"));
    }
}
