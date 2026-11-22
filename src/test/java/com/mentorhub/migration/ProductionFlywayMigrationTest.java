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

        assertThat(production.info().current().getVersion().getVersion()).isEqualTo("19");
        assertThat(count("students")).isZero();
        assertThat(count("daily_reports")).isZero();
        assertThat(count("task_rules")).isZero();
        assertThat(count("curator_scripts")).isZero();
        assertThat(count("mentor_settings")).isEqualTo(1);
        assertThat(count("admin_tokens")).isZero();
        assertThat(count("information_schema.columns WHERE lower(table_name) = 'content_drafts' AND lower(column_name) = 'file_unique_id'"))
                .isEqualTo(1);
        assertThat(count("information_schema.columns WHERE lower(table_name) = 'mentor_settings' AND lower(column_name) = 'report_ack_enabled'"))
                .isEqualTo(1);
        assertThat(count("information_schema.columns WHERE lower(table_name) = 'students' AND lower(column_name) = 'task_plan_raw'"))
                .isEqualTo(1);
        assertThat(count("information_schema.columns WHERE lower(table_name) = 'students' AND lower(column_name) = 'currency'"))
                .isEqualTo(1);

        production.migrate();
        assertThat(count("students")).isZero();
    }

    @Test
    void productionBaselinePreservesExistingDataAndSkipsLegacyDataFixes() throws Exception {
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

        assertThat(production.info().current().getVersion().getVersion()).isEqualTo("19");
        assertThat(count("daily_reports")).isEqualTo(seededReports);
        assertThat(count("information_schema.columns WHERE lower(table_name) = 'content_drafts' AND lower(column_name) = 'file_unique_id'"))
                .isEqualTo(1);
        assertThat(appliedLegacyDataFixes()).isZero();
        assertThat(count("admin_tokens WHERE is_active = TRUE AND token IN ('magic-ksenia-mentor-token-998877', 'magic-katya-curator-token-112233')"))
                .isZero();
    }

    @Test
    void v19RemovesMiroslavAndPreservesAllOtherStudents() throws Exception {
        Flyway baseline = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/production-migration")
                .baselineOnMigrate(true)
                .baselineVersion("9")
                .ignoreMigrationPatterns("*:missing", "*:future")
                .target("18")
                .load();
        baseline.migrate();

        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("""
                    INSERT INTO students (id, full_name, avatar_text, tg_username, tg_chat_id, niche)
                    VALUES
                        (100, 'Мирослав', 'М', 'yxxtg', -5369189672, 'Бьюти'),
                        (101, 'Панкова Анна', 'ПА', 'pankova_a', 11111, 'Брови'),
                        (102, 'Гурская Елена', 'ГЭ', 'gurskaya_e', 22222, 'Ногти'),
                        (103, 'Алампиева Ольга', 'АО', 'alampieva_o', 33333, 'Визаж'),
                        (104, 'Садченко Мария', 'СМ', 'sadchenko_m', 44444, 'Ресницы'),
                        (105, 'Вероника', 'В', 'veronika_v', -1003279128599, 'Волосы'),
                        (106, 'Якубовская Дарья', 'ЯД', 'yakubovskaya_d', 55555, 'Косметология'),
                        (107, '123', '12', 'student123', 66666, 'Массаж');
                    """);

            statement.executeUpdate("""
                    INSERT INTO daily_reports (id, student_id, report_date, status, submitted_at)
                    VALUES
                        (1001, 100, '2026-11-01', 'PENDING', CURRENT_TIMESTAMP),
                        (1002, 101, '2026-11-01', 'APPROVED', CURRENT_TIMESTAMP);
                    """);

            statement.executeUpdate("""
                    INSERT INTO sales_entries (id, report_id, student_id, amount)
                    VALUES
                        (2001, 1001, 100, 15000.00),
                        (2002, 1002, 101, 25000.00);
                    """);

            statement.executeUpdate("""
                    INSERT INTO reel_submissions (id, report_id, reel_url, canonical_url, shortcode, reel_hash)
                    VALUES
                        (3001, 1001, 'https://instagram.com/reel/1', 'https://instagram.com/reel/1', 'r1', 'hash1'),
                        (3002, 1002, 'https://instagram.com/reel/2', 'https://instagram.com/reel/2', 'r2', 'hash2');
                    """);

            statement.executeUpdate("""
                    INSERT INTO content_drafts (id, report_id, draft_type, label)
                    VALUES
                        (4001, 1001, 'REEL', 'Черновик 1'),
                        (4002, 1002, 'REEL', 'Черновик 2');
                    """);

            statement.executeUpdate("""
                    INSERT INTO zoom_meetings (id, student_id, meeting_title)
                    VALUES
                        (5001, 100, 'Созвон Мирослав'),
                        (5002, 101, 'Созвон Панкова');
                    """);

            statement.executeUpdate("""
                    INSERT INTO student_day_overrides (id, student_id, day_date, status)
                    VALUES
                        (6001, 100, '2026-11-02', 'REST'),
                        (6002, 101, '2026-11-02', 'REST');
                    """);

            statement.executeUpdate("""
                    INSERT INTO audit_message_logs (id, update_id, tg_chat_id, source_type, raw_payload, processing_status)
                    VALUES
                        (7001, 9001, -5369189672, 'TG', '{"from":{"username":"yxxtg"},"text":"отчет мирослав"}', 'PROCESSED'),
                        (7002, 9002, 11111, 'TG', '{"from":{"username":"pankova_a"},"text":"отчет"}', 'PROCESSED');
                    """);
        }

        Flyway production = productionFlyway();
        production.migrate();

        assertThat(production.info().current().getVersion().getVersion()).isEqualTo("19");

        assertThat(count("students WHERE id = 100")).isZero();
        assertThat(count("daily_reports WHERE student_id = 100")).isZero();
        assertThat(count("sales_entries WHERE student_id = 100")).isZero();
        assertThat(count("reel_submissions WHERE id = 3001")).isZero();
        assertThat(count("content_drafts WHERE id = 4001")).isZero();
        assertThat(count("zoom_meetings WHERE student_id = 100")).isZero();
        assertThat(count("student_day_overrides WHERE student_id = 100")).isZero();
        assertThat(count("audit_message_logs WHERE tg_chat_id = -5369189672")).isZero();

        assertThat(count("students WHERE id = 101")).isEqualTo(1);
        assertThat(count("students WHERE id = 102")).isEqualTo(1);
        assertThat(count("students WHERE id = 103")).isEqualTo(1);
        assertThat(count("students WHERE id = 104")).isEqualTo(1);
        assertThat(count("students WHERE id = 105")).isEqualTo(1);
        assertThat(count("students WHERE id = 106")).isEqualTo(1);
        assertThat(count("students WHERE id = 107")).isEqualTo(1);
        assertThat(count("students")).isEqualTo(7);

        assertThat(count("daily_reports WHERE student_id = 101")).isEqualTo(1);
        assertThat(count("sales_entries WHERE student_id = 101")).isEqualTo(1);
        assertThat(count("reel_submissions WHERE id = 3002")).isEqualTo(1);
        assertThat(count("content_drafts WHERE id = 4002")).isEqualTo(1);
        assertThat(count("zoom_meetings WHERE student_id = 101")).isEqualTo(1);
        assertThat(count("student_day_overrides WHERE student_id = 101")).isEqualTo(1);
        assertThat(count("audit_message_logs WHERE id = 7002")).isEqualTo(1);
    }

    private Flyway productionFlyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/production-migration")
                .baselineOnMigrate(true)
                .baselineVersion("9")
                .ignoreMigrationPatterns("*:missing", "*:future")
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
