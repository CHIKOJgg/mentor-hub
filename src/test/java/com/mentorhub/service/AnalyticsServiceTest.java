package com.mentorhub.service;

import com.mentorhub.dto.AnalyticsSummaryResponse;
import com.mentorhub.dto.StoryCaseResponse;
import com.mentorhub.entity.Student;
import com.mentorhub.repository.StudentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnalyticsServiceTest {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private StudentRepository studentRepository;

    @Test
    @DisplayName("getAnalyticsSummary calculates all 10 cross-cutting metrics accurately")
    void testGetAnalyticsSummaryMetrics() {
        AnalyticsSummaryResponse summary = analyticsService.getAnalyticsSummary();

        // 1. Total Earned (Point B Cohort Income): 895 000 ₽
        assertThat(summary.totalEarned()).isEqualByComparingTo("895000.00");

        // 2. Revenue Growth Factor: +397%
        assertThat(summary.revenueGrowthFactor()).isEqualTo("+397%");

        // 3. Total Reels verified: 66
        assertThat(summary.totalReels()).isEqualTo(66);

        // 4. Total Carousels verified: 8 + 3 + 12 + 6 + 9 = 38
        assertThat(summary.totalCarousels()).isEqualTo(38);

        // 5. Total Storytells verified: 10 + 4 + 14 + 8 + 11 = 47
        assertThat(summary.totalStorytells()).isEqualTo(47);

        // 6. Total Mailings (Direct outreach): 702
        assertThat(summary.totalMailings()).isEqualTo(702);

        // 7. Total New Clients: 7 + 2 + 11 + 5 + 8 = 33
        assertThat(summary.totalNewClients()).isEqualTo(33);

        // 8. Cohort Average Ticket (Current): 40 600 ₽
        assertThat(summary.currentAvgTicket()).isEqualByComparingTo("40600.00");

        // 9. Initial Average Ticket (Point A): 4 800 ₽
        assertThat(summary.initialAvgTicket()).isEqualByComparingTo("4800.00");

        // 10. Average Ticket Growth Factor: x8.4
        assertThat(summary.avgTicketGrowthFactor()).isEqualTo("x8.4");

        // Additional operational metrics
        assertThat(summary.averageDisciplineRate()).isEqualTo(88.0);
        assertThat(summary.activeBurnoutAlertsCount()).isGreaterThanOrEqualTo(1L);
        assertThat(summary.aiReflectionProcessedCount()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("Division by zero protection in growth calculator when Point A is 0, negative, or null")
    void testDivisionByZeroProtection() {
        assertThat(AnalyticsService.calculateMultiplier(new BigDecimal("150000.00"), BigDecimal.ZERO))
                .isEqualTo("1.0");
        assertThat(AnalyticsService.calculateMultiplier(new BigDecimal("150000.00"), new BigDecimal("-5000.00")))
                .isEqualTo("1.0");
        assertThat(AnalyticsService.calculateMultiplier(new BigDecimal("150000.00"), null))
                .isEqualTo("1.0");
        assertThat(AnalyticsService.calculateMultiplier(null, new BigDecimal("30000.00")))
                .isEqualTo("1.0");
    }

    @Test
    @DisplayName("Multiplier matches Point A -> Point B ratio for each student")
    void testGrowthMultiplierPerStudent() {
        Student milana = studentRepository.findByTgUsername("milana_gorohova").orElseThrow();
        assertThat(AnalyticsService.calculateMultiplier(milana.getCurrentIncome(), milana.getInitialIncome()))
                .isEqualTo("6.0");

        Student anya = studentRepository.findByTgUsername("anya_petrova_style").orElseThrow();
        assertThat(AnalyticsService.calculateMultiplier(anya.getCurrentIncome(), anya.getInitialIncome()))
                .isEqualTo("1.8");

        Student karina = studentRepository.findByTgUsername("volkova_coach").orElseThrow();
        assertThat(AnalyticsService.calculateMultiplier(karina.getCurrentIncome(), karina.getInitialIncome()))
                .isEqualTo("6.2");

        Student victoria = studentRepository.findByTgUsername("v_lebedeva_fit").orElseThrow();
        assertThat(AnalyticsService.calculateMultiplier(victoria.getCurrentIncome(), victoria.getInitialIncome()))
                .isEqualTo("4.0");

        Student polina = studentRepository.findByTgUsername("sokolova_smm").orElseThrow();
        assertThat(AnalyticsService.calculateMultiplier(polina.getCurrentIncome(), polina.getInitialIncome()))
                .isEqualTo("5.5");
    }

    @Test
    @DisplayName("getStoryCase generates formatted Instagram copy and variables")
    void testGetStoryCase() {
        Student milana = studentRepository.findByTgUsername("milana_gorohova").orElseThrow();
        StoryCaseResponse storyCase = analyticsService.getStoryCase(milana.getId());

        assertThat(storyCase.fullName()).isEqualTo("Милана Горохова");
        assertThat(storyCase.niche()).isEqualTo("Стилист-имиджмейкер");
        assertThat(storyCase.growthMultiplier()).isEqualTo("x6.0");
        assertThat(storyCase.growthPercent()).isEqualTo("+500%");
        assertThat(storyCase.disciplineRate()).isEqualTo(95);

        String copy = storyCase.generatedStoryCopy();
        assertThat(copy).contains("🔥 КЕЙС ПОТОКА: Милана Горохова");
        assertThat(copy).contains("Ниша: Стилист-имиджмейкер");
        assertThat(copy).contains("🚀 Точка А: 30 000 ₽ ➡️ Точка Б: 180 000 ₽ (РОСТ x6.0!)");
        assertThat(copy).contains("📈 Средний чек: вырос с 3 000 ₽ до 45 000 ₽");
        assertThat(copy).contains("🎬 Контент: 14 проверенных Reels за 3 недели");
        assertThat(copy).contains("✉️ Исходящий трафик: 145 рассылок по скрипту");
        assertThat(copy).contains("💎 Дисциплина сдачи отчетов: 95%");
        assertThat(copy).contains("«Она просто перестала сливать дедлайны и внедрила ежедневный трекинг действий»");
    }

    @Test
    @DisplayName("getStoryCase with 0 initial income uses division by zero fallback")
    void testStoryCaseZeroInitialIncome() {
        Student testStudent = Student.builder()
                .fullName("Тест Новичок")
                .avatarText("ТН")
                .niche("Дизайнер")
                .tariff("STANDARD")
                .initialIncome(BigDecimal.ZERO)
                .currentIncome(new BigDecimal("100000.00"))
                .initialAvgTicket(BigDecimal.ZERO)
                .currentAvgTicket(new BigDecimal("20000.00"))
                .isActive(true)
                .build();
        studentRepository.save(testStudent);

        StoryCaseResponse storyCase = analyticsService.getStoryCase(testStudent.getId());
        assertThat(storyCase.growthMultiplier()).isEqualTo("x1.0");
        assertThat(storyCase.growthPercent()).isEqualTo("+0%");
        assertThat(storyCase.generatedStoryCopy()).contains("(РОСТ x1.0!)");
    }

    @Test
    @DisplayName("getStoryCase throws ResourceNotFoundException for unknown ID")
    void testStoryCaseNotFound() {
        assertThatThrownBy(() -> analyticsService.getStoryCase(999999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
