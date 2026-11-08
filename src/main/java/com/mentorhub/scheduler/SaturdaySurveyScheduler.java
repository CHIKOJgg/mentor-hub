package com.mentorhub.scheduler;

import com.mentorhub.entity.Student;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class SaturdaySurveyScheduler {

    private static final ZoneId MINSK_ZONE = ZoneId.of("Europe/Minsk");

    private final StudentRepository studentRepository;
    private final TelegramSenderService telegramSenderService;

    @Value("${telegram.poll.rate-limit-ms:1000}")
    private long rateLimitMs = 1000L;

    @Scheduled(cron = "${telegram.poll.cron:0 0 18 * * SAT}", zone = "${telegram.poll.zone:Europe/Minsk}")
    public void executeSaturdaySurveyJob() {
        LocalDate today = LocalDate.now(MINSK_ZONE);
        log.info("Starting SaturdaySurveyScheduler job for date {}", today);

        if (today.getDayOfWeek() != DayOfWeek.SATURDAY) {
            log.info("Skipping Saturday survey job: today is not Saturday ({})", today.getDayOfWeek());
            return;
        }

        List<Student> students = getEligibleStudents();
        log.info("Found {} eligible student(s) for Saturday weekly survey", students.size());

        for (Student student : students) {
            try {
                sendSurveyToStudent(student);

                if (rateLimitMs > 0) {
                    Thread.sleep(rateLimitMs);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Saturday survey loop interrupted");
                break;
            } catch (Exception ex) {
                log.error("Failed to send Saturday survey to student '{}' (#{}), chat {}: {}",
                        student.getFullName(), student.getId(), student.getTgChatId(), ex.getMessage(), ex);
            }
        }
    }

    public List<Student> getEligibleStudents() {
        return studentRepository.findByIsActiveTrue().stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsPaused()))
                .filter(s -> s.getTgChatId() != null)
                .toList();
    }

    public boolean sendSurveyToStudent(Student student) {
        if (student == null || student.getTgChatId() == null) {
            log.warn("Cannot send survey: student or tg_chat_id is null");
            return false;
        }

        String surveyText = "Субботний чекап недели! 📊\nСколько новых клиентов ты закрыла за эту неделю?";
        return telegramSenderService.sendSaturdaySurveyPoll(student.getTgChatId(), student.getId(), surveyText);
    }
}
