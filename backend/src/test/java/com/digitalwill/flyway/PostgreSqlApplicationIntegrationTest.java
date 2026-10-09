package com.digitalwill.flyway;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PostgreSqlApplicationIntegrationTest {

    private static EmbeddedPostgres embeddedPostgres;

    @BeforeAll
    static void startPostgres() throws IOException {
        embeddedPostgres = EmbeddedPostgres.builder().start();
    }

    @AfterAll
    static void stopPostgres() throws IOException {
        if (embeddedPostgres != null) {
            embeddedPostgres.close();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> embeddedPostgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Test
    @DisplayName("Verify Spring Boot backend starts successfully with real PostgreSQL and Flyway migrations")
    void contextLoadsWithRealPostgreSql() throws Exception {
        assertThat(dataSource).isNotNull();
        assertThat(flyway).isNotNull();

        // Verify Flyway applied all 7 migrations
        assertThat(flyway.info().applied()).hasSize(7);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("7");

        // Verify connection is PostgreSQL
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version()")) {
            assertThat(rs.next()).isTrue();
            String dbVersion = rs.getString(1);
            assertThat(dbVersion).containsIgnoringCase("PostgreSQL");
        }
    }
}
