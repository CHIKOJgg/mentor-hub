package com.mentorhub.scheduler;

import com.mentorhub.entity.DailyReport;
import com.mentorhub.entity.Student;
import com.mentorhub.repository.DailyReportRepository;
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

    private Long reminderChatId;
    private String reminderText;

    private TelegramSenderService stubSenderService;
    private DailyReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        studentRepository = mock(StudentRepository.class);
        dailyReportRepository = mock(DailyReportRepository.class);

        stubSenderService = new TelegramSenderService(null) {
            @Override
            public boolean sendReminder(Long chatId, String text) {
                reminderChatId = chatId;
                reminderText = text;
                return true;
            }
        };

        scheduler = new DailyReminderScheduler(studentRepository, dailyReportRepository, stubSenderService);
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

        // submittedStudent has report with status PENDING
        when(dailyReportRepository.findByStudentIdAndReportDate(3L, monday)).thenReturn(Optional.of(
                DailyReport.builder().status("PENDING").build()
        ));

        List<Student> debtors = scheduler.findDebtors(monday);

        // Only activeDebtor should be selected!
        assertThat(debtors).hasSize(1);
        assertThat(debtors.get(0).getId()).isEqualTo(1L);
        assertThat(debtors.get(0).getFullName()).isEqualTo("Милана Горохова");
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
}
