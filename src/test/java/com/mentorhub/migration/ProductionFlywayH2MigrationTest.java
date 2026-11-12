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

        assertThat(production.info().current().getVersion().getVersion()).isEqualTo("11");
        assertThat(count(url, "students")).isZero();
        assertThat(count(url, "daily_reports")).isZero();
        assertThat(count(url, "task_rules")).isZero();
        assertThat(count(url, "curator_scripts")).isZero();
        assertThat(count(url, "mentor_settings")).isEqualTo(1);
        assertThat(count(url, "admin_tokens")).isZero();
    }

    @Test
    void productionBaselineSkipsUnappliedLegacyDataFixMigrations() throws Exception {
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

        assertThat(production.info().current().getVersion().getVersion()).isEqualTo("11");
        assertThat(count(url, "daily_reports")).isZero();
        assertThat(count(url, "flyway_schema_history WHERE version IN ('7', '8')")).isZero();
    }

    private Flyway productionFlyway(String url) {
        return Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/production-migration")
                .baselineOnMigrate(true)
                .baselineVersion("9")
                .ignoreMigrationPatterns("*:missing")
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
