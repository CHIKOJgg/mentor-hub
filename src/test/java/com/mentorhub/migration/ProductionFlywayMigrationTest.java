package com.mentorhub.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class ProductionFlywayMigrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("mentor_production_migrations")
            .withUsername("migration_test")
            .withPassword("migration_test_only");

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
    }

    @Test
    void freshProductionDatabaseHasSchemaButNoDemoRows() throws Exception {
        Flyway production = productionFlyway();
        production.migrate();

        assertThat(production.info().current().getVersion().getVersion()).isEqualTo("11");
        assertThat(count("students")).isZero();
        assertThat(count("daily_reports")).isZero();
        assertThat(count("task_rules")).isZero();
        assertThat(count("curator_scripts")).isZero();
        assertThat(count("mentor_settings")).isEqualTo(1);
        assertThat(count("admin_tokens")).isZero();

        production.migrate();
        assertThat(count("students")).isZero();
    }

    @Test
    void productionBaselineSkipsUnappliedDestructiveLegacyDataFixes() throws Exception {
        Flyway legacy = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target("6")
                .load();
        legacy.migrate();
        long seededReports = count("daily_reports");
        assertThat(seededReports).isPositive();

        Flyway production = productionFlyway();
        production.migrate();

        assertThat(production.info().current().getVersion().getVersion()).isEqualTo("11");
        assertThat(count("daily_reports")).isZero();
        assertThat(appliedLegacyDataFixes()).isZero();
        assertThat(count("admin_tokens WHERE is_active = TRUE AND token IN ('magic-ksenia-mentor-token-998877', 'magic-katya-curator-token-112233')"))
                .isZero();
    }

    private Flyway productionFlyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/production-migration")
                .baselineOnMigrate(true)
                .baselineVersion("9")
                .ignoreMigrationPatterns("*:missing")
                .load();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private long count(String tableOrFilter) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + tableOrFilter)) {
            result.next();
            return result.getLong(1);
        }
    }

    private long appliedLegacyDataFixes() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('7', '8')")) {
            result.next();
            return result.getLong(1);
        }
    }
}
