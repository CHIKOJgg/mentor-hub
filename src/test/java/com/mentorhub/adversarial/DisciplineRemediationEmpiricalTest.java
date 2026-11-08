package com.mentorhub.adversarial;

import com.mentorhub.dto.DayInfoResponse;
import com.mentorhub.dto.StudentDisciplineResponse;
import com.mentorhub.entity.ContentDraft;
import com.mentorhub.entity.DailyReport;
import com.mentorhub.entity.Student;
import com.mentorhub.repository.DailyReportRepository;
import com.mentorhub.repository.StudentRepository;
import com.mentorhub.service.StudentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EMPIRICAL ADVERSARIAL CHALLENGER TEST SUITE (Iteration 2)
 * Rigorously stress-tests dynamic discipline rate calculation,
 * zero-division safety, non-evaluated day exclusions, and tooltip formats.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class DisciplineRemediationEmpiricalTest {

    @Autowired
    private StudentService studentService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private DailyReportRepository dailyReportRepository;

    // =========================================================================
    // 1. MATHEMATICAL FORMULA & ZERO-DIVISION ADVERSARIAL TESTS
    // =========================================================================
    @Nested
    @DisplayName("1. Mathematical Formula & Boundary Stress Tests")
    class FormulaAndBoundaryTests {

        @Test
        @DisplayName("Zero evaluated days returns 100% (division by zero safety)")
        void testZeroEvaluatedDaysSafety() {
            Student dummy = Student.builder().fullName("Zero Days").build();

            // All WEEKEND
            List<DayInfoResponse> allWeekend = List.of(
                    new DayInfoResponse("Вс", "08.11", "WEEKEND", "Выходной")
            );
            assertThat(studentService.calculateDisciplineRate(dummy, allWeekend)).isEqualTo(100);

            // All PAUSED
            List<DayInfoResponse> allPaused = List.of(
                    new DayInfoResponse("Пн", "02.11", "PAUSED", "Болеет"),
                    new DayInfoResponse("Вт", "03.11", "PAUSED", "Болеет"),
                    new DayInfoResponse("Вс", "08.11", "WEEKEND", "Выходной")
            );
            assertThat(studentService.calculateDisciplineRate(dummy, allPaused)).isEqualTo(100);

            // All PENDING
            List<DayInfoResponse> allPending = List.of(
                    new DayInfoResponse("Пн", "02.11", "PENDING", null),
                    new DayInfoResponse("Вт", "03.11", "PENDING", null),
                    new DayInfoResponse("Вс", "08.11", "WEEKEND", "Выходной")
            );
            assertThat(studentService.calculateDisciplineRate(dummy, allPending)).isEqualTo(100);

            // Empty list
            assertThat(studentService.calculateDisciplineRate(dummy, List.of())).isEqualTo(100);
        }

        @ParameterizedTest(name = "onTime={0}, late={1}, missed={2}, pending={3}, paused={4}, weekend={5} => expectedRate={6}%")
        @CsvSource({
                "1, 0, 0, 5, 0, 1, 100",  // 1 on-time / 1 eval = 100%
                "0, 1, 0, 5, 0, 1, 70",   // 1 late / 1 eval = 70%
                "1, 1, 0, 4, 0, 1, 85",   // (100+70)/2 = 85%
                "0, 0, 1, 5, 0, 1, 0",    // 0/1 = 0%
                "1, 0, 1, 4, 0, 1, 50",   // 100/2 = 50%
                "0, 1, 1, 4, 0, 1, 35",   // 70/2 = 35%
                "3, 1, 0, 0, 2, 1, 93",   // Victoria: 370/4 = 92.5 -> 93%
                "4, 1, 0, 0, 0, 1, 94",   // 470/5 = 94%
                "4, 1, 0, 1, 0, 0, 94",   // 470/5 = 94% (PENDING excluded)
                "2, 2, 2, 0, 0, 1, 57",   // (200+140)/6 = 340/6 = 56.67 -> 57% (Anya)
                "6, 0, 0, 0, 0, 1, 100",  // Karina: 600/6 = 100%
                "5, 1, 0, 0, 0, 1, 95"    // Milana & Polina: 570/6 = 95%
        })
        @DisplayName("Discipline rate synthetic permutations adhere to formula")
        void testSyntheticPermutations(int onTime, int late, int missed, int pending, int paused, int weekend, int expected) {
            Student dummy = Student.builder().fullName("Synthetic").build();
            List<DayInfoResponse> days = new ArrayList<>();

            for (int i = 0; i < onTime; i++) days.add(new DayInfoResponse("D", "01.11", "COMPLETED_ON_TIME", "OK"));
            for (int i = 0; i < late; i++) days.add(new DayInfoResponse("D", "01.11", "COMPLETED_LATE", "Late"));
            for (int i = 0; i < missed; i++) days.add(new DayInfoResponse("D", "01.11", "MISSED", "Missed"));
            for (int i = 0; i < pending; i++) days.add(new DayInfoResponse("D", "01.11", "PENDING", null));
            for (int i = 0; i < paused; i++) days.add(new DayInfoResponse("D", "01.11", "PAUSED", "Paused"));
            for (int i = 0; i < weekend; i++) days.add(new DayInfoResponse("Вс", "08.11", "WEEKEND", "Weekend"));

            int rate = studentService.calculateDisciplineRate(dummy, days);
            assertThat(rate).isEqualTo(expected);
        }
    }

    // =========================================================================
    // 2. COHORT INDIVIDUAL RATES & NO HARDCODED SHORTCUTS
    // =========================================================================
    @Nested
    @DisplayName("2. Dynamic Seeded Cohort Rates & Generic Dynamic Behavior")
    class DynamicCohortRatesTests {

        @Test
        @DisplayName("All 5 seeded students evaluate dynamically to authentic rates")
        void testSeededCohortRates() {
            List<StudentDisciplineResponse> cohort = studentService.getStudents(null, true);
            assertThat(cohort).hasSize(5);

            for (StudentDisciplineResponse s : cohort) {
                switch (s.tgUsername()) {
                    case "milana_gorohova" -> {
                        // Mon-Thu on time (4), Fri late (1), Sat on time (1), Sun weekend (1)
                        assertThat(s.disciplineRate()).isEqualTo(95);
                        long onTime = s.weekDays().stream().filter(d -> "COMPLETED_ON_TIME".equals(d.status())).count();
                        long late = s.weekDays().stream().filter(d -> "COMPLETED_LATE".equals(d.status())).count();
                        assertThat(onTime).isEqualTo(5);
                        assertThat(late).isEqualTo(1);
                    }
                    case "anya_petrova_style" -> {
                        // Mon on time (1), Tue missed (1), Wed late (1), Thu on time (1), Fri missed (1), Sat late (1)
                        assertThat(s.disciplineRate()).isEqualTo(57);
                        long onTime = s.weekDays().stream().filter(d -> "COMPLETED_ON_TIME".equals(d.status())).count();
                        long late = s.weekDays().stream().filter(d -> "COMPLETED_LATE".equals(d.status())).count();
                        long missed = s.weekDays().stream().filter(d -> "MISSED".equals(d.status())).count();
                        assertThat(onTime).isEqualTo(2);
                        assertThat(late).isEqualTo(2);
                        assertThat(missed).isEqualTo(2);
                    }
                    case "volkova_coach" -> {
                        // Mon-Sat on time (6)
                        assertThat(s.disciplineRate()).isEqualTo(100);
                        long onTime = s.weekDays().stream().filter(d -> "COMPLETED_ON_TIME".equals(d.status())).count();
                        assertThat(onTime).isEqualTo(6);
                    }
                    case "v_lebedeva_fit" -> {
                        // Mon-Wed on time (3), Thu late (1), Fri-Sat paused (2)
                        assertThat(s.disciplineRate()).isEqualTo(93);
                        long onTime = s.weekDays().stream().filter(d -> "COMPLETED_ON_TIME".equals(d.status())).count();
                        long late = s.weekDays().stream().filter(d -> "COMPLETED_LATE".equals(d.status())).count();
                        long paused = s.weekDays().stream().filter(d -> "PAUSED".equals(d.status())).count();
                        assertThat(onTime).isEqualTo(3);
                        assertThat(late).isEqualTo(1);
                        assertThat(paused).isEqualTo(2);
                    }
                    case "sokolova_smm" -> {
                        // Mon-Fri on time (5), Sat late (1)
                        assertThat(s.disciplineRate()).isEqualTo(95);
                        long onTime = s.weekDays().stream().filter(d -> "COMPLETED_ON_TIME".equals(d.status())).count();
                        long late = s.weekDays().stream().filter(d -> "COMPLETED_LATE".equals(d.status())).count();
                        assertThat(onTime).isEqualTo(5);
                        assertThat(late).isEqualTo(1);
                    }
                    default -> throw new IllegalStateException("Unexpected student username: " + s.tgUsername());
                }
            }
        }

        @Test
        @DisplayName("Newly created dynamic student with unique username evaluates purely dynamically")
        void testNewlyCreatedDynamicStudentEvaluation() {
            String randomUsername = "dynamic_user_" + UUID.randomUUID().toString().substring(0, 8);
            Student student = Student.builder()
                    .fullName("Динамическая Ученица")
                    .avatarText("ДУ")
                    .tgUsername(randomUsername)
                    .tgChatId(99118822L)
                    .niche("Астрология")
                    .tariff("STANDARD")
                    .initialIncome(new BigDecimal("30000.00"))
                    .currentIncome(new BigDecimal("60000.00"))
                    .startDate(LocalDate.of(2026, 11, 2))
                    .isActive(true)
                    .isPaused(false)
                    .build();
            Student saved = studentRepository.save(student);

            // Add 2 on-time reports and 1 late report to active week (2026-11-02 to 2026-11-04)
            DailyReport rep1 = DailyReport.builder()
                    .student(saved)
                    .reportDate(LocalDate.of(2026, 11, 2))
                    .submittedAt(Instant.parse("2026-11-02T16:00:00Z"))
                    .isOnTime(true)
                    .status("APPROVED")
                    .mailingsCount(10)
                    .build();
            DailyReport rep2 = DailyReport.builder()
                    .student(saved)
                    .reportDate(LocalDate.of(2026, 11, 3))
                    .submittedAt(Instant.parse("2026-11-03T17:00:00Z"))
                    .isOnTime(true)
                    .status("APPROVED")
                    .mailingsCount(15)
                    .build();
            DailyReport rep3 = DailyReport.builder()
                    .student(saved)
                    .reportDate(LocalDate.of(2026, 11, 4))
                    .submittedAt(Instant.parse("2026-11-04T20:15:00Z"))
                    .isOnTime(false)
                    .status("APPROVED")
                    .mailingsCount(20)
                    .build();

            dailyReportRepository.saveAll(List.of(rep1, rep2, rep3));

            StudentDisciplineResponse response = studentService.getStudentById(saved.getId());

            // 6 days evaluated (Mon on-time, Tue on-time, Wed late, Thu missed, Fri missed, Sat missed)
            // on-time: 2 (200), late: 1 (70), missed: 3 (0). Total = 270 / 6 = 45%
            assertThat(response.disciplineRate()).isEqualTo(45);
            assertThat(response.weekDays().get(0).status()).isEqualTo("COMPLETED_ON_TIME");
            assertThat(response.weekDays().get(1).status()).isEqualTo("COMPLETED_ON_TIME");
            assertThat(response.weekDays().get(2).status()).isEqualTo("COMPLETED_LATE");
            assertThat(response.weekDays().get(3).status()).isEqualTo("MISSED");
            assertThat(response.weekDays().get(6).status()).isEqualTo("WEEKEND");
        }
    }

    // =========================================================================
    // 3. DYNAMIC CALENDAR TOOLTIPS VERIFICATION
    // =========================================================================
    @Nested
    @DisplayName("3. Dynamic Calendar Tooltips Verification")
    class DynamicCalendarTooltipsTests {

        @Test
        @DisplayName("Completed late reports render exact late tooltip pattern: 'Сдано в HH:mm (опоздание): ...'")
        void testLateReportTooltipPattern() {
            List<StudentDisciplineResponse> cohort = studentService.getStudents(null, true);

            // Victoria Lebedeva on Thursday 05.11 (late at 22:10)
            StudentDisciplineResponse victoria = cohort.stream()
                    .filter(s -> "v_lebedeva_fit".equals(s.tgUsername()))
                    .findFirst().orElseThrow();
            DayInfoResponse thu = victoria.weekDays().get(3);
            assertThat(thu.status()).isEqualTo("COMPLETED_LATE");
            assertThat(thu.missedDetails())
                    .startsWith("Сдано в 22:10 (опоздание): ")
                    .contains("Reels #11")
                    .contains("20 рассылок");

            // Anya Petrova on Wednesday 04.11 (late at 23:15)
            StudentDisciplineResponse anya = cohort.stream()
                    .filter(s -> "anya_petrova_style".equals(s.tgUsername()))
                    .findFirst().orElseThrow();
            DayInfoResponse wed = anya.weekDays().get(2);
            assertThat(wed.status()).isEqualTo("COMPLETED_LATE");
            assertThat(wed.missedDetails())
                    .startsWith("Сдано в 23:15 (опоздание): ")
                    .contains("Карусель")
                    .contains("12 рассылок");
        }

        @Test
        @DisplayName("On-time reports render drafts, mailings, and new clients correctly in tooltip")
        void testOnTimeReportTooltipContent() {
            List<StudentDisciplineResponse> cohort = studentService.getStudents(null, true);

            // Karina Volkova on Monday 02.11 (Reels #15, 30 mailings, +2 clients)
            StudentDisciplineResponse karina = cohort.stream()
                    .filter(s -> "volkova_coach".equals(s.tgUsername()))
                    .findFirst().orElseThrow();
            DayInfoResponse mon = karina.weekDays().get(0);
            assertThat(mon.status()).isEqualTo("COMPLETED_ON_TIME");
            assertThat(mon.missedDetails())
                    .contains("Reels #15")
                    .contains("30 рассылок")
                    .contains("+2 клиента");

            // Milana Gorohova on Tuesday 03.11 (Карусель #5, 25 mailings)
            StudentDisciplineResponse milana = cohort.stream()
                    .filter(s -> "milana_gorohova".equals(s.tgUsername()))
                    .findFirst().orElseThrow();
            DayInfoResponse tue = milana.weekDays().get(1);
            assertThat(tue.status()).isEqualTo("COMPLETED_ON_TIME");
            assertThat(tue.missedDetails())
                    .contains("Карусель #5")
                    .contains("25 рассылок");
        }

        @Test
        @DisplayName("Absence tooltips strictly match canonical messages for WEEKEND, PAUSED, MISSED")
        void testCanonicalAbsenceTooltips() {
            List<StudentDisciplineResponse> cohort = studentService.getStudents(null, true);

            for (StudentDisciplineResponse s : cohort) {
                // Sunday must be WEEKEND
                DayInfoResponse sunday = s.weekDays().get(6);
                assertThat(sunday.status()).isEqualTo("WEEKEND");
                assertThat(sunday.missedDetails()).isEqualTo("Воскресенье — общий выходной потока ☕");

                // Check paused days for Victoria
                if ("v_lebedeva_fit".equals(s.tgUsername())) {
                    assertThat(s.weekDays().get(4).status()).isEqualTo("PAUSED");
                    assertThat(s.weekDays().get(4).missedDetails()).isEqualTo("Режим паузы: болезнь, стрик заморожен ☕");
                    assertThat(s.weekDays().get(5).status()).isEqualTo("PAUSED");
                    assertThat(s.weekDays().get(5).missedDetails()).isEqualTo("Режим паузы: болезнь, стрик заморожен ☕");
                }

                // Check missed days for Anya
                if ("anya_petrova_style".equals(s.tgUsername())) {
                    assertThat(s.weekDays().get(1).status()).isEqualTo("MISSED");
                    assertThat(s.weekDays().get(1).missedDetails()).isEqualTo("Пропуск: отчёт не сдан до 21:00");
                    assertThat(s.weekDays().get(4).status()).isEqualTo("MISSED");
                    assertThat(s.weekDays().get(4).missedDetails()).isEqualTo("Пропуск: отчёт не сдан до 21:00");
                }
            }
        }
    }
}
