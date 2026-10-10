package com.digitalwill.flyway;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=validate"
})
class FlywayMigrationIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Verify all Flyway migrations are applied successfully and version is V7")
    void allFlywayMigrationsAppliedSuccessfully() {
        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied).isNotEmpty();
        assertThat(applied.length).isEqualTo(7);

        for (MigrationInfo info : applied) {
            assertThat(info.getState().isApplied()).isTrue();
            assertThat(info.getState().isFailed()).isFalse();
        }

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("7");
    }

    @Test
    @DisplayName("Verify authoritative tables exist in database schema")
    void authoritativeTablesExist() throws Exception {
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
             ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                actualTables.add(rs.getString("TABLE_NAME").toLowerCase());
            }
        }

        assertThat(actualTables).containsAll(expectedTables);
    }
}
