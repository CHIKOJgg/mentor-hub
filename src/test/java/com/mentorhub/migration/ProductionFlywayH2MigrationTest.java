package com.mentorhub.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionFlywayH2MigrationTest {
    @Test
    void freshProductionSchemaDoesNotSeedDemoRecords() throws Exception {
        String url = jdbcUrl();
        Flyway production = productionFlyway(url);
        production.migrate();

        assertThat(production.info().current().getVersion().getVersion()).isEqualTo("10");
        assertThat(count(url, "students")).isZero();
        assertThat(count(url, "daily_reports")).isZero();
        assertThat(count(url, "task_rules")).isZero();
        assertThat(count(url, "curator_scripts")).isZero();
        assertThat(count(url, "mentor_settings")).isEqualTo(1);
        assertThat(count(url, "admin_tokens")).isZero();
    }

    @Test
    void productionBaselinePreservesRowsAndSkipsUnappliedLegacyDataFixMigrations() throws Exception {
        String url = jdbcUrl();
        Flyway legacy = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target("6")
                .load();
        legacy.migrate();
        long legacyReportCount = count(url, "daily_reports");
        assertThat(legacyReportCount).isPositive();

        Flyway production = productionFlyway(url);
        production.migrate();

        assertThat(production.info().current().getVersion().getVersion()).isEqualTo("10");
        assertThat(count(url, "daily_reports")).isEqualTo(legacyReportCount);
        assertThat(count(url, "flyway_schema_history WHERE version IN ('7', '8')")).isZero();
    }

    @Test
    void productionValidationToleratesAnAlreadyAppliedRemovedFutureMigration() throws Exception {
        String url = jdbcUrl();
        Flyway production = productionFlyway(url);
        production.migrate();
        long reportsBefore = count(url, "daily_reports");
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO flyway_schema_history
                        (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
                    VALUES (2, '11', 'removed cleanup migration', 'SQL', 'V11__remove_all_test_and_demo_data.sql', 123, 'sa', CURRENT_TIMESTAMP, 1, TRUE)
                    """);
        }

        productionFlyway(url).validate();
        assertThat(count(url, "daily_reports")).isEqualTo(reportsBefore);
    }

    private Flyway productionFlyway(String url) {
        return Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/production-migration")
                .baselineOnMigrate(true)
                .baselineVersion("9")
                .ignoreMigrationPatterns("*:missing", "*:future")
                .load();
    }

    private String jdbcUrl() {
        return "jdbc:h2:mem:prod-migration-" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    }

    private long count(String url, String tableOrFilter) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + tableOrFilter)) {
            result.next();
            return result.getLong(1);
        }
    }
}
