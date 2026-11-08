package com.mentorhub.scheduler;

import com.mentorhub.entity.DailyReport;
import com.mentorhub.entity.Student;
import com.mentorhub.repository.DailyReportRepository;
import com.mentorhub.repository.StudentRepository;
import com.mentorhub.telegram.service.TelegramSenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackQueryHandler {

    private static final Pattern SURVEY_PATTERN = Pattern.compile("^survey:clients:(\\d+):(\\d+)$");
    private static final ZoneId MINSK_ZONE = ZoneId.of("Europe/Minsk");

    private final StudentRepository studentRepository;
    private final DailyReportRepository dailyReportRepository;
    private final TelegramSenderService telegramSenderService;

    @Transactional
    public boolean handleCallbackQuery(CallbackQuery callbackQuery) {
        if (callbackQuery == null || callbackQuery.getData() == null) {
            return false;
        }

        String data = callbackQuery.getData();
        Matcher matcher = SURVEY_PATTERN.matcher(data);
        if (!matcher.matches()) {
            log.debug("Callback query data '{}' does not match Saturday survey pattern", data);
            return false;
        }

        long studentId = Long.parseLong(matcher.group(1));
        int count = Integer.parseInt(matcher.group(2));

        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            log.warn("Student with ID #{} not found for Saturday survey callback", studentId);
            telegramSenderService.answerCallbackQuery(callbackQuery.getId(), "Ошибка: ученица не найдена");
            return false;
        }

        LocalDate today = LocalDate.now(MINSK_ZONE);

        DailyReport report = dailyReportRepository.findByStudentIdAndReportDate(student.getId(), today)
                .orElseGet(() -> DailyReport.builder()
                        .student(student)
                        .reportDate(today)
                        .submittedAt(Instant.now())
                        .isOnTime(true)
                        .status("PENDING")
                        .build()
                );

        report.setNewClientsCount(count);
        report.setSubmittedAt(Instant.now());
        dailyReportRepository.save(report);

        int currentTotal = student.getTotalNewClients() != null ? student.getTotalNewClients() : 0;
        student.setTotalNewClients(currentTotal + count);
        studentRepository.save(student);

        log.info("Recorded Saturday survey response for student '{}' (#{}): +{} new clients. Total: {}",
                student.getFullName(), student.getId(), count, student.getTotalNewClients());

        String ackText = "Зафиксировано: +" + count + " клиентов! 🎉";
        telegramSenderService.answerCallbackQuery(callbackQuery.getId(), ackText);

        Long chatId = null;
        Integer messageId = null;
        if (callbackQuery.getMessage() != null) {
            chatId = callbackQuery.getMessage().getChatId();
            messageId = callbackQuery.getMessage().getMessageId();
        }

        if (chatId != null && messageId != null) {
            String updatedText = "Спасибо! Зафиксировано новых клиентов за неделю: +" + count +
                    " 🚀\nУкажи сумму продаж недели через #продажи";
            telegramSenderService.editMessageText(chatId, messageId, updatedText);
        }

        return true;
    }
}
