package com.mentorhub.service;

import com.mentorhub.dto.*;
import com.mentorhub.entity.ContentDraft;
import com.mentorhub.entity.DailyReport;
import com.mentorhub.entity.MentorSetting;
import com.mentorhub.entity.ReelSubmission;
import com.mentorhub.repository.DailyReportRepository;
import com.mentorhub.repository.MentorSettingRepository;
import com.mentorhub.telegram.service.TelegramSenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final DailyReportRepository dailyReportRepository;
    private final MentorSettingRepository mentorSettingRepository;
    private final TelegramSenderService telegramSenderService;

    private static final ZoneId MINSK_ZONE = ZoneId.of("Europe/Minsk");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm").withZone(MINSK_ZONE);

    @Transactional(readOnly = true)
    public List<DailyReportResponse> getInboxQueue(LocalDate date, String status) {
        List<DailyReport> reports;

        String filterStatus = (status == null || status.isBlank()) ? "PENDING" : status.toUpperCase();

        if (date != null) {
            if ("ALL".equals(filterStatus)) {
                reports = dailyReportRepository.findByReportDateBetweenOrderByReportDateAsc(date, date);
            } else {
                reports = dailyReportRepository.findByReportDateAndStatus(date, filterStatus);
            }
        } else {
            if ("ALL".equals(filterStatus)) {
                reports = dailyReportRepository.findAll();
            } else {
                reports = dailyReportRepository.findByStatus(filterStatus);
            }
        }

        return reports.stream()
                .filter(r -> !"PENDING".equalsIgnoreCase(filterStatus) || r.hasActualSubmission())
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DailyReportResponse getReportById(Long id) {
        DailyReport report = dailyReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DailyReport not found with id: " + id));
        return mapToResponse(report);
    }

    @Transactional
    public ReportActionResponse approveReport(Long reportId, ApproveReportRequest request) {
        DailyReport report = dailyReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("DailyReport not found with id: " + reportId));

        if ("APPROVED".equalsIgnoreCase(report.getStatus())) {
            throw new IllegalStateException("Report " + reportId + " is already approved");
        }

        report.setStatus("APPROVED");
        Boolean messageDelivered = null;
        String replyText = request == null ? null : request.replyText();
        if (StringUtils.hasText(replyText)) {
            report.setMentorReply(replyText.trim());
            Long chatId = report.getStudent() == null ? null : report.getStudent().getTgChatId();
            MentorSetting setting = mentorSettingRepository.findAll().stream().findFirst().orElse(null);
            String businessConnectionId = setting != null && Boolean.TRUE.equals(setting.getIsBusinessConnected())
                    ? setting.getBusinessConnectionId()
                    : null;
            String fullReply = buildMentorReplyMessage("🌸 Ответ наставника Ксении на отчёт", report.getSubmittedAt(), replyText.trim());
            if (report.getLastTgMessageId() != null) {
                messageDelivered = telegramSenderService.sendCaringReply(chatId, report.getLastTgMessageId(), fullReply, businessConnectionId);
                if (!messageDelivered) {
                    messageDelivered = telegramSenderService.sendStudentMessage(chatId, businessConnectionId, fullReply);
                }
            } else {
                messageDelivered = telegramSenderService.sendStudentMessage(chatId, businessConnectionId, fullReply);
            }
            if (Boolean.TRUE.equals(messageDelivered)) report.setReplySentAt(Instant.now());
            else log.warn("Report #{} approved but Telegram reply was not delivered (chat={})", reportId, chatId);
        }
        dailyReportRepository.save(report);

        log.info("Report #{} approved by mentor. Telegram reply delivered: {}", reportId, messageDelivered);

        return new ReportActionResponse(
                true,
                String.valueOf(report.getId()),
                "APPROVED",
                messageDelivered,
                null,
                Instant.now(),
                null
        );
    }

    @Transactional
    public ReportActionResponse rejectReport(Long reportId, RejectReportRequest request) {
        DailyReport report = dailyReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("DailyReport not found with id: " + reportId));

        report.setStatus("REJECTED");
        Boolean feedbackSent = null;
        if (request != null && StringUtils.hasText(request.reason())) {
            report.setMentorReply(request.reason().trim());
            Long chatId = report.getStudent() == null ? null : report.getStudent().getTgChatId();
            MentorSetting setting = mentorSettingRepository.findAll().stream().findFirst().orElse(null);
            String businessConnectionId = setting != null && Boolean.TRUE.equals(setting.getIsBusinessConnected())
                    ? setting.getBusinessConnectionId()
                    : null;
            String fullReply = buildMentorReplyMessage("⚠️ На доработку от Ксении по отчёту", report.getSubmittedAt(), request.reason().trim());
            if (report.getLastTgMessageId() != null) {
                feedbackSent = telegramSenderService.sendCaringReply(chatId, report.getLastTgMessageId(), fullReply, businessConnectionId);
                if (!feedbackSent) {
                    feedbackSent = telegramSenderService.sendStudentMessage(chatId, businessConnectionId, fullReply);
                }
            } else {
                feedbackSent = telegramSenderService.sendStudentMessage(chatId, businessConnectionId, fullReply);
            }
            if (Boolean.TRUE.equals(feedbackSent)) report.setReplySentAt(Instant.now());
        }
        dailyReportRepository.save(report);

        log.info("Report #{} rejected by mentor. Telegram feedback delivered: {}", reportId, feedbackSent);

        return new ReportActionResponse(
                true,
                String.valueOf(report.getId()),
                "REJECTED",
                feedbackSent,
                feedbackSent,
                null,
                Instant.now()
        );
    }

    public DailyReportResponse mapToResponse(DailyReport report) {
        String studentId = report.getStudent() != null ? String.valueOf(report.getStudent().getId()) : "";
        String studentName = report.getStudent() != null ? report.getStudent().getFullName() : "";
        String avatarText = report.getStudent() != null ? report.getStudent().getAvatarText() : "";
        String tgUsername = report.getStudent() != null ? report.getStudent().getTgUsername() : "";
        String rawTariff = report.getStudent() != null ? report.getStudent().getTariff() : "STANDARD";
        String tariff = "VIP".equalsIgnoreCase(rawTariff) ? "VIP" : "Стандарт";

        String submittedAtStr = formatSubmittedAt(report.getSubmittedAt(), report.getReportDate(), report.getIsOnTime());

        List<ContentDraftDto> drafts = report.getContentDrafts() != null
                ? report.getContentDrafts().stream().map(this::mapDraftToDto).toList()
                : List.of();

        List<ReelDto> reels = report.getReelSubmissions() != null
                ? report.getReelSubmissions().stream().map(this::mapReelToDto).toList()
                : List.of();

        String question = cleanQuestionText(report.getReflectionQuestion());
        String mentorQuestion = cleanQuestionText(report.getMentorQuestion());
        String reflectionActions = cleanReflectionActions(report.getReflectionActions());

        ReflectionSummaryDto reflection = null;
        if (StringUtils.hasText(report.getReflectionEmotions()) ||
            StringUtils.hasText(reflectionActions) ||
            StringUtils.hasText(question)) {
            reflection = new ReflectionSummaryDto(
                    report.getReflectionEmotions() != null ? report.getReflectionEmotions() : "",
                    reflectionActions,
                    question
            );
        }

        String currency = report.getStudent() != null && StringUtils.hasText(report.getStudent().getCurrency())
                ? report.getStudent().getCurrency()
                : "BYN";

        return new DailyReportResponse(
                String.valueOf(report.getId()),
                studentId,
                studentName,
                avatarText,
                tgUsername,
                tariff,
                submittedAtStr,
                report.getReportDate(),
                report.getIsOnTime(),
                report.getStatus(),
                report.getMentorReply(),
                drafts,
                reels,
                report.getSalesTotal(),
                report.getMailingsCount() != null ? report.getMailingsCount() : 0,
                report.getNewClientsCount() != null ? report.getNewClientsCount() : 0,
                mentorQuestion,
                report.getVoiceTranscript(),
                reflection,
                report.getAiSummary() != null ? report.getAiSummary() : "",
                currency
        );
    }

    private String formatSubmittedAt(Instant submittedAt, LocalDate reportDate, Boolean isOnTime) {
        if (submittedAt == null) return "только что";
        String timeStr = TIME_FORMATTER.format(submittedAt);
        LocalDate today = LocalDate.now(MINSK_ZONE);
        String dateSuffix;
        if (reportDate == null || reportDate.equals(today)) {
            dateSuffix = " (сегодня)";
        } else if (reportDate.equals(today.minusDays(1))) {
            dateSuffix = " (вчера, " + reportDate.format(DateTimeFormatter.ofPattern("dd.MM")) + ")";
        } else {
            dateSuffix = " (" + reportDate.format(DateTimeFormatter.ofPattern("dd.MM")) + ")";
        }
        if (Boolean.FALSE.equals(isOnTime)) {
            return timeStr + dateSuffix.replace(")", ", опоздание)");
        }
        return timeStr + dateSuffix;
    }

    private ContentDraftDto mapDraftToDto(ContentDraft draft) {
        String draftType = draft.getDraftType() != null ? draft.getDraftType().toLowerCase() : "reel";
        return new ContentDraftDto(
                String.valueOf(draft.getId()),
                draftType,
                draft.getLabel(),
                Boolean.TRUE.equals(draft.getIsDuplicate()),
                draft.getDuplicateDate(),
                draft.getUrl()
        );
    }

    private ReelDto mapReelToDto(ReelSubmission reel) {
        return new ReelDto(
                String.valueOf(reel.getId()),
                reel.getReelUrl(),
                reel.getShortcode(),
                Boolean.TRUE.equals(reel.getIsValid()),
                Boolean.TRUE.equals(reel.getIsDuplicate()),
                reel.getDuplicateDate()
        );
    }

    private String buildMentorReplyMessage(String prefix, Instant submittedAt, String content) {
        if (content.startsWith(prefix)) {
            return content;
        }
        String timePart = submittedAt != null ? " (сдан в " + TIME_FORMATTER.format(submittedAt) + ")" : "";
        return prefix + timePart + ":\n\n" + content;
    }

    public String cleanReflectionActions(String rawActions) {
        if (!StringUtils.hasText(rawActions)) return "";
        String focusLine = null;
        String latestFact = null;
        // Split on newlines OR on lookahead for bullet/emoji indicators (handles inline concatenation)
        String[] chunks = rawActions.split("(?=(?:🎯|✅|\\n))");
        for (String chunk : chunks) {
            String trimmed = chunk.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("🎯 Фокус дня:") || (focusLine == null && trimmed.startsWith("🎯"))) {
                focusLine = trimmed;
            } else if (trimmed.startsWith("✅ Факт:")) {
                latestFact = trimmed;
            } else if (trimmed.startsWith("✅")) {
                latestFact = "✅ Факт: " + trimmed.substring(1).trim();
            } else if (trimmed.contains("Обработано рассылок:") || trimmed.contains("Создан черновик") || trimmed.contains("запланирован") || trimmed.contains("выполняла") || trimmed.contains("предпринимала")) {
                latestFact = "✅ Факт: " + trimmed.replaceFirst("^✅\\s*Факт:\\s*", "");
            } else if (latestFact == null && StringUtils.hasText(trimmed)) {
                latestFact = "✅ Факт: " + trimmed;
            }
        }
        if (focusLine != null && latestFact != null) {
            return focusLine + "\n" + latestFact;
        } else if (focusLine != null) {
            return focusLine;
        } else if (latestFact != null) {
            return latestFact;
        }
        return rawActions.trim();
    }

    public String cleanQuestionText(String q) {
        if (!StringUtils.hasText(q)) return null;
        String rawTrimmed = q.trim();
        String withoutTimestamp = rawTrimmed.replaceFirst("^\\[\\d{1,2}:\\d{2}\\]\\s*", "").trim();
        String trimmed = withoutTimestamp.replaceAll("^[\\\"'«»\\[\\(]+|[\\\"'»\\]\\).!?,—\\-]+$", "").trim();
        if (trimmed.isEmpty()) return null;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.equals("null") || lower.equals("none") || lower.equals("nil") ||
            lower.equals("нет") || lower.equals("нету") || lower.equals("нема") ||
            lower.equals("нет вопросов") || lower.equals("вопросов нет") || lower.equals("нет затыков") ||
            lower.equals("затыков нет") || lower.equals("все понятно") || lower.equals("всё понятно") ||
            lower.equals("все ясно") || lower.equals("всё ясно") || lower.equals("все ок") || lower.equals("всё ок") ||
            lower.equals("без вопросов") || lower.equals("ничего") ||
            lower.equals("n/a") || lower.equals("undefined") || lower.equals("-") || lower.equals("—") ||
            lower.matches("^[-—_~]+$")) {
            return null;
        }
        return withoutTimestamp;
    }
}
