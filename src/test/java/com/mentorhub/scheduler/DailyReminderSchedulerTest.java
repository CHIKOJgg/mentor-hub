package com.mentorhub.scheduler;

import com.mentorhub.entity.DailyReport;
import com.mentorhub.entity.MentorSetting;
import com.mentorhub.entity.Student;
import com.mentorhub.repository.DailyReportRepository;
import com.mentorhub.repository.MentorSettingRepository;
import com.mentorhub.repository.StudentRepository;
import com.mentorhub.telegram.service.TelegramSenderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DailyReminderSchedulerTest {

    private StudentRepository studentRepository;
    private DailyReportRepository dailyReportRepository;
    private MentorSettingRepository mentorSettingRepository;

    private Long reminderChatId;
    private String reminderText;
    private Long caringReplyChatId;
    private String caringReplyText;

    private TelegramSenderService stubSenderService;
    private DailyReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        studentRepository = mock(StudentRepository.class);
        dailyReportRepository = mock(DailyReportRepository.class);
        mentorSettingRepository = mock(MentorSettingRepository.class);

        stubSenderService = new TelegramSenderService(null) {
            @Override
            public boolean sendReminder(Long chatId, String text) {
                reminderChatId = chatId;
                reminderText = text;
                return true;
            }

            @Override
            public boolean sendCaringReply(Long chatId, Integer replyToMessageId, String text, String businessConnectionId) {
                caringReplyChatId = chatId;
                caringReplyText = text;
                return true;
            }
        };

        scheduler = new DailyReminderScheduler(studentRepository, dailyReportRepository, mentorSettingRepository, stubSenderService);
        ReflectionTestUtils.setField(scheduler, "rateLimitMs", 0L);
    }

    @Test
    @DisplayName("Sunday is official day off: findDebtors returns empty list")
    void findDebtors_sundayIsOff() {
        LocalDate sunday = LocalDate.of(2026, 11, 8); // Sunday
        assertThat(sunday.getDayOfWeek().getValue()).isEqualTo(7);

        List<Student> debtors = scheduler.findDebtors(sunday);
        assertThat(debtors).isEmpty();
        verifyNoInteractions(studentRepository);
    }

    @Test
    @DisplayName("Strictly excludes sick/paused students and students who already submitted reports")
    void findDebtors_filtersPausedAndSubmitted() {
        LocalDate monday = LocalDate.of(2026, 11, 9); // Monday

        Student activeDebtor = Student.builder()
                .id(1L)
                .fullName("Милана Горохова")
                .tgChatId(71010101L)
                .isActive(true)
                .isPaused(false)
                .build();

        Student pausedStudent = Student.builder()
                .id(2L)
                .fullName("Виктория Лебедева")
                .tgChatId(71040404L)
                .isActive(true)
                .isPaused(true)
                .pauseReason("Болеет ОРВИ")
                .build();

        Student submittedStudent = Student.builder()
                .id(3L)
                .fullName("Аня Петрова")
                .tgChatId(71020202L)
                .isActive(true)
                .isPaused(false)
                .build();

        when(studentRepository.findByIsActiveTrue()).thenReturn(List.of(activeDebtor, pausedStudent, submittedStudent));

        // activeDebtor has no report
        when(dailyReportRepository.findByStudentIdAndReportDate(1L, monday)).thenReturn(Optional.empty());

        // submittedStudent has report with actual submission
        when(dailyReportRepository.findByStudentIdAndReportDate(3L, monday)).thenReturn(Optional.of(
                DailyReport.builder().status("PENDING").lastTgMessageId(101).mailingsCount(15).build()
        ));

        List<Student> debtors = scheduler.findDebtors(monday);

        // Only activeDebtor should be selected!
        assertThat(debtors).hasSize(1);
        assertThat(debtors.get(0).getId()).isEqualTo(1L);
        assertThat(debtors.get(0).getFullName()).isEqualTo("Милана Горохова");
    }

    @Test
    @DisplayName("findDebtors includes student who received morning focus but did not submit homework")
    void findDebtors_includesMorningFocusOnlyStudent() {
        LocalDate tuesday = LocalDate.of(2026, 11, 17);
        Student focusOnlyStudent = Student.builder()
                .id(5L)
                .fullName("Лиза")
                .tgChatId(71050505L)
                .isActive(true)
                .isPaused(false)
                .build();

        when(studentRepository.findByIsActiveTrue()).thenReturn(List.of(focusOnlyStudent));
        // Report created by morning focus has reflectionActions but 0 submissions and no lastTgMessageId
        when(dailyReportRepository.findByStudentIdAndReportDate(5L, tuesday)).thenReturn(Optional.of(
                DailyReport.builder()
                        .status("PENDING")
                        .reflectionActions("🎯 Фокус дня: Продажи и созвоны")
                        .mailingsCount(0)
                        .salesTotal(java.math.BigDecimal.ZERO)
                        .newClientsCount(0)
                        .build()
        ));

        List<Student> debtors = scheduler.findDebtors(tuesday);
        assertThat(debtors).hasSize(1);
        assertThat(debtors.get(0).getId()).isEqualTo(5L);
        assertThat(debtors.get(0).getFullName()).isEqualTo("Лиза");
    }

    @Test
    @DisplayName("sendReminderToStudent sends caring 1-hour before deadline reminder text")
    void sendReminderToStudent_sendsProperText() {
        Student student = Student.builder()
                .id(1L)
                .fullName("Милана Горохова")
                .tgChatId(71010101L)
                .build();

        boolean sent = scheduler.sendReminderToStudent(student);

        assertThat(sent).isTrue();
        assertThat(reminderChatId).isEqualTo(71010101L);
        assertThat(reminderText).contains("Милана Горохова, привет! Напоминаю: до дедлайна сдачи отчета остался 1 час (до 21:00) ⏰");
    }

    @Test
    @DisplayName("Resolves student timezone with fallback to Europe/Minsk")
    void resolveStudentZone_test() {
        Student msk = Student.builder().timezone("Europe/Moscow").build();
        assertThat(scheduler.resolveStudentZone(msk).getId()).isEqualTo("Europe/Moscow");

        Student vlad = Student.builder().timezone("Asia/Vladivostok").build();
        assertThat(scheduler.resolveStudentZone(vlad).getId()).isEqualTo("Asia/Vladivostok");

        Student def = Student.builder().timezone(null).build();
        assertThat(scheduler.resolveStudentZone(def).getId()).isEqualTo("Europe/Minsk");

        Student invalid = Student.builder().timezone("Invalid/Timezone").build();
        assertThat(scheduler.resolveStudentZone(invalid).getId()).isEqualTo("Europe/Minsk");
    }

    @Test
    @DisplayName("sendMorningFocusToSingleStudent sends clean greeting without poll buttons when no plan set")
    void sendMorningFocus_noPlan_sendsCleanGreetingWithoutPoll() {
        Student student = Student.builder()
                .id(10L)
                .fullName("Лиза")
                .tgChatId(998877L)
                .build();

        MentorSetting settings = MentorSetting.builder()
                .deadlineTime(java.time.LocalTime.of(21, 0))
                .build();

        scheduler.sendMorningFocusToSingleStudent(student, settings, java.time.LocalDate.of(2026, 11, 17));

        assertThat(caringReplyChatId).isEqualTo(998877L);
        assertThat(caringReplyText).contains("Лиза, доброе утро!").contains("до 21:00");
        assertThat(caringReplyText).doesNotContain("кнопку");
    }

    @Test
    @DisplayName("Evening reminder IS sent if student only recorded morning focus but no actual report")
    void sendEveningReminderIfDebtor_withMorningFocusOnly_sendsReminder() {
        Student student = Student.builder().id(1L).fullName("Мария").tgChatId(71010101L).build();
        LocalDate today = LocalDate.of(2026, 11, 19);

        // Report exists but has only morning focus actions (hasActualSubmission == false)
        DailyReport morningOnlyReport = DailyReport.builder()
                .student(student)
                .reportDate(today)
                .status("PENDING")
                .reflectionActions("🎯 Фокус дня: 30 рассылок")
                .mailingsCount(0)
                .salesTotal(java.math.BigDecimal.ZERO)
                .build();

        when(dailyReportRepository.findByStudentIdAndReportDate(eq(1L), eq(today)))
                .thenReturn(Optional.of(morningOnlyReport));

        reminderChatId = null;
        reminderText = null;

        MentorSetting settings = MentorSetting.builder().deadlineTime(java.time.LocalTime.of(21, 0)).build();
        scheduler.sendEveningReminderIfDebtor(student, settings, today);

        // Reminder should be sent!
        assertThat(reminderChatId).isEqualTo(71010101L);
        assertThat(reminderText).contains("до дедлайна сдачи отчета остался 1 час");
    }

    @Test
    @DisplayName("Evening reminder is SKIPPED once student submits actual report")
    void sendEveningReminderIfDebtor_withActualReportSubmitted_skipsReminder() {
        Student student = Student.builder().id(1L).fullName("Мария").tgChatId(71010101L).build();
        LocalDate today = LocalDate.of(2026, 11, 19);

        // Report has actual mailings submitted (hasActualSubmission == true)
        DailyReport submittedReport = DailyReport.builder()
                .student(student)
                .reportDate(today)
                .status("PENDING")
                .mailingsCount(40)
                .salesTotal(java.math.BigDecimal.ZERO)
                .build();

        when(dailyReportRepository.findByStudentIdAndReportDate(eq(1L), eq(today)))
                .thenReturn(Optional.of(submittedReport));

        reminderChatId = null;
        reminderText = null;

        MentorSetting settings = MentorSetting.builder().deadlineTime(java.time.LocalTime.of(21, 0)).build();
        scheduler.sendEveningReminderIfDebtor(student, settings, today);

        // Reminder should be skipped!
        assertThat(reminderChatId).isNull();
        assertThat(reminderText).isNull();
    }
}
