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

        assertThat(production.info().current().getVersion().getVersion()).isEqualTo("19");
        assertThat(count(url, "students")).isZero();
        assertThat(count(url, "daily_reports")).isZero();
        assertThat(count(url, "task_rules")).isZero();
        assertThat(count(url, "curator_scripts")).isZero();
        assertThat(count(url, "mentor_settings")).isEqualTo(1);
        assertThat(count(url, "admin_tokens")).isZero();
        assertThat(count(url, "information_schema.columns WHERE lower(table_name) = 'content_drafts' AND lower(column_name) = 'file_unique_id'"))
                .isEqualTo(1);
        assertThat(count(url, "information_schema.columns WHERE lower(table_name) = 'mentor_settings' AND lower(column_name) = 'report_ack_enabled'"))
                .isEqualTo(1);
        assertThat(count(url, "information_schema.columns WHERE lower(table_name) = 'students' AND lower(column_name) = 'task_plan_raw'"))
                .isEqualTo(1);
        assertThat(count(url, "information_schema.columns WHERE lower(table_name) = 'students' AND lower(column_name) = 'currency'"))
                .isEqualTo(1);
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

        assertThat(production.info().current().getVersion().getVersion()).isEqualTo("19");
        assertThat(count(url, "daily_reports")).isEqualTo(legacyReportCount);
        assertThat(count(url, "information_schema.columns WHERE lower(table_name) = 'content_drafts' AND lower(column_name) = 'file_unique_id'"))
                .isEqualTo(1);
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
                    VALUES (99, '11', 'removed cleanup migration', 'SQL', 'V11__remove_all_test_and_demo_data.sql', 123, 'sa', CURRENT_TIMESTAMP, 1, TRUE)
                    """);
        }

        productionFlyway(url).validate();
        assertThat(count(url, "daily_reports")).isEqualTo(reportsBefore);
    }

    @Test
    void v19RemovesMiroslavAndPreservesAllOtherStudents() throws Exception {
        String url = jdbcUrl();
        Flyway baseline = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/production-migration")
                .baselineOnMigrate(true)
                .baselineVersion("9")
                .ignoreMigrationPatterns("*:missing", "*:future")
                .target("18")
                .load();
        baseline.migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
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

        Flyway production = productionFlyway(url);
        production.migrate();

        assertThat(production.info().current().getVersion().getVersion()).isEqualTo("19");

        assertThat(count(url, "students WHERE id = 100")).isZero();
        assertThat(count(url, "daily_reports WHERE student_id = 100")).isZero();
        assertThat(count(url, "sales_entries WHERE student_id = 100")).isZero();
        assertThat(count(url, "reel_submissions WHERE id = 3001")).isZero();
        assertThat(count(url, "content_drafts WHERE id = 4001")).isZero();
        assertThat(count(url, "zoom_meetings WHERE student_id = 100")).isZero();
        assertThat(count(url, "student_day_overrides WHERE student_id = 100")).isZero();
        assertThat(count(url, "audit_message_logs WHERE tg_chat_id = -5369189672")).isZero();

        assertThat(count(url, "students WHERE id = 101")).isEqualTo(1);
        assertThat(count(url, "students WHERE id = 102")).isEqualTo(1);
        assertThat(count(url, "students WHERE id = 103")).isEqualTo(1);
        assertThat(count(url, "students WHERE id = 104")).isEqualTo(1);
        assertThat(count(url, "students WHERE id = 105")).isEqualTo(1);
        assertThat(count(url, "students WHERE id = 106")).isEqualTo(1);
        assertThat(count(url, "students WHERE id = 107")).isEqualTo(1);
        assertThat(count(url, "students")).isEqualTo(7);

        assertThat(count(url, "daily_reports WHERE student_id = 101")).isEqualTo(1);
        assertThat(count(url, "sales_entries WHERE student_id = 101")).isEqualTo(1);
        assertThat(count(url, "reel_submissions WHERE id = 3002")).isEqualTo(1);
        assertThat(count(url, "content_drafts WHERE id = 4002")).isEqualTo(1);
        assertThat(count(url, "zoom_meetings WHERE student_id = 101")).isEqualTo(1);
        assertThat(count(url, "student_day_overrides WHERE student_id = 101")).isEqualTo(1);
        assertThat(count(url, "audit_message_logs WHERE id = 7002")).isEqualTo(1);
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
