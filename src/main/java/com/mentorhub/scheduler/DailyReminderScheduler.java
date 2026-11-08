package com.mentorhub.scheduler;

import com.mentorhub.entity.DailyReport;
import com.mentorhub.entity.Student;
import com.mentorhub.repository.DailyReportRepository;
import com.mentorhub.repository.StudentRepository;
import com.mentorhub.telegram.service.TelegramSenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyReminderScheduler {

    private static final ZoneId MINSK_ZONE = ZoneId.of("Europe/Minsk");

    private final StudentRepository studentRepository;
    private final DailyReportRepository dailyReportRepository;
    private final TelegramSenderService telegramSenderService;

    @Value("${telegram.reminder.rate-limit-ms:1000}")
    private long rateLimitMs = 1000L;

    @Scheduled(cron = "${telegram.reminder.cron:0 0 20 * * MON-SAT}", zone = "${telegram.reminder.zone:Europe/Minsk}")
    public void executeDailyReminderJob() {
        LocalDate today = LocalDate.now(MINSK_ZONE);
        log.info("Starting DailyReminderScheduler job for date {}", today);

        // Sunday is official rest day for the cohort
        if (today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            log.info("Skipping daily reminder job: Sunday is official day off");
            return;
        }

        List<Student> debtors = findDebtors(today);
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
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return List.of();
        }

        return studentRepository.findByIsActiveTrue().stream()
                // Strictly exclude sick/paused students
                .filter(s -> !Boolean.TRUE.equals(s.getIsPaused()))
                // Exclude students who already submitted a report today
                .filter(s -> {
                    Optional<DailyReport> reportOpt = dailyReportRepository.findByStudentIdAndReportDate(s.getId(), date);
                    if (reportOpt.isEmpty()) {
                        return true;
                    }
                    String status = reportOpt.get().getStatus();
                    return !"PENDING".equalsIgnoreCase(status) && !"APPROVED".equalsIgnoreCase(status);
                })
                .toList();
    }

    public boolean sendReminderToStudent(Student student) {
        if (student == null || student.getTgChatId() == null) {
            log.warn("Cannot send reminder: student or tg_chat_id is null");
            return false;
        }

        String messageText = String.format(
                "%s, привет! Напоминаю: до дедлайна сдачи отчета остался 1 час (до 21:00) ⏰ " +
                "Ждем твои черновики #контента (#рилс, #карусель, #сторителл), результаты по #рассылкам и #продажам!",
                student.getFullName()
        );

        return telegramSenderService.sendReminder(student.getTgChatId(), messageText);
    }
}
