package com.mentorhub.scheduler;

import com.mentorhub.entity.DailyReport;
import com.mentorhub.entity.Student;
import com.mentorhub.entity.MentorSetting;
import com.mentorhub.repository.DailyReportRepository;
import com.mentorhub.repository.MentorSettingRepository;
import com.mentorhub.repository.StudentRepository;
import com.mentorhub.telegram.service.TelegramSenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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

    @Value("${telegram.reminder.rate-limit-ms:1000}")
    private long rateLimitMs = 1000L;

    @Scheduled(cron = "0 * * * * *", zone = "Europe/Minsk")
    public void pollConfiguredReminders() {
        LocalDateTime now = LocalDateTime.now(MINSK_ZONE).truncatedTo(ChronoUnit.MINUTES);
        MentorSetting settings = mentorSettingRepository.findAll().stream().findFirst().orElse(null);
        if (settings == null) return;
        boolean sundayRest = Boolean.TRUE.equals(settings.getSundayRestEnabled());
        if (now.getDayOfWeek() == DayOfWeek.SUNDAY && sundayRest) return;

        LocalTime currentTime = now.toLocalTime();
        if (Boolean.TRUE.equals(settings.getMorningPushEnabled()) && currentTime.equals(settings.getMorningPushTime())) {
            sendMorningFocusToStudents(settings);
        }
        if (Boolean.TRUE.equals(settings.getEveningReminderEnabled())
                && Boolean.TRUE.equals(settings.getAutoRemindersEnabled())
                && currentTime.equals(settings.getEveningReminderTime())) {
            executeDailyReminderJob(now.toLocalDate(), sundayRest);
        }
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
        if (student == null || student.getTgChatId() == null) {
            log.warn("Cannot send reminder: student or tg_chat_id is null");
            return false;
        }

        MentorSetting settings = mentorSettingRepository.findAll().stream().findFirst().orElse(null);
        String deadline = settings != null && settings.getDeadlineTime() != null ? settings.getDeadlineTime().toString() : "21:00";
        String messageText = String.format(
                "%s, привет! Напоминаю: до дедлайна сдачи отчета остался 1 час (до %s) ⏰ " +
                "Ждем твои черновики #контента (#рилс, #карусель, #сторителл), результаты по #рассылкам и #продажам!",
                student.getFullName(), deadline
        );

        String businessConnectionId = settings != null && Boolean.TRUE.equals(settings.getIsBusinessConnected())
                ? settings.getBusinessConnectionId()
                : null;
        return businessConnectionId == null
                ? telegramSenderService.sendReminder(student.getTgChatId(), messageText)
                : telegramSenderService.sendStudentMessage(student.getTgChatId(), businessConnectionId, messageText);
    }

    private void sendMorningFocusToStudents(MentorSetting settings) {
        String businessConnectionId = Boolean.TRUE.equals(settings.getIsBusinessConnected())
                ? settings.getBusinessConnectionId()
                : null;
        for (Student student : studentRepository.findByIsActiveTrue()) {
            if (Boolean.TRUE.equals(student.getIsPaused()) || student.getTgChatId() == null) continue;
            String text = student.getFullName() + ", доброе утро. Выберите один главный результат на сегодня и не забывайте отметить отчет до "
                    + (settings.getDeadlineTime() == null ? "21:00" : settings.getDeadlineTime()) + ".";
            telegramSenderService.sendStudentMessage(student.getTgChatId(), businessConnectionId, text);
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
}
