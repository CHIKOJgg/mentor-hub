package com.mentorhub.adversarial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentorhub.dto.CreateStudentRequest;
import com.mentorhub.dto.TogglePauseRequest;
import com.mentorhub.entity.AdminToken;
import com.mentorhub.entity.Student;
import com.mentorhub.repository.AdminTokenRepository;
import com.mentorhub.repository.StudentRepository;
import com.mentorhub.service.AnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
@org.springframework.test.context.NestedTestConfiguration(org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration.INHERIT)
public class ApiDisciplineAdversarialTest {

    private static final String KSENIA_TOKEN = "magic-ksenia-mentor-token-998877";
    private static final String KATYA_TOKEN = "magic-katya-curator-token-112233";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AdminTokenRepository adminTokenRepository;

    @org.junit.jupiter.api.AfterEach
    void cleanupDatabase() {
        studentRepository.findAll().stream()
                .filter(s -> s.getId() > 5)
                .forEach(studentRepository::delete);
        adminTokenRepository.findAll().stream()
                .filter(t -> t.getAdminName().contains("Admin"))
                .forEach(adminTokenRepository::delete);
    }

    // =========================================================================
    // 1. MAGIC LINK SECURITY STRESS TESTS
    // =========================================================================
    @Nested
    @DisplayName("1. Magic Link Security & Authorization Adversarial Tests")
    class MagicLinkSecurityTests {

        @Test
        @DisplayName("All protected REST endpoints return 401 Unauthorized without admin token")
        void testProtectedEndpointsRejectUnauthenticated() throws Exception {
            String[] protectedEndpoints = {
                    "/api/inbox",
                    "/api/students",
                    "/api/analytics/summary",
                    "/api/analytics/case/1",
                    "/api/tasks/rules",
                    "/api/system/zoom-tracker"
            };

            for (String endpoint : protectedEndpoints) {
                mockMvc.perform(get(endpoint))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.status").value(401))
                        .andExpect(jsonPath("$.error").value("Unauthorized"));
            }
        }

        @Test
        @DisplayName("Public status and health endpoints allow unauthenticated access (200 OK)")
        void testPublicEndpointsAllowUnauthenticated() throws Exception {
            mockMvc.perform(get("/api/system/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.springBootVersion").value("3.4.2"));

            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Ksenia's magic token works via header, Bearer, and query param")
        void testKseniaTokenAuthenticationMethods() throws Exception {
            // Header: X-Admin-Token
            mockMvc.perform(get("/api/students")
                            .header("X-Admin-Token", KSENIA_TOKEN))
                    .andExpect(status().isOk());

            // Header: Authorization Bearer
            mockMvc.perform(get("/api/students")
                            .header("Authorization", "Bearer " + KSENIA_TOKEN))
                    .andExpect(status().isOk());

            // Query parameter: ?token=
            mockMvc.perform(get("/api/students")
                            .param("token", KSENIA_TOKEN))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Katya's magic token works via header, Bearer, and query param")
        void testKatyaTokenAuthenticationMethods() throws Exception {
            // Header: X-Admin-Token
            mockMvc.perform(get("/api/inbox")
                            .header("X-Admin-Token", KATYA_TOKEN))
                    .andExpect(status().isOk());

            // Header: Authorization Bearer
            mockMvc.perform(get("/api/inbox")
                            .header("Authorization", "Bearer " + KATYA_TOKEN))
                    .andExpect(status().isOk());

            // Query parameter: ?token=
            mockMvc.perform(get("/api/inbox")
                            .param("token", KATYA_TOKEN))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Forged, malformed, inactive and SQL injection tokens are blocked with 401")
        void testAdversarialTokensBlocked() throws Exception {
            String[] maliciousTokens = {
                    "invalid-token-12345",
                    "' OR '1'='1",
                    "../../etc/passwd",
                    "<script>alert(1)</script>",
                    "Bearer",
                    "   "
            };

            for (String badToken : maliciousTokens) {
                mockMvc.perform(get("/api/inbox")
                                .header("X-Admin-Token", badToken))
                        .andExpect(status().isUnauthorized());
            }

            // Inactive token
            String inactiveTokenStr = "inactive-" + UUID.randomUUID();
            AdminToken inactiveToken = AdminToken.builder()
                    .adminName("InactiveAdmin")
                    .role("CURATOR")
                    .token(inactiveTokenStr)
                    .isActive(false)
                    .build();
            adminTokenRepository.save(inactiveToken);

            mockMvc.perform(get("/api/inbox")
                            .header("X-Admin-Token", inactiveTokenStr))
                    .andExpect(status().isUnauthorized());

            // Expired token
            String expiredTokenStr = "expired-" + UUID.randomUUID();
            AdminToken expiredToken = AdminToken.builder()
                    .adminName("ExpiredAdmin")
                    .role("CURATOR")
                    .token(expiredTokenStr)
                    .isActive(true)
                    .expiresAt(Instant.now().minus(2, ChronoUnit.HOURS))
                    .build();
            adminTokenRepository.save(expiredToken);

            mockMvc.perform(get("/api/inbox")
                            .header("X-Admin-Token", expiredTokenStr))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // 2. DISCIPLINE CALENDAR & STREAK FREEZE STRESS TESTS
    // =========================================================================
    @Nested
    @DisplayName("2. Discipline Calendar & Streak Freeze Adversarial Tests")
    class DisciplineCalendarFreezeTests {

        @Test
        @DisplayName("Sunday is always marked as WEEKEND and excluded from discipline penalties")
        void testSundayIsMarkedAsWeekend() throws Exception {
            mockMvc.perform(get("/api/students/st-1/discipline-calendar")
                            .header("X-Admin-Token", KSENIA_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(7)))
                    .andExpect(jsonPath("$[6].day").value("Вс"))
                    .andExpect(jsonPath("$[6].status").value("WEEKEND"))
                    .andExpect(jsonPath("$[6].missedDetails").value(containsString("выходной потока ☕")));
        }

        @Test
        @DisplayName("Seeded paused student (Victoria Lebedeva) has PAUSED status and frozen streak")
        void testVictoriaLebedevaPausedState() throws Exception {
            mockMvc.perform(get("/api/students/st-4")
                            .header("X-Admin-Token", KSENIA_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fullName").value("Виктория Лебедева"))
                    .andExpect(jsonPath("$.isOnPause").value(true))
                    .andExpect(jsonPath("$.pauseReason").value(containsString("ОРВИ")))
                    .andExpect(jsonPath("$.weekDays[4].status").value("PAUSED"))
                    .andExpect(jsonPath("$.weekDays[5].status").value("PAUSED"))
                    .andExpect(jsonPath("$.weekDays[6].status").value("WEEKEND"))
                    .andExpect(jsonPath("$.disciplineRate").value(93));
        }

        @Test
        @DisplayName("Toggle pause to true freezes streak with custom reason, toggle to false unfreezes")
        void testTogglePauseFlow() throws Exception {
            // Student 2 (Anya Petrova) is initially active
            TogglePauseRequest pauseReq = new TogglePauseRequest(true, "В отпуске до 15 ноября");

            mockMvc.perform(post("/api/students/st-2/toggle-pause")
                            .header("X-Admin-Token", KSENIA_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(pauseReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isOnPause").value(true))
                    .andExpect(jsonPath("$.streakFrozen").value(true))
                    .andExpect(jsonPath("$.pauseReason").value("В отпуске до 15 ноября"));

            // Check student profile reflects pause
            mockMvc.perform(get("/api/students/st-2")
                            .header("X-Admin-Token", KSENIA_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isOnPause").value(true))
                    .andExpect(jsonPath("$.pauseReason").value("В отпуске до 15 ноября"));

            // Toggle pause back to false
            TogglePauseRequest unpauseReq = new TogglePauseRequest(false, null);

            mockMvc.perform(post("/api/students/st-2/toggle-pause")
                            .header("X-Admin-Token", KSENIA_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(unpauseReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isOnPause").value(false));
        }

        @Test
        @DisplayName("New dynamic student in paused state gets all working days marked as PAUSED and 100% discipline")
        void testDynamicStudentPausedDisciplineRate() throws Exception {
            Student student = Student.builder()
                    .fullName("Тестовая Ученица Пауза")
                    .avatarText("ТП")
                    .tgUsername("test_pause_" + UUID.randomUUID().toString().substring(0, 6))
                    .tgChatId(88776655L)
                    .niche("Нутрициолог")
                    .tariff("STANDARD")
                    .initialIncome(new BigDecimal("20000.00"))
                    .currentIncome(new BigDecimal("20000.00"))
                    .initialAvgTicket(new BigDecimal("2000.00"))
                    .currentAvgTicket(new BigDecimal("2000.00"))
                    .startDate(LocalDate.now())
                    .isActive(true)
                    .isPaused(true)
                    .pauseReason("Декретный отпуск")
                    .build();
            Student saved = studentRepository.save(student);

            mockMvc.perform(get("/api/students/st-" + saved.getId())
                            .header("X-Admin-Token", KSENIA_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isOnPause").value(true))
                    .andExpect(jsonPath("$.disciplineRate").value(100)) // evaluatedDays=0 -> defaults safely to 100%
                    .andExpect(jsonPath("$.weekDays[0].status").value("PAUSED"))
                    .andExpect(jsonPath("$.weekDays[1].status").value("PAUSED"))
                    .andExpect(jsonPath("$.weekDays[6].status").value("WEEKEND"));
        }
    }

    // =========================================================================
    // 3. MULTIPLIER CALCULATOR & DIVISION BY ZERO STRESS TESTS
    // =========================================================================
    @Nested
    @DisplayName("3. Multiplier Calculator & Division by Zero Adversarial Tests")
    class MultiplierCalculatorTests {

        @Test
        @DisplayName("Zero initial income (initialIncome=0.00) returns x1.0 and does not crash with ArithmeticException")
        void testZeroInitialIncomeMultiplier() throws Exception {
            Student student = Student.builder()
                    .fullName("Новичок с Нулевым Доходом")
                    .avatarText("НН")
                    .tgUsername("zero_income_" + UUID.randomUUID().toString().substring(0, 6))
                    .tgChatId(99112233L)
                    .niche("Коуч с нуля")
                    .tariff("STANDARD")
                    .initialIncome(BigDecimal.ZERO)
                    .currentIncome(new BigDecimal("150000.00"))
                    .initialAvgTicket(BigDecimal.ZERO)
                    .currentAvgTicket(new BigDecimal("15000.00"))
                    .startDate(LocalDate.now())
                    .isActive(true)
                    .isPaused(false)
                    .build();
            Student saved = studentRepository.save(student);

            mockMvc.perform(get("/api/analytics/case/st-" + saved.getId())
                            .header("X-Admin-Token", KSENIA_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.initialIncome").value(0))
                    .andExpect(jsonPath("$.currentIncome").value(150000.00))
                    .andExpect(jsonPath("$.growthMultiplier").value("x1.0"))
                    .andExpect(jsonPath("$.growthPercent").value("+0%"))
                    .andExpect(jsonPath("$.generatedStoryCopy").value(containsString("РОСТ x1.0!")));
        }

        @Test
        @DisplayName("Null initial income in calculation helper returns 1.0 and does not throw NPE or ArithmeticException")
        void testNullInitialIncomeMultiplier() {
            // Test calculation logic directly with all corner cases
            assertThat(AnalyticsService.calculateMultiplier(new BigDecimal("80000"), null)).isEqualTo("1.0");
            assertThat(AnalyticsService.calculateMultiplier(null, new BigDecimal("30000"))).isEqualTo("1.0");
            assertThat(AnalyticsService.calculateMultiplier(null, null)).isEqualTo("1.0");
            assertThat(AnalyticsService.calculateMultiplier(BigDecimal.ZERO, BigDecimal.ZERO)).isEqualTo("1.0");
            assertThat(AnalyticsService.calculateMultiplier(new BigDecimal("-5000"), new BigDecimal("30000"))).isEqualTo("1.0");
            assertThat(AnalyticsService.calculateMultiplier(new BigDecimal("180000"), new BigDecimal("30000"))).isEqualTo("6.0");
        }

        @Test
        @DisplayName("POST /api/students with null initialIncome safely defaults to 0.00 and allows case generation")
        void testCreateStudentWithNullIncomeAndGenerateCase() throws Exception {
            CreateStudentRequest req = new CreateStudentRequest(
                    "Ученица Без Точки А",
                    "БА",
                    "user_" + UUID.randomUUID().toString().substring(0, 6),
                    99554433L,
                    "Таролог",
                    "STANDARD",
                    null, // null initialIncome
                    null, // null initialAvgTicket
                    null
            );

            String responseContent = mockMvc.perform(post("/api/students")
                            .header("X-Admin-Token", KSENIA_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.initialIncome").value(0))
                    .andReturn().getResponse().getContentAsString();

            String studentId = objectMapper.readTree(responseContent).get("id").asText();

            // Calling /api/analytics/case/{id} succeeds with x1.0
            mockMvc.perform(get("/api/analytics/case/" + studentId)
                            .header("X-Admin-Token", KSENIA_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.growthMultiplier").value("x1.0"))
                    .andExpect(jsonPath("$.growthPercent").value("+0%"));
        }

        @Test
        @DisplayName("Valid Point A -> Point B accurately computes multiplier (e.g. 30k -> 180k = x6.0)")
        void testValidMultiplierComputation() throws Exception {
            mockMvc.perform(get("/api/analytics/case/st-1")
                            .header("X-Admin-Token", KSENIA_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.growthMultiplier").value("x6.0"))
                    .andExpect(jsonPath("$.growthPercent").value("+500%"))
                    .andExpect(jsonPath("$.generatedStoryCopy").value(containsString("РОСТ x6.0!")));
        }
    }

    // =========================================================================
    // 4. 10 CROSS-CUTTING METRICS UNDER BOUNDARY CONDITIONS
    // =========================================================================
    @Nested
    @DisplayName("4. 10 Cross-Cutting Metrics Under Boundary Conditions")
    class CrossCuttingMetricsTests {

        @Test
        @DisplayName("GET /api/analytics/summary returns all 10 core metrics correctly")
        void testAnalyticsSummaryMetrics() throws Exception {
            mockMvc.perform(get("/api/analytics/summary")
                            .header("X-Admin-Token", KSENIA_TOKEN))
                    .andExpect(status().isOk())
                    // Metric 1: Reels
                    .andExpect(jsonPath("$.totalReels", greaterThanOrEqualTo(66)))
                    // Metric 2: Carousels
                    .andExpect(jsonPath("$.totalCarousels", greaterThanOrEqualTo(38)))
                    // Metric 3: Storytells
                    .andExpect(jsonPath("$.totalStorytells", greaterThanOrEqualTo(47)))
                    // Metric 4: Mailings
                    .andExpect(jsonPath("$.totalMailings", greaterThanOrEqualTo(702)))
                    // Metric 5: Revenue (Total Earned)
                    .andExpect(jsonPath("$.totalEarned", greaterThanOrEqualTo(895000.0)))
                    // Metric 6: Current Avg Ticket
                    .andExpect(jsonPath("$.currentAvgTicket", notNullValue()))
                    // Metric 7: Revenue Growth Factor
                    .andExpect(jsonPath("$.revenueGrowthFactor", notNullValue()))
                    // Metric 8: Ticket Growth Factor
                    .andExpect(jsonPath("$.avgTicketGrowthFactor", notNullValue()))
                    // Metric 9: New Clients
                    .andExpect(jsonPath("$.totalNewClients", greaterThanOrEqualTo(33)))
                    // Metric 10: AI Reflection Count & Discipline Rate
                    .andExpect(jsonPath("$.aiReflectionProcessedCount", greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.activeBurnoutAlertsCount", greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.averageDisciplineRate", greaterThan(0.0)));
        }

        @Test
        @DisplayName("Zero sales in report card renders 0 without crashing or omitting field")
        void testZeroSalesInInboxCard() throws Exception {
            mockMvc.perform(get("/api/inbox")
                            .header("X-Admin-Token", KSENIA_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                    // Find report with 0 sales (Anya Petrova)
                    .andExpect(jsonPath("$[?(@.salesAmount == 0)]").exists());
        }
    }

    // =========================================================================
    // 5. REST API CONTRACT & ERROR HANDLING ADVERSARIAL TESTS
    // =========================================================================
    @Nested
    @DisplayName("5. REST API Contract & RFC 7807 Error Handling")
    class RestContractTests {

        @Test
        @DisplayName("Both numeric id (1) and prefixed id (st-1, rep-1, rule-1) are parsed transparently")
        void testIdPrefixSupport() throws Exception {
            // Student: numeric vs prefixed
            mockMvc.perform(get("/api/students/1").header("X-Admin-Token", KSENIA_TOKEN))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/students/st-1").header("X-Admin-Token", KSENIA_TOKEN))
                    .andExpect(status().isOk());

            // Task Rule: numeric vs prefixed
            mockMvc.perform(get("/api/tasks/rules/1").header("X-Admin-Token", KSENIA_TOKEN))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/tasks/rules/rule-1").header("X-Admin-Token", KSENIA_TOKEN))
                    .andExpect(status().isOk());

            // Inbox: numeric vs prefixed
            mockMvc.perform(get("/api/inbox/1").header("X-Admin-Token", KSENIA_TOKEN))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/inbox/rep-1").header("X-Admin-Token", KSENIA_TOKEN))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Non-existent resource returns 404 ProblemDetail (RFC 7807)")
        void testNonExistentResourceReturns404ProblemDetail() throws Exception {
            mockMvc.perform(get("/api/students/999999")
                            .header("X-Admin-Token", KSENIA_TOKEN))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Resource Not Found"))
                    .andExpect(jsonPath("$.type").value("https://mentorhub.com/errors/not-found"))
                    .andExpect(jsonPath("$.detail").value(containsString("Student not found")));
        }

        @Test
        @DisplayName("Invalid payload on POST /api/students returns 400 ProblemDetail with field errors")
        void testInvalidPayloadReturnsValidationProblemDetail() throws Exception {
            // Missing required fullName
            CreateStudentRequest badRequest = new CreateStudentRequest(
                    null, // Missing fullName (@NotBlank)
                    "НН",
                    "invalid_user",
                    12345L,
                    "Ниша",
                    "STANDARD",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null
            );

            mockMvc.perform(post("/api/students")
                            .header("X-Admin-Token", KSENIA_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Bad Request"))
                    .andExpect(jsonPath("$.errors.fullName").exists());
        }
    }
}
