package com.mentorhub.scheduler;

import com.mentorhub.entity.DailyReport;
import com.mentorhub.entity.Student;
import com.mentorhub.entity.MentorSetting;
import com.mentorhub.repository.DailyReportRepository;
import com.mentorhub.repository.MentorSettingRepository;
import com.mentorhub.repository.StudentRepository;
import com.mentorhub.service.StudentService;
import com.mentorhub.telegram.service.TelegramSenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyReminderScheduler {

    private static final ZoneId MINSK_ZONE = ZoneId.of("Europe/Minsk");

    private final StudentRepository studentRepository;
    private final DailyReportRepository dailyReportRepository;
    private final MentorSettingRepository mentorSettingRepository;
    private final TelegramSenderService telegramSenderService;

    @Autowired(required = false)
    private StudentService studentService;

    @Value("${telegram.reminder.rate-limit-ms:1000}")
    private long rateLimitMs = 1000L;

    @Scheduled(cron = "0 * * * * *", zone = "Europe/Minsk")
    public void pollConfiguredReminders() {
        MentorSetting settings = mentorSettingRepository.findAll().stream().findFirst().orElse(null);
        if (settings == null) return;
        boolean sundayRest = Boolean.TRUE.equals(settings.getSundayRestEnabled());

        List<Student> activeStudents = studentRepository.findByIsActiveTrue().stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsPaused()) && s.getTgChatId() != null)
                .toList();

        for (Student student : activeStudents) {
            ZoneId zone = resolveStudentZone(student);
            LocalDateTime localNow = LocalDateTime.now(zone).truncatedTo(ChronoUnit.MINUTES);
            LocalTime localTime = localNow.toLocalTime();
            LocalDate localDate = localNow.toLocalDate();

            if (localDate.getDayOfWeek() == DayOfWeek.SUNDAY && sundayRest) {
                continue;
            }

            if (Boolean.TRUE.equals(settings.getMorningPushEnabled())
                    && localTime.equals(settings.getMorningPushTime())) {
                sendMorningFocusToSingleStudent(student, settings, localDate);
            }

            if (Boolean.TRUE.equals(settings.getEveningReminderEnabled())
                    && Boolean.TRUE.equals(settings.getAutoRemindersEnabled())
                    && localTime.equals(settings.getEveningReminderTime())) {
                sendEveningReminderIfDebtor(student, settings, localDate);
            }
        }
    }

    public ZoneId resolveStudentZone(Student student) {
        if (student != null && StringUtils.hasText(student.getTimezone())) {
            try {
                return ZoneId.of(student.getTimezone());
            } catch (Exception ex) {
                log.warn("Invalid timezone '{}' for student #{}, defaulting to Europe/Minsk", student.getTimezone(), student.getId());
            }
        }
        return MINSK_ZONE;
    }

    public void executeDailyReminderJob() {
        executeDailyReminderJob(LocalDate.now(MINSK_ZONE), true);
    }

    private void executeDailyReminderJob(LocalDate today, boolean sundayRest) {
        log.info("Starting DailyReminderScheduler job for date {}", today);

        // Sunday is official rest day for the cohort
        if (today.getDayOfWeek() == DayOfWeek.SUNDAY && sundayRest) {
            log.info("Skipping daily reminder job: Sunday is official day off");
            return;
        }

        List<Student> debtors = findDebtors(today, sundayRest);
        log.info("Found {} debtor student(s) without report for {}", debtors.size(), today);

        for (Student student : debtors) {
            try {
                sendReminderToStudent(student);

                if (rateLimitMs > 0) {
                    Thread.sleep(rateLimitMs);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Reminder loop interrupted");
                break;
            } catch (Exception ex) {
                log.error("Failed to send evening reminder to student '{}' (#{}), chat {}: {}",
                        student.getFullName(), student.getId(), student.getTgChatId(), ex.getMessage(), ex);
            }
        }
    }

    public List<Student> findDebtors(LocalDate date) {
        return findDebtors(date, true);
    }

    private List<Student> findDebtors(LocalDate date, boolean sundayRest) {
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY && sundayRest) {
            return List.of();
        }

        List<Student> activeStudents = studentRepository.findByIsActiveTrue().stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsPaused()))
                .toList();

        if (activeStudents.isEmpty()) {
            return List.of();
        }

        java.util.List<DailyReport> batchReports = dailyReportRepository.findByReportDate(date);
        java.util.Set<Long> submittedStudentIds;
        if (batchReports != null && !batchReports.isEmpty()) {
            submittedStudentIds = batchReports.stream()
                    .filter(r -> "PENDING".equalsIgnoreCase(r.getStatus()) || "APPROVED".equalsIgnoreCase(r.getStatus()))
                    .filter(r -> r.getStudent() != null && r.getStudent().getId() != null)
                    .map(r -> r.getStudent().getId())
                    .collect(java.util.stream.Collectors.toSet());
        } else {
            // Fallback for isolated mocks or environments where findByReportDate is not populated
            submittedStudentIds = activeStudents.stream()
                    .filter(s -> dailyReportRepository.findByStudentIdAndReportDate(s.getId(), date)
                            .map(r -> "PENDING".equalsIgnoreCase(r.getStatus()) || "APPROVED".equalsIgnoreCase(r.getStatus()))
                            .orElse(false))
                    .map(Student::getId)
                    .collect(java.util.stream.Collectors.toSet());
        }

        return activeStudents.stream()
                .filter(s -> !submittedStudentIds.contains(s.getId()))
                .toList();
    }

    public boolean sendReminderToStudent(Student student) {
        return sendReminderToStudent(student, LocalDate.now(resolveStudentZone(student)));
    }

    public boolean sendReminderToStudent(Student student, LocalDate targetDate) {
        if (student == null || student.getTgChatId() == null) {
            log.warn("Cannot send reminder: student or tg_chat_id is null");
            return false;
        }

        MentorSetting settings = mentorSettingRepository.findAll().stream().findFirst().orElse(null);
        String deadline = settings != null && settings.getDeadlineTime() != null ? settings.getDeadlineTime().toString() : "21:00";
        LocalDate today = targetDate != null ? targetDate : LocalDate.now(resolveStudentZone(student));
        String todayTask = studentService != null ? studentService.getTodayTask(student, today) : null;

        String messageText;
        if (StringUtils.hasText(todayTask)) {
            messageText = String.format(
                    "%s, привет! Напоминаю: до дедлайна сдачи отчёта остался 1 час (до %s) ⏰\n\n" +
                    "Твоя задача на сегодня: %s\n\n" +
                    "Ждём твои материалы и отчёт 🌸",
                    student.getFullName(), deadline, todayTask
            );
        } else {
            messageText = String.format(
                    "%s, привет! Напоминаю: до дедлайна сдачи отчета остался 1 час (до %s) ⏰ " +
                    "Ждем твои черновики #контента (#рилс, #карусель, #сторителл), результаты по #рассылкам и #продажам!",
                    student.getFullName(), deadline
            );
        }

        String businessConnectionId = settings != null && Boolean.TRUE.equals(settings.getIsBusinessConnected())
                ? settings.getBusinessConnectionId()
                : null;
        return businessConnectionId == null
                ? telegramSenderService.sendReminder(student.getTgChatId(), messageText)
                : telegramSenderService.sendStudentMessage(student.getTgChatId(), businessConnectionId, messageText);
    }

    public void sendMorningFocusToStudents(MentorSetting settings) {
        for (Student student : studentRepository.findByIsActiveTrue()) {
            if (Boolean.TRUE.equals(student.getIsPaused()) || student.getTgChatId() == null) continue;
            LocalDate studentDate = LocalDate.now(resolveStudentZone(student));
            sendMorningFocusToSingleStudent(student, settings, studentDate);

            if (rateLimitMs > 0) {
                try {
                    Thread.sleep(rateLimitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Morning focus loop interrupted");
                    break;
                }
            }
        }
    }

    public void sendMorningFocusToSingleStudent(Student student, MentorSetting settings, LocalDate studentDate) {
        String businessConnectionId = settings != null && Boolean.TRUE.equals(settings.getIsBusinessConnected())
                ? settings.getBusinessConnectionId()
                : null;
        String deadlineStr = settings == null || settings.getDeadlineTime() == null ? "21:00" : settings.getDeadlineTime().toString();

        String todayTask = studentService != null ? studentService.getTodayTask(student, studentDate) : null;
        int currentDay = studentService != null ? studentService.getCurrentDayNumber(student, studentDate) : 1;

        if (StringUtils.hasText(todayTask)) {
            String text = String.format(
                    "🌸 %s, доброе утро!\n\n" +
                    "Твой персональный фокус на сегодня (День %d):\n" +
                    "🎯 %s\n\n" +
                    "Не забывай сдать отчёт до %s ❤️",
                    student.getFullName(), currentDay, todayTask, deadlineStr
            );
            recordDailyFocus(student, studentDate, todayTask);
            telegramSenderService.sendCaringReply(student.getTgChatId(), null, text, businessConnectionId);
        } else {
            String text = String.format(
                    "🌸 %s, доброе утро!\n\n" +
                    "Продуктивного дня! Не забывай сдать отчёт до %s ❤️",
                    student.getFullName(), deadlineStr
            );
            telegramSenderService.sendCaringReply(student.getTgChatId(), null, text, businessConnectionId);
        }
    }

    public void sendEveningReminderIfDebtor(Student student, MentorSetting settings, LocalDate studentDate) {
        boolean hasSubmitted = dailyReportRepository.findByStudentIdAndReportDate(student.getId(), studentDate)
                .map(r -> "PENDING".equalsIgnoreCase(r.getStatus()) || "APPROVED".equalsIgnoreCase(r.getStatus()))
                .orElse(false);
        if (!hasSubmitted) {
            sendReminderToStudent(student, studentDate);
        }
    }

    private void recordDailyFocus(Student student, LocalDate today, String todayTask) {
        try {
            DailyReport report = dailyReportRepository.findByStudentIdAndReportDate(student.getId(), today)
                    .orElseGet(() -> DailyReport.builder()
                            .student(student)
                            .reportDate(today)
                            .submittedAt(Instant.now())
                            .isOnTime(true)
                            .status("PENDING")
                            .mailingsCount(0)
                            .salesTotal(BigDecimal.ZERO)
                            .newClientsCount(0)
                            .contentDrafts(new ArrayList<>())
                            .reelSubmissions(new ArrayList<>())
                            .salesEntries(new ArrayList<>())
                            .build());
            if (!StringUtils.hasText(report.getReflectionActions())) {
                report.setReflectionActions("🎯 Фокус дня: " + todayTask);
                dailyReportRepository.save(report);
            }
        } catch (Exception ex) {
            log.warn("Could not auto-record morning focus in daily report for student #{}: {}", student.getId(), ex.getMessage());
        }
    }
}
