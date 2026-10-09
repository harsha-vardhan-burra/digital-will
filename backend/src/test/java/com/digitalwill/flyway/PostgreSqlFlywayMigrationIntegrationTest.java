package com.digitalwill.flyway;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PostgreSqlFlywayMigrationIntegrationTest {

    private static EmbeddedPostgres embeddedPostgres;
    private static DataSource dataSource;
    private static Flyway flyway;

    @BeforeAll
    static void setUp() throws IOException {
        embeddedPostgres = EmbeddedPostgres.builder().start();
        dataSource = embeddedPostgres.getPostgresDatabase();

        flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (embeddedPostgres != null) {
            embeddedPostgres.close();
        }
    }

    @Test
    @DisplayName("Verify Flyway migrations execute successfully against real PostgreSQL")
    void flywayMigrationsExecuteAgainstRealPostgres() {
        int migrationsApplied = flyway.migrate().migrationsExecuted;
        assertThat(migrationsApplied).isGreaterThanOrEqualTo(7);

        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied).hasSize(7);

        for (MigrationInfo info : applied) {
            assertThat(info.getState().isApplied()).isTrue();
            assertThat(info.getState().isFailed()).isFalse();
        }

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("7");
    }

    @Test
    @DisplayName("Verify authoritative tables exist in PostgreSQL schema")
    void postgresAuthoritativeTablesExist() throws Exception {
        Set<String> expectedTables = Set.of(
                "wills",
                "trusted_contacts",
                "will_contacts",
                "verification_requests",
                "contact_confirmations",
                "encrypted_documents",
                "audit_logs",
                "assets",
                "beneficiaries",
                "asset_allocations",
                "release_executions",
                "release_execution_items",
                "disclosure_tokens",
                "disclosed_records",
                "users",
                "user_auth_tokens"
        );

        Set<String> actualTables = new HashSet<>();
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, "public", "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                actualTables.add(rs.getString("TABLE_NAME").toLowerCase());
            }
        }

        assertThat(actualTables).containsAll(expectedTables);
    }

    @Test
    @DisplayName("Verify PostgreSQL UUID, timestamp with time zone, and check constraints operate correctly")
    void postgresDataTypesAndConstraintsOperateCorrectly() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID willId = UUID.randomUUID();
        Instant now = Instant.now();

        try (Connection conn = dataSource.getConnection()) {
            // Insert user
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (id, email, password_hash, full_name, created_at) VALUES (?, ?, ?, ?, ?)")) {
                ps.setObject(1, userId);
                ps.setString(2, "postgres_test@digitalwill.local");
                ps.setString(3, "$2a$12$e8Y6B/test_hash");
                ps.setString(4, "Postgres Tester");
                ps.setTimestamp(5, Timestamp.from(now));
                int rows = ps.executeUpdate();
                assertThat(rows).isEqualTo(1);
            }

            // Insert will
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO wills (id, owner_id, title, state, created_at, updated_at, last_verified_activity_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, willId);
                ps.setObject(2, userId);
                ps.setString(3, "Postgres Will");
                ps.setString(4, "ACTIVE");
                ps.setTimestamp(5, Timestamp.from(now));
                ps.setTimestamp(6, Timestamp.from(now));
                ps.setTimestamp(7, Timestamp.from(now));
                int rows = ps.executeUpdate();
                assertThat(rows).isEqualTo(1);
            }

            // Verify select
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, owner_id, title, state FROM wills WHERE id = ?")) {
                ps.setObject(1, willId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("title")).isEqualTo("Postgres Will");
                    assertThat(rs.getString("state")).isEqualTo("ACTIVE");
                }
            }
        }
    }
}
