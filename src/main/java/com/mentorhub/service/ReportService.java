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

        return reports.stream().map(this::mapToResponse).toList();
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
            messageDelivered = telegramSenderService.sendStudentMessage(chatId, businessConnectionId, replyText.trim());
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
            feedbackSent = telegramSenderService.sendStudentMessage(chatId, businessConnectionId, request.reason().trim());
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

        String submittedAtStr = formatSubmittedAt(report.getSubmittedAt(), report.getIsOnTime());

        List<ContentDraftDto> drafts = report.getContentDrafts() != null
                ? report.getContentDrafts().stream().map(this::mapDraftToDto).toList()
                : List.of();

        List<ReelDto> reels = report.getReelSubmissions() != null
                ? report.getReelSubmissions().stream().map(this::mapReelToDto).toList()
                : List.of();

        ReflectionSummaryDto reflection = null;
        if (StringUtils.hasText(report.getReflectionEmotions()) ||
            StringUtils.hasText(report.getReflectionActions()) ||
            StringUtils.hasText(report.getReflectionQuestion())) {
            reflection = new ReflectionSummaryDto(
                    report.getReflectionEmotions() != null ? report.getReflectionEmotions() : "",
                    report.getReflectionActions() != null ? report.getReflectionActions() : "",
                    report.getReflectionQuestion()
            );
        }

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
                report.getMentorQuestion(),
                report.getVoiceTranscript(),
                reflection,
                report.getAiSummary() != null ? report.getAiSummary() : ""
        );
    }

    private String formatSubmittedAt(Instant submittedAt, Boolean isOnTime) {
        if (submittedAt == null) return "только что";
        String timeStr = TIME_FORMATTER.format(submittedAt);
        if (Boolean.FALSE.equals(isOnTime)) {
            return timeStr + " (опоздание)";
        }
        return timeStr + " (сегодня)";
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
}
