package com.mentorhub.telegram;

import com.mentorhub.ai.dto.ExtractedReportDto;
import com.mentorhub.ai.service.DeterministicAiFallbackService;
import com.mentorhub.ai.service.SpringAiReportExtractor;
import com.mentorhub.dto.ReflectionSummaryDto;
import com.mentorhub.entity.DailyReport;
import com.mentorhub.entity.Student;
import com.mentorhub.repository.ContentDraftRepository;
import com.mentorhub.repository.DailyReportRepository;
import com.mentorhub.repository.ReelSubmissionRepository;
import com.mentorhub.repository.SalesEntryRepository;
import com.mentorhub.repository.StudentRepository;
import com.mentorhub.telegram.antifraud.ReelsAccessibilityProbe;
import com.mentorhub.telegram.antifraud.ReelsAntiFraudEngine;
import com.mentorhub.telegram.handler.TelegramReportHandler;
import com.mentorhub.telegram.service.TelegramSenderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TelegramReportHandlerTest {

    private DailyReportRepository dailyReportRepository;
    private StudentRepository studentRepository;
    private ReelSubmissionRepository reelSubmissionRepository;
    private ContentDraftRepository contentDraftRepository;
    private SalesEntryRepository salesEntryRepository;
    private ReelsAntiFraudEngine reelsAntiFraudEngine;
    private SpringAiReportExtractor springAiReportExtractor;
    private DeterministicAiFallbackService deterministicAiFallbackService;

    private Long sentChatId;
    private Integer sentReplyToId;
    private String sentReplyText;
    private TelegramSenderService stubSenderService;
    private TelegramReportHandler handler;

    @BeforeEach
    void setUp() {
        dailyReportRepository = mock(DailyReportRepository.class);
        studentRepository = mock(StudentRepository.class);
        reelSubmissionRepository = mock(ReelSubmissionRepository.class);
        contentDraftRepository = mock(ContentDraftRepository.class);
        salesEntryRepository = mock(SalesEntryRepository.class);

        when(dailyReportRepository.save(any(DailyReport.class))).thenAnswer(i -> i.getArgument(0));
        when(studentRepository.save(any(Student.class))).thenAnswer(i -> i.getArgument(0));

        reelsAntiFraudEngine = new ReelsAntiFraudEngine(reelSubmissionRepository, new ReelsAccessibilityProbe() {
            @Override
            public ProbeResult verifyAccessibility(String canonicalUrl) {
                return new ProbeResult(true, 200, null);
            }
        });

        deterministicAiFallbackService = new DeterministicAiFallbackService();

        springAiReportExtractor = new SpringAiReportExtractor(deterministicAiFallbackService, null, null, "mock-key") {
            @Override
            public ExtractedReportDto extractFacts(String rawMessage, String studentName) {
                return new ExtractedReportDto(
                        List.of(),
                        BigDecimal.ZERO,
                        0,
                        0,
                        null,
                        "NORMAL",
                        "Сводка дня",
                        new ReflectionSummaryDto("Рабочее", "Выполнены рассылки", null),
                        null
                );
            }
        };

        stubSenderService = new TelegramSenderService(null) {
            @Override
            public boolean sendCaringReply(Long chatId, Integer replyToMsgId, String text) {
                sentChatId = chatId;
                sentReplyToId = replyToMsgId;
                sentReplyText = text;
                return true;
            }
        };

        handler = new TelegramReportHandler(
                dailyReportRepository,
                studentRepository,
                reelSubmissionRepository,
                contentDraftRepository,
                salesEntryRepository,
                reelsAntiFraudEngine,
                springAiReportExtractor,
                deterministicAiFallbackService,
                stubSenderService
        );
    }

    @Test
    @DisplayName("Accumulator upsert handles first message of day and sends caring acknowledgment reply")
    void handleStudentMessage_initialMessage() {
        Student student = Student.builder()
                .id(1L)
                .fullName("Милана Горохова")
                .tgChatId(71010101L)
                .currentIncome(new BigDecimal("180000.00"))
                .totalMailings(100)
                .build();

        Chat chat = Chat.builder().id(-10071010101L).type("supergroup").build();
        Message message = Message.builder()
                .messageId(501)
                .chat(chat)
                .text("#рассылки 25\n#продажи 35.500 руб\n#вопрос Как составить оферту?")
                .build();

        when(dailyReportRepository.findByStudentIdAndReportDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        DailyReport result = handler.handleStudentMessage(student, message);

        assertThat(result.getMailingsCount()).isEqualTo(25);
        assertThat(result.getSalesTotal()).isEqualByComparingTo(new BigDecimal("35500.00"));
        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getMentorQuestion()).contains("Как составить оферту?");

        // Student stats updated
        assertThat(student.getTotalMailings()).isEqualTo(125);
        assertThat(student.getCurrentIncome()).isEqualByComparingTo(new BigDecimal("215500.00"));

        // Caring acknowledgment reply sent
        assertThat(sentChatId).isEqualTo(-10071010101L);
        assertThat(sentReplyToId).isEqualTo(501);
        assertThat(sentReplyText)
                .contains("Принято на проверку Ксении и Кате (")
                .contains(")! ✍️ Черновик сохранён.");
        assertThat(result.getLastTgMessageId()).isEqualTo(501);
    }

    @Test
    @DisplayName("Accumulator upsert accumulates second message on same day and concatenates questions")
    void handleStudentMessage_accumulatesSecondMessage() {
        Student student = Student.builder()
                .id(1L)
                .fullName("Милана Горохова")
                .tgChatId(71010101L)
                .currentIncome(new BigDecimal("215500.00"))
                .totalMailings(125)
                .build();

        DailyReport existingReport = DailyReport.builder()
                .id(10L)
                .student(student)
                .reportDate(LocalDate.now())
                .mailingsCount(25)
                .salesTotal(new BigDecimal("35500.00"))
                .mentorQuestion("[14:15] Как составить оферту?")
                .status("PENDING")
                .contentDrafts(new ArrayList<>())
                .reelSubmissions(new ArrayList<>())
                .salesEntries(new ArrayList<>())
                .build();

        when(dailyReportRepository.findByStudentIdAndReportDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(existingReport));

        Chat chat = Chat.builder().id(-10071010101L).type("supergroup").build();
        Message message = Message.builder()
                .messageId(502)
                .chat(chat)
                .text("#рассылки 10\n#продажи 80к\n#вопрос Клиентка хочет скидку")
                .build();

        DailyReport result = handler.handleStudentMessage(student, message);

        // Summed up values
        assertThat(result.getMailingsCount()).isEqualTo(35);
        assertThat(result.getSalesTotal()).isEqualByComparingTo(new BigDecimal("115500.00"));

        // Concatenated questions
        assertThat(result.getMentorQuestion())
                .contains("[14:15] Как составить оферту?")
                .contains("Клиентка хочет скидку");

        // Student stats updated
        assertThat(student.getTotalMailings()).isEqualTo(135);
        assertThat(student.getCurrentIncome()).isEqualByComparingTo(new BigDecimal("295500.00"));
    }

    @Test
    @DisplayName("General chat conversation is scanned silently WITHOUT sending acknowledgment spam")
    void handleStudentMessage_chitChatScannedSilently() {
        Student student = Student.builder()
                .id(1L)
                .fullName("Милана Горохова")
                .tgChatId(71010101L)
                .build();

        DailyReport existingReport = DailyReport.builder()
                .id(10L)
                .student(student)
                .reportDate(LocalDate.now())
                .status("APPROVED")
                .contentDrafts(new ArrayList<>())
                .reelSubmissions(new ArrayList<>())
                .salesEntries(new ArrayList<>())
                .build();

        when(dailyReportRepository.findByStudentIdAndReportDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(existingReport));

        sentReplyText = null;

        Chat chat = Chat.builder().id(-10071010101L).type("supergroup").build();
        Message message = Message.builder()
                .messageId(503)
                .chat(chat)
                .text("Привет, девочки! Как настроение?")
                .build();

        DailyReport result = handler.handleStudentMessage(student, message);

        // Crucial: no caring reply sent on general chat messages
        assertThat(sentReplyText).isNull();

        // Status is NOT changed to PENDING (remains APPROVED)
        assertThat(result.getStatus()).isEqualTo("APPROVED");

        // Raw message text contains the conversation
        assertThat(result.getRawMessageText()).contains("Привет, девочки! Как настроение?");
    }

    @Test
    @DisplayName("Morning focus text message records goal and sends caring confirmation")
    void handleMorningFocusMessage_savesFocusAndConfirms() {
        Student student = Student.builder()
                .id(1L)
                .fullName("Милана Горохова")
                .tgChatId(71010101L)
                .build();

        when(dailyReportRepository.findByStudentIdAndReportDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        sentReplyText = null;

        Chat chat = Chat.builder().id(-10071010101L).type("supergroup").build();
        Message message = Message.builder()
                .messageId(504)
                .chat(chat)
                .text("Сегодня я буду делать рилсы")
                .build();

        DailyReport result = handler.handleMorningFocusMessage(student, message, "Сегодня я буду делать рилсы");

        assertThat(result.getReflectionActions()).contains("🎯 Фокус дня: Сегодня я буду делать рилсы");
        assertThat(result.getAiSummary()).contains("Фокус на сегодня: Сегодня я буду делать рилсы");

        // Sent custom focus confirmation instead of generic draft acknowledgment
        assertThat(sentReplyText).contains("🎯 Цель на сегодня зафиксирована: «Сегодня я буду делать рилсы»!");
    }
}
