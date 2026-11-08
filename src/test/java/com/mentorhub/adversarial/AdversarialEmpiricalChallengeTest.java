package com.mentorhub.adversarial;

import com.mentorhub.ai.dto.ExtractedReportDto;
import com.mentorhub.ai.service.DeterministicAiFallbackService;
import com.mentorhub.ai.service.SpringAiReportExtractor;
import com.mentorhub.entity.DailyReport;
import com.mentorhub.entity.ReelSubmission;
import com.mentorhub.entity.Student;
import com.mentorhub.repository.ContentDraftRepository;
import com.mentorhub.repository.DailyReportRepository;
import com.mentorhub.repository.ReelSubmissionRepository;
import com.mentorhub.repository.SalesEntryRepository;
import com.mentorhub.repository.StudentRepository;
import com.mentorhub.scheduler.CallbackQueryHandler;
import com.mentorhub.scheduler.DailyReminderScheduler;
import com.mentorhub.scheduler.SaturdaySurveyScheduler;
import com.mentorhub.telegram.antifraud.ReelsAccessibilityProbe;
import com.mentorhub.telegram.antifraud.ReelsAntiFraudEngine;
import com.mentorhub.telegram.service.TelegramSenderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ADVERSARIAL STRESS TEST SUITE
 * Rigorously stress-tests Telegram Bot, Anti-Fraud Engine, Spring AI Fallback, and Schedulers.
 */
public class AdversarialEmpiricalChallengeTest {

    // =========================================================================
    // SECTION 1: ANTI-FRAUD & DEDUPLICATION EMPIRICAL STRESS TESTS
    // =========================================================================
    @Nested
    @DisplayName("1. Anti-Fraud & Deduplication Stress Tests")
    class AntiFraudDeduplicationTests {

        private final ReelsAccessibilityProbe stubProbe = new ReelsAccessibilityProbe() {
            @Override
            public ProbeResult verifyAccessibility(String canonicalUrl) {
                if (canonicalUrl != null && canonicalUrl.contains("DELETED")) {
                    return new ProbeResult(false, 404, "Ролик не найден или удален (404)");
                }
                if (canonicalUrl != null && canonicalUrl.contains("RATE_LIMIT")) {
                    return new ProbeResult(true, 429, "TIMEOUT_UNVERIFIED: Превышен лимит запросов сети (429)");
                }
                if (canonicalUrl != null && canonicalUrl.contains("TIMEOUT")) {
                    return new ProbeResult(true, 0, "TIMEOUT_UNVERIFIED: Таймаут сети");
                }
                return new ProbeResult(true, 200, "OK");
            }
        };

        private final ReelSubmissionRepository repo = mock(ReelSubmissionRepository.class);
        private final ReelsAntiFraudEngine engine = new ReelsAntiFraudEngine(repo, stubProbe);

        @ParameterizedTest(name = "Normalize messy URL: {0}")
        @ValueSource(strings = {
                "https://www.instagram.com/reel/DC194kaP91m/?igsh=MWQ1ZGUxMzBkMA==",
                "https://instagram.com/reel/DC194kaP91m?utm_source=qr&utm_medium=copy",
                "http://instagr.am/p/DC194kaP91m/?utm_source=ig_web_copy_link",
                "https://www.instagram.com/share/reel/DC194kaP91m/",
                "https://instagram.com/reels/DC194kaP91m?fbclid=IwAR234",
                "Смотри отчет: (https://www.instagram.com/reel/DC194kaP91m?igsh=123)!",
                "«https://instagr.am/reel/DC194kaP91m»",
                "HTTPS://WWW.INSTAGRAM.COM/REEL/DC194kaP91m/?UTM_CAMPAIGN=TEST"
        })
        @DisplayName("Canonical shortcode extraction across adversarial URL variations")
        void testCanonicalShortcodeExtraction_MessyUrls(String messyUrl) {
            List<ReelsAntiFraudEngine.ExtractedReel> extracted = engine.extractReels(messyUrl);

            assertThat(extracted).isNotEmpty();
            ReelsAntiFraudEngine.ExtractedReel reel = extracted.get(0);
            assertThat(reel.shortcode()).isEqualTo("DC194kaP91m");
            assertThat(reel.canonicalUrl()).isEqualTo("https://www.instagram.com/reel/DC194kaP91m/");
        }

        @Test
        @DisplayName("Extracts multiple reels with alphanumeric, underscores and hyphens in shortcode")
        void testMultipleReelsWithSpecialCharacters() {
            String multiReels = """
                    Сдала сегодня 3 единицы контента:
                    1) https://www.instagram.com/reel/ABC_123-xyz/?igsh=1
                    2) https://instagram.com/p/---___999___---/
                    3) https://instagr.am/share/reel/Reel_Code-2026/
                    """;

            List<ReelsAntiFraudEngine.ExtractedReel> extracted = engine.extractReels(multiReels);

            assertThat(extracted).hasSize(3);
            assertThat(extracted.get(0).shortcode()).isEqualTo("ABC_123-xyz");
            assertThat(extracted.get(0).canonicalUrl()).isEqualTo("https://www.instagram.com/reel/ABC_123-xyz/");

            assertThat(extracted.get(1).shortcode()).isEqualTo("---___999___---");
            assertThat(extracted.get(1).canonicalUrl()).isEqualTo("https://www.instagram.com/reel/---___999___---/");

            assertThat(extracted.get(2).shortcode()).isEqualTo("Reel_Code-2026");
            assertThat(extracted.get(2).canonicalUrl()).isEqualTo("https://www.instagram.com/reel/Reel_Code-2026/");
        }

        @Test
        @DisplayName("SHA-256 deduplication: Internal duplicate flagged yellow banner (no fraud alert)")
        void testSha256Deduplication_InternalDuplicate_YellowWarning() {
            Student student = Student.builder().id(10L).fullName("Екатерина Смирнова").build();
            DailyReport pastReport = DailyReport.builder()
                    .id(101L)
                    .student(student)
                    .reportDate(LocalDate.of(2026, 11, 2))
                    .build();

            ReelSubmission existingSubmission = ReelSubmission.builder()
                    .id(501L)
                    .report(pastReport)
                    .shortcode("DC194kaP91m")
                    .reelHash(engine.calculateHash("DC194kaP91m"))
                    .build();

            when(repo.findByReelHash(engine.calculateHash("DC194kaP91m")))
                    .thenReturn(Optional.of(existingSubmission));

            ReelsAntiFraudEngine.AntiFraudEvaluation eval = engine.evaluateReel(
                    "https://www.instagram.com/reel/DC194kaP91m/?utm_source=test",
                    "DC194kaP91m",
                    "https://www.instagram.com/reel/DC194kaP91m/",
                    student
            );

            assertThat(eval.isDuplicate()).isTrue();
            assertThat(eval.isCrossStudentFraud()).isFalse();
            assertThat(eval.fraudAlertMessage()).isNull();
            assertThat(eval.duplicateDate()).contains("02");
            assertThat(eval.duplicateOf()).isEqualTo(existingSubmission);
        }

        @Test
        @DisplayName("SHA-256 deduplication: Cross-student submission triggers critical red fraud alert")
        void testSha256Deduplication_CrossStudentPlagiarism_RedFraudAlert() {
            Student originalAuthor = Student.builder().id(10L).fullName("Екатерина Смирнова").build();
            Student plagiarist = Student.builder().id(20L).fullName("Ольга Мошенникова").build();

            DailyReport originalReport = DailyReport.builder()
                    .id(101L)
                    .student(originalAuthor)
                    .reportDate(LocalDate.of(2026, 11, 2))
                    .build();

            ReelSubmission existingSubmission = ReelSubmission.builder()
                    .id(501L)
                    .report(originalReport)
                    .shortcode("DC194kaP91m")
                    .reelHash(engine.calculateHash("DC194kaP91m"))
                    .build();

            when(repo.findByReelHash(engine.calculateHash("DC194kaP91m")))
                    .thenReturn(Optional.of(existingSubmission));

            ReelsAntiFraudEngine.AntiFraudEvaluation eval = engine.evaluateReel(
                    "https://www.instagram.com/reel/DC194kaP91m/",
                    "DC194kaP91m",
                    "https://www.instagram.com/reel/DC194kaP91m/",
                    plagiarist
            );

            assertThat(eval.isDuplicate()).isTrue();
            assertThat(eval.isCrossStudentFraud()).isTrue();
            assertThat(eval.fraudAlertMessage())
                    .isNotNull()
                    .contains("ФРОД: ролик сдан ученицей Екатерина Смирнова")
                    .contains("02");
        }

        @Test
        @DisplayName("Accessibility probe handles 429 TooManyRequests and network timeout gracefully")
        void testAccessibilityProbe_RateLimitAndTimeoutHandling() {
            // Real probe instance testing timeout against unresolvable host
            ReelsAccessibilityProbe realProbe = new ReelsAccessibilityProbe();

            // 1. Unreachable host / timeout
            ReelsAccessibilityProbe.ProbeResult timeoutResult = realProbe.verifyAccessibility(
                    "https://non-existent-probe-test-999888777.test/reel/TIMEOUT_TEST/"
            );
            assertThat(timeoutResult.isValid()).isTrue();
            assertThat(timeoutResult.note()).contains("TIMEOUT_UNVERIFIED");

            // 2. Empty URL
            ReelsAccessibilityProbe.ProbeResult emptyResult = realProbe.verifyAccessibility("");
            assertThat(emptyResult.isValid()).isFalse();
            assertThat(emptyResult.httpStatus()).isEqualTo(400);

            // 3. Stub probe simulating 429
            ReelsAccessibilityProbe.ProbeResult result429 = stubProbe.verifyAccessibility("https://instagram.com/reel/RATE_LIMIT/");
            assertThat(result429.isValid()).isTrue();
            assertThat(result429.httpStatus()).isEqualTo(429);
            assertThat(result429.note()).contains("TIMEOUT_UNVERIFIED");

            // 4. Stub probe simulating 404 (deleted reel) -> must be flagged invalid!
            ReelsAccessibilityProbe.ProbeResult result404 = stubProbe.verifyAccessibility("https://instagram.com/reel/DELETED/");
            assertThat(result404.isValid()).isFalse();
            assertThat(result404.httpStatus()).isEqualTo(404);
        }
    }

    // =========================================================================
    // SECTION 2: SPRING AI & DETERMINISTIC FALLBACK EMPIRICAL STRESS TESTS
    // =========================================================================
    @Nested
    @DisplayName("2. Spring AI & Deterministic Fallback Stress Tests")
    class SpringAiFallbackTests {

        private final DeterministicAiFallbackService fallbackService = new DeterministicAiFallbackService();

        @Test
        @DisplayName("Generates strict 3-bullet reflection: emotions, actions, questionToMentor")
        void test3BulletReflectionStructure() {
            String reportText = """
                    #рассылки 45
                    #продажи 120.000 руб
                    #вопрос Как масштабировать трафик без слива бюджета?
                    Настроение отличное, сегодня на кураже закрыла двух клиентов!
                    """;

            ExtractedReportDto facts = fallbackService.extractFacts(reportText, "Алёна Волкова");

            assertThat(facts.reflectionSummary()).isNotNull();
            assertThat(facts.reflectionSummary().emotions()).isNotEmpty();
            assertThat(facts.reflectionSummary().actions())
                    .contains("45")
                    .contains("120000.00");
            assertThat(facts.reflectionSummary().questionToMentor())
                    .contains("Как масштабировать трафик");

            assertThat(facts.mailingsCount()).isEqualTo(45);
            assertThat(facts.salesAmount()).isEqualByComparingTo(new BigDecimal("120000.00"));
        }

        @ParameterizedTest(name = "Burnout keyword detected: {0}")
        @ValueSource(strings = {
                "Сегодня выгорела окончательно, ничего не хочу",
                "Ужасно устала от постоянных отказов в переписке",
                "После созвона руки опустились, не знаю что делать",
                "Просто нет сил, пролежала полдня в слезах",
                "ВЫГОРЕЛА, все бросить хочется!",
                "Очень Устала, апатия накатила",
                "РУКИ ОПУСТИЛИСЬ, нет сил продолжать"
        })
        @DisplayName("Detects burnout keywords and triggers BURNOUT_ALERT")
        void testBurnoutKeywordsDetection(String stressfulText) {
            String status = fallbackService.detectBurnout(stressfulText);
            assertThat(status).isEqualTo("BURNOUT_ALERT");

            ExtractedReportDto report = fallbackService.extractFacts(stressfulText, "Стрессующая Ученица");
            assertThat(report.emotionalStatus()).isEqualTo("BURNOUT_ALERT");
            assertThat(report.reflectionSummary().emotions()).contains("маркеры выгорания");
        }

        @Test
        @DisplayName("Normal working state without burnout keywords yields NORMAL status")
        void testNormalEmotionalStatus() {
            String happyText = "Провела 3 созвона, закрыла чек, полна сил и энергии!";
            String status = fallbackService.detectBurnout(happyText);
            assertThat(status).isEqualTo("NORMAL");

            ExtractedReportDto report = fallbackService.extractFacts(happyText, "Уверенная Ученица");
            assertThat(report.emotionalStatus()).isEqualTo("NORMAL");
        }

        @Test
        @DisplayName("Offline fallback resilience: 0 report loss, handles complex Russian grouping separators")
        void testOfflineFallbackResilience_ZeroReportLoss() {
            SpringAiReportExtractor extractor = new SpringAiReportExtractor(
                    fallbackService,
                    null,
                    null,
                    "mock-key"
            );

            assertThat(extractor.isAiConfigured()).isFalse();

            // Edge cases in financial text formatting
            String messyRussianFinancialReport = """
                    Сдаю вечерний отчет!
                    #рассылки 35
                    #продажи 35.500 руб (предоплата за наставничество)
                    #продажи 80к
                    #вопрос Ксения, нужно ли оформлять договор самозанятой?
                    Кружочек записала.
                    """;

            ExtractedReportDto extracted = extractor.extractFacts(messyRussianFinancialReport, "Тестовая Ученица");

            assertThat(extracted).isNotNull();
            assertThat(extracted.mailingsCount()).isEqualTo(35);
            // First sales tag recognized: 35.500 руб -> 35500.00
            assertThat(extracted.salesAmount()).isEqualByComparingTo(new BigDecimal("35500.00"));
            assertThat(extracted.mentorQuestion()).contains("договор самозанятой");

            // Audio transcription in offline mode safely handled
            String audioTranscript = extractor.transcribeAudio(new byte[]{1, 2, 3}, "circle.mp4");
            assertThat(audioTranscript).contains("офлайн-режиме");
        }
    }

    // =========================================================================
    // SECTION 3: SCHEDULERS EMPIRICAL STRESS TESTS
    // =========================================================================
    @Nested
    @DisplayName("3. Schedulers Stress Tests")
    class SchedulerTests {

        private final StudentRepository studentRepository = mock(StudentRepository.class);
        private final DailyReportRepository dailyReportRepository = mock(DailyReportRepository.class);

        private final AtomicReference<Long> lastChatId = new AtomicReference<>();
        private final AtomicReference<String> lastText = new AtomicReference<>();
        private final AtomicReference<String> lastAnswerText = new AtomicReference<>();
        private final AtomicReference<String> lastAnswerQueryId = new AtomicReference<>();
        private final AtomicInteger sendReminderCount = new AtomicInteger(0);

        private final TelegramSenderService stubSenderService = new TelegramSenderService(null) {
            @Override
            public boolean sendReminder(Long chatId, String text) {
                lastChatId.set(chatId);
                lastText.set(text);
                sendReminderCount.incrementAndGet();
                return true;
            }

            @Override
            public boolean sendSaturdaySurveyPoll(Long chatId, Long studentId, String text) {
                lastChatId.set(chatId);
                lastText.set(text);
                return true;
            }

            @Override
            public boolean answerCallbackQuery(String callbackQueryId, String text) {
                lastAnswerQueryId.set(callbackQueryId);
                lastAnswerText.set(text);
                return true;
            }

            @Override
            public boolean editMessageText(Long chatId, Integer messageId, String text) {
                lastChatId.set(chatId);
                lastText.set(text);
                return true;
            }
        };

        @Test
        @DisplayName("20:00 Reminder logic: Sunday is strictly excluded (0 reminders)")
        void testReminderLogic_SundayStrictlyExcluded() {
            DailyReminderScheduler scheduler = new DailyReminderScheduler(
                    studentRepository,
                    dailyReportRepository,
                    stubSenderService
            );

            LocalDate sunday = LocalDate.of(2026, 11, 8); // Sunday
            assertThat(sunday.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);

            List<Student> debtors = scheduler.findDebtors(sunday);
            assertThat(debtors).isEmpty();
            verifyNoInteractions(studentRepository);
            assertThat(sendReminderCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("20:00 Reminder logic: Paused/Sick students strictly excluded")
        void testReminderLogic_PausedStudentsExcluded() {
            DailyReminderScheduler scheduler = new DailyReminderScheduler(
                    studentRepository,
                    dailyReportRepository,
                    stubSenderService
            );
            ReflectionTestUtils.setField(scheduler, "rateLimitMs", 0L);

            LocalDate wednesday = LocalDate.of(2026, 11, 4);

            Student activeStudent = Student.builder().id(1L).fullName("Активная").tgChatId(71010101L).isActive(true).isPaused(false).build();
            Student pausedSickStudent = Student.builder().id(2L).fullName("Болеющая").tgChatId(71020202L).isActive(true).isPaused(true).pauseReason("ОРВИ").build();

            when(studentRepository.findByIsActiveTrue()).thenReturn(List.of(activeStudent, pausedSickStudent));
            when(dailyReportRepository.findByStudentIdAndReportDate(1L, wednesday)).thenReturn(Optional.empty());

            List<Student> debtors = scheduler.findDebtors(wednesday);

            assertThat(debtors).hasSize(1);
            assertThat(debtors.get(0).getId()).isEqualTo(1L);
            assertThat(debtors.get(0).getFullName()).isEqualTo("Активная");

            // Execute sending
            scheduler.sendReminderToStudent(debtors.get(0));
            assertThat(lastChatId.get()).isEqualTo(71010101L);
            assertThat(lastText.get()).contains("до дедлайна сдачи отчета остался 1 час (до 21:00) ⏰");
        }

        @Test
        @DisplayName("Saturday 18:00 survey: Inline buttons and callback query atomic update")
        void testSaturdaySurvey_InlineButtonsAndCallbackAtomicUpdate() {
            CallbackQueryHandler handler = new CallbackQueryHandler(
                    studentRepository,
                    dailyReportRepository,
                    stubSenderService
            );

            Student student = Student.builder()
                    .id(42L)
                    .fullName("Анастасия")
                    .tgChatId(888123456L)
                    .totalNewClients(5)
                    .build();

            when(studentRepository.findById(42L)).thenReturn(Optional.of(student));
            when(dailyReportRepository.findByStudentIdAndReportDate(eq(42L), any(LocalDate.class)))
                    .thenReturn(Optional.empty());
            when(dailyReportRepository.save(any(DailyReport.class))).thenAnswer(inv -> inv.getArgument(0));
            when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

            Chat chat = Chat.builder().id(-10099887766L).type("supergroup").build();
            Message message = Message.builder().messageId(333).chat(chat).build();

            // Simulate student clicking "+3 clients" button
            CallbackQuery callbackQuery = new CallbackQuery();
            callbackQuery.setId("query_abc_123");
            callbackQuery.setData("survey:clients:42:3");
            callbackQuery.setMessage(message);

            boolean processed = handler.handleCallbackQuery(callbackQuery);

            assertThat(processed).isTrue();

            // Atomic increment of new clients: 5 + 3 = 8
            assertThat(student.getTotalNewClients()).isEqualTo(8);
            verify(studentRepository).save(student);

            // Daily report created/updated with newClientsCount = 3
            verify(dailyReportRepository).save(argThat(r -> r.getNewClientsCount() == 3));

            // Answer callback query with celebration emoji
            assertThat(lastAnswerQueryId.get()).isEqualTo("query_abc_123");
            assertThat(lastAnswerText.get()).isEqualTo("Зафиксировано: +3 клиентов! 🎉");

            // Edit original message with confirmation
            assertThat(lastChatId.get()).isEqualTo(-10099887766L);
            assertThat(lastText.get()).contains("Спасибо! Зафиксировано новых клиентов за неделю: +3 🚀");
        }

        @Test
        @DisplayName("Saturday survey callback query: malformed and unknown student handling")
        void testSaturdaySurvey_MalformedAndEdgeCallbacks() {
            CallbackQueryHandler handler = new CallbackQueryHandler(
                    studentRepository,
                    dailyReportRepository,
                    stubSenderService
            );

            // 1. Null query
            assertThat(handler.handleCallbackQuery(null)).isFalse();

            // 2. Query with empty data
            CallbackQuery emptyDataQuery = new CallbackQuery();
            emptyDataQuery.setId("1");
            emptyDataQuery.setData(null);
            assertThat(handler.handleCallbackQuery(emptyDataQuery)).isFalse();

            // 3. Query with non-matching pattern
            CallbackQuery wrongPrefixQuery = new CallbackQuery();
            wrongPrefixQuery.setId("2");
            wrongPrefixQuery.setData("vote:like:42");
            assertThat(handler.handleCallbackQuery(wrongPrefixQuery)).isFalse();

            // 4. Non-existent student ID
            when(studentRepository.findById(999999L)).thenReturn(Optional.empty());
            CallbackQuery unknownStudentQuery = new CallbackQuery();
            unknownStudentQuery.setId("3");
            unknownStudentQuery.setData("survey:clients:999999:2");
            boolean res = handler.handleCallbackQuery(unknownStudentQuery);
            assertThat(res).isFalse();
            assertThat(lastAnswerQueryId.get()).isEqualTo("3");
            assertThat(lastAnswerText.get()).isEqualTo("Ошибка: ученица не найдена");
        }
    }

    // =========================================================================
    // SECTION 4: END-TO-END INGESTION PIPELINE STRESS TESTS
    // =========================================================================
    @Nested
    @DisplayName("4. End-to-End Ingestion Pipeline Stress Tests")
    class EndToEndPipelineTests {

        private final DailyReportRepository dailyReportRepo = mock(DailyReportRepository.class);
        private final StudentRepository studentRepo = mock(StudentRepository.class);
        private final ReelSubmissionRepository reelRepo = mock(ReelSubmissionRepository.class);
        private final ContentDraftRepository draftRepo = mock(ContentDraftRepository.class);
        private final SalesEntryRepository salesRepo = mock(SalesEntryRepository.class);

        private final AtomicReference<Long> repliedChatId = new AtomicReference<>();
        private final AtomicReference<String> replyMessage = new AtomicReference<>();

        private final TelegramSenderService stubSender = new TelegramSenderService(null) {
            @Override
            public boolean sendCaringReply(Long chatId, Integer replyToMsgId, String text) {
                repliedChatId.set(chatId);
                replyMessage.set(text);
                return true;
            }
        };

        private final ReelsAccessibilityProbe stubProbe = new ReelsAccessibilityProbe() {
            @Override
            public ProbeResult verifyAccessibility(String canonicalUrl) {
                return new ProbeResult(true, 200, "OK");
            }
        };

        @Test
        @DisplayName("Complete end-to-end report: Plagiarized Reel + Russian sales + Outreach + Mentor question")
        void testEndToEndReportProcessing_WithCrossStudentPlagiarism() {
            ReelsAntiFraudEngine antiFraud = new ReelsAntiFraudEngine(reelRepo, stubProbe);
            DeterministicAiFallbackService fallback = new DeterministicAiFallbackService();
            SpringAiReportExtractor aiExtractor = new SpringAiReportExtractor(fallback, null, null, "mock-key");

            com.mentorhub.telegram.handler.TelegramReportHandler handler =
                    new com.mentorhub.telegram.handler.TelegramReportHandler(
                            dailyReportRepo,
                            studentRepo,
                            reelRepo,
                            draftRepo,
                            salesRepo,
                            antiFraud,
                            aiExtractor,
                            fallback,
                            stubSender
                    );

            when(dailyReportRepo.save(any(DailyReport.class))).thenAnswer(i -> i.getArgument(0));
            when(studentRepo.save(any(Student.class))).thenAnswer(i -> i.getArgument(0));
            when(dailyReportRepo.findByStudentIdAndReportDate(anyLong(), any(LocalDate.class)))
                    .thenReturn(Optional.empty());

            // Setup existing reel belonging to student #1
            Student victim = Student.builder().id(1L).fullName("Милана Горохова").build();
            DailyReport victimReport = DailyReport.builder().id(10L).student(victim).reportDate(LocalDate.of(2026, 11, 3)).build();
            ReelSubmission existingReel = ReelSubmission.builder()
                    .id(100L)
                    .report(victimReport)
                    .shortcode("DC194kaP91m")
                    .reelHash(antiFraud.calculateHash("DC194kaP91m"))
                    .build();
            when(reelRepo.findByReelHash(antiFraud.calculateHash("DC194kaP91m")))
                    .thenReturn(Optional.of(existingReel));

            // Current student submitting stolen reel
            Student currentStudent = Student.builder()
                    .id(2L)
                    .fullName("Алина Ковалева")
                    .tgChatId(888999111L)
                    .currentIncome(BigDecimal.ZERO)
                    .totalMailings(0)
                    .build();

            Chat chat = Chat.builder().id(-100888999111L).type("supergroup").build();
            Message msg = Message.builder()
                    .messageId(701)
                    .chat(chat)
                    .text("""
                            Вечерний отчет:
                            https://www.instagram.com/reel/DC194kaP91m/?igsh=share_test
                            #рассылки 40
                            #продажи 45 000 руб
                            #вопрос Как лучше обработать возражение 'дорого'?
                            Устала очень, но сделала.
                            """)
                    .build();

            DailyReport saved = handler.handleStudentMessage(currentStudent, msg);

            // Assertions
            assertThat(saved).isNotNull();
            assertThat(saved.getStatus()).isEqualTo("PENDING");
            assertThat(saved.getMailingsCount()).isEqualTo(40);
            assertThat(saved.getSalesTotal()).isEqualByComparingTo(new BigDecimal("45000.00"));
            assertThat(saved.getMentorQuestion()).contains("возражение 'дорого'");
            assertThat(saved.getEmotionalStatus()).isEqualTo("BURNOUT_ALERT"); // from "Устала"

            // Reel Submissions anti-fraud check
            assertThat(saved.getReelSubmissions()).hasSize(1);
            ReelSubmission sub = saved.getReelSubmissions().get(0);
            assertThat(sub.getIsDuplicate()).isTrue();
            assertThat(sub.getShortcode()).isEqualTo("DC194kaP91m");

            // Content draft check: has red fraud alert
            assertThat(saved.getContentDrafts()).isNotEmpty();
            boolean hasFraudDraft = saved.getContentDrafts().stream()
                    .anyMatch(d -> d.getLabel().contains("ФРОД: ролик сдан ученицей Милана Горохова"));
            assertThat(hasFraudDraft).isTrue();

            // Caring reply was sent
            assertThat(repliedChatId.get()).isEqualTo(-100888999111L);
            assertThat(replyMessage.get()).contains("Принято на проверку Ксении и Кате!");
        }
    }
}

