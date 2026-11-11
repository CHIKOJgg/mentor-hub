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
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackQueryHandler {

    private static final Pattern SURVEY_PATTERN = Pattern.compile("^survey:clients:(\\d+):(\\d+)$");
    private static final Pattern FOCUS_PATTERN = Pattern.compile("^focus:([a-zA-Z0-9_]+):(\\d+)$");
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

        Matcher surveyMatcher = SURVEY_PATTERN.matcher(data);
        if (surveyMatcher.matches()) {
            return handleSurveyCallback(callbackQuery, surveyMatcher);
        }

        Matcher focusMatcher = FOCUS_PATTERN.matcher(data);
        if (focusMatcher.matches()) {
            String focusKey = focusMatcher.group(1);
            long studentId = Long.parseLong(focusMatcher.group(2));
            return handleMorningFocusCallback(callbackQuery, studentId, focusKey);
        }

        log.debug("Callback query data '{}' does not match any recognized pattern", data);
        return false;
    }

    private boolean handleSurveyCallback(CallbackQuery callbackQuery, Matcher matcher) {
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

    private boolean handleMorningFocusCallback(CallbackQuery callbackQuery, long studentId, String focusKey) {
        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            log.warn("Student with ID #{} not found for morning focus callback", studentId);
            telegramSenderService.answerCallbackQuery(callbackQuery.getId(), "Ошибка: ученица не найдена");
            return false;
        }

        String focusTitle;
        switch (focusKey.toLowerCase()) {
            case "reels" -> focusTitle = "🎬 Снять и выложить Reels";
            case "outreach" -> focusTitle = "✉️ Сделать 20+ рассылок в Direct";
            case "offer" -> focusTitle = "💎 Упаковка профиля / новый оффер";
            case "sales" -> focusTitle = "💰 Продажи и созвоны с клиентами";
            default -> focusTitle = focusKey;
        }

        LocalDate today = LocalDate.now(MINSK_ZONE);

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
                        .build()
                );

        report.setReflectionActions("🎯 Фокус дня: " + focusTitle);
        if (!StringUtils.hasText(report.getAiSummary())) {
            report.setAiSummary("Фокус на сегодня: " + focusTitle);
        }
        String existingRaw = report.getRawMessageText();
        report.setRawMessageText(StringUtils.hasText(existingRaw)
                ? existingRaw + "\n[Фокус дня]: " + focusTitle
                : "[Фокус дня]: " + focusTitle);

        dailyReportRepository.save(report);

        log.info("Recorded morning focus for student '{}' (#{}): {}",
                student.getFullName(), student.getId(), focusTitle);

        String ackToast = "Фокус дня выбран: " + focusTitle + " 🎯";
        telegramSenderService.answerCallbackQuery(callbackQuery.getId(), ackToast);

        Long chatId = null;
        Integer messageId = null;
        if (callbackQuery.getMessage() != null) {
            chatId = callbackQuery.getMessage().getChatId();
            messageId = callbackQuery.getMessage().getMessageId();
        }

        if (chatId != null && messageId != null) {
            String updatedText = "Доброе утро, " + student.getFullName() + "! 🌸\n\n" +
                    "🎯 Твой главный фокус на сегодня зафиксирован:\n«" + focusTitle + "»\n\n" +
                    "⏰ Ждём отчёт о выполнении до 21:00. Продуктивного дня!";
            telegramSenderService.editMessageText(chatId, messageId, updatedText);
        }

        return true;
    }
}
