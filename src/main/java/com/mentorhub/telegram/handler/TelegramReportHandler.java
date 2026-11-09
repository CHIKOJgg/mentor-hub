package com.mentorhub.telegram.handler;

import com.mentorhub.ai.dto.ExtractedReportDto;
import com.mentorhub.ai.service.DeterministicAiFallbackService;
import com.mentorhub.ai.service.SpringAiReportExtractor;
import com.mentorhub.entity.*;
import com.mentorhub.repository.ContentDraftRepository;
import com.mentorhub.repository.DailyReportRepository;
import com.mentorhub.repository.ReelSubmissionRepository;
import com.mentorhub.repository.SalesEntryRepository;
import com.mentorhub.repository.StudentRepository;
import com.mentorhub.repository.MentorSettingRepository;
import com.mentorhub.repository.ZoomMeetingRepository;
import com.mentorhub.telegram.antifraud.ReelsAntiFraudEngine;
import com.mentorhub.telegram.service.TelegramSenderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TelegramReportHandler {

    private static final ZoneId MINSK_ZONE = ZoneId.of("Europe/Minsk");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm").withZone(MINSK_ZONE);

    private static final Pattern HASHTAG_DRAFT_PATTERN = Pattern.compile(
            "#(?i)(контент|рилс|карусель|карусели|сторителл|сторис)[:\\s]*([^#\n]*)"
    );

    private final DailyReportRepository dailyReportRepository;
    private final StudentRepository studentRepository;
    private final MentorSettingRepository mentorSettingRepository;
    private final ZoomMeetingRepository zoomMeetingRepository;
    private final ReelSubmissionRepository reelSubmissionRepository;
    private final ContentDraftRepository contentDraftRepository;
    private final SalesEntryRepository salesEntryRepository;
    private final ReelsAntiFraudEngine reelsAntiFraudEngine;
    private final SpringAiReportExtractor springAiReportExtractor;
    private final DeterministicAiFallbackService deterministicAiFallbackService;
    private final TelegramSenderService telegramSenderService;

    @Autowired
    public TelegramReportHandler(
            DailyReportRepository dailyReportRepository,
            StudentRepository studentRepository,
            MentorSettingRepository mentorSettingRepository,
            ZoomMeetingRepository zoomMeetingRepository,
            ReelSubmissionRepository reelSubmissionRepository,
            ContentDraftRepository contentDraftRepository,
            SalesEntryRepository salesEntryRepository,
            ReelsAntiFraudEngine reelsAntiFraudEngine,
            SpringAiReportExtractor springAiReportExtractor,
            DeterministicAiFallbackService deterministicAiFallbackService,
            TelegramSenderService telegramSenderService
    ) {
        this.dailyReportRepository = dailyReportRepository;
        this.studentRepository = studentRepository;
        this.mentorSettingRepository = mentorSettingRepository;
        this.zoomMeetingRepository = zoomMeetingRepository;
        this.reelSubmissionRepository = reelSubmissionRepository;
        this.contentDraftRepository = contentDraftRepository;
        this.salesEntryRepository = salesEntryRepository;
        this.reelsAntiFraudEngine = reelsAntiFraudEngine;
        this.springAiReportExtractor = springAiReportExtractor;
        this.deterministicAiFallbackService = deterministicAiFallbackService;
        this.telegramSenderService = telegramSenderService;
    }

    public TelegramReportHandler(
            DailyReportRepository dailyReportRepository,
            StudentRepository studentRepository,
            ReelSubmissionRepository reelSubmissionRepository,
            ContentDraftRepository contentDraftRepository,
            SalesEntryRepository salesEntryRepository,
            ReelsAntiFraudEngine reelsAntiFraudEngine,
            SpringAiReportExtractor springAiReportExtractor,
            DeterministicAiFallbackService deterministicAiFallbackService,
            TelegramSenderService telegramSenderService
    ) {
        this(dailyReportRepository, studentRepository, null, null, reelSubmissionRepository,
                contentDraftRepository, salesEntryRepository, reelsAntiFraudEngine,
                springAiReportExtractor, deterministicAiFallbackService, telegramSenderService);
    }

    @Transactional
    public DailyReport handleStudentMessage(Student student, Message message) {
        LocalDate today = LocalDate.now(MINSK_ZONE);
        LocalTime nowTime = LocalTime.now(MINSK_ZONE);
        MentorSetting settings = mentorSettingRepository != null ? mentorSettingRepository.findFirstByOrderByIdAsc().orElse(null) : null;
        boolean strictDeadlineEnabled = settings == null || Boolean.TRUE.equals(settings.getStrictDeadlineEnabled());
        LocalTime deadline = settings != null && settings.getDeadlineTime() != null ? settings.getDeadlineTime() : LocalTime.of(21, 0);
        boolean isOnTime = !strictDeadlineEnabled || nowTime.isBefore(deadline);
        boolean deduplicationEnabled = settings == null || Boolean.TRUE.equals(settings.getAntiFraudDedupEnabled());

        DailyReport report = dailyReportRepository.findByStudentIdAndReportDate(student.getId(), today)
                .orElseGet(() -> {
                    DailyReport newReport = DailyReport.builder()
                            .student(student)
                            .reportDate(today)
                            .submittedAt(Instant.now())
                            .isOnTime(isOnTime)
                            .status("PENDING")
                            .mailingsCount(0)
                            .salesTotal(BigDecimal.ZERO)
                            .newClientsCount(0)
                            .contentDrafts(new ArrayList<>())
                            .reelSubmissions(new ArrayList<>())
                            .salesEntries(new ArrayList<>())
                            .build();
                    return dailyReportRepository.save(newReport);
                });
        if (report.getId() == null) {
            report = dailyReportRepository.save(report);
        }

        report.setSubmittedAt(Instant.now());
        report.setIsOnTime(isOnTime);
        if ("REJECTED".equalsIgnoreCase(report.getStatus())) {
            report.setStatus("PENDING");
        }

        String text = message.getText();
        String caption = message.getCaption();
        String rawContent = StringUtils.hasText(text) ? text : (StringUtils.hasText(caption) ? caption : "");

        // 1. Process Voice / Audio attachments
        if (message.hasVoice() && (settings == null || Boolean.TRUE.equals(settings.getAiWhisperEnabled()))) {
            String voiceFileId = message.getVoice().getFileId();
            byte[] audioBytes = telegramSenderService.downloadFileBytes(voiceFileId, 20 * 1024 * 1024);
            String voiceTranscript = audioBytes == null ? "" : springAiReportExtractor.transcribeAudio(audioBytes, "voice.oga");
            if (StringUtils.hasText(voiceTranscript)) report.setVoiceTranscript(voiceTranscript);
            if (StringUtils.hasText(voiceTranscript)) {
                rawContent = (StringUtils.hasText(rawContent) ? rawContent + "\n" : "") + voiceTranscript;
            }
        }

        // 2. Process Content Drafts & Attachments (BEFORE publication)
        processAttachmentsAndDrafts(report, student, message, rawContent, deduplicationEnabled);

        // 3. Process Instagram Reels with Anti-Fraud (SHA-256 deduplication & accessibility)
        processReelsAntiFraud(report, student, rawContent, deduplicationEnabled);

        // 4. Sales amount normalization (Russian grouping separators: #продажи 35.500 руб -> 35500.00, 80к -> 80000.00)
        BigDecimal salesAmount = deterministicAiFallbackService.extractSalesAmount(rawContent);
        if (salesAmount != null && salesAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentSales = report.getSalesTotal() != null ? report.getSalesTotal() : BigDecimal.ZERO;
            report.setSalesTotal(currentSales.add(salesAmount));

            SalesEntry entry = SalesEntry.builder()
                    .report(report)
                    .student(student)
                    .amount(salesAmount)
                    .description(rawContent.length() > 200 ? rawContent.substring(0, 200) : rawContent)
                    .createdAt(Instant.now())
                    .build();
            salesEntryRepository.save(entry);
            report.getSalesEntries().add(entry);

            BigDecimal currentIncome = student.getCurrentIncome() != null ? student.getCurrentIncome() : BigDecimal.ZERO;
            student.setCurrentIncome(currentIncome.add(salesAmount));
        }

        // 5. Mailings / Outreach count (#рассылки 25)
        Integer mailings = deterministicAiFallbackService.extractMailingsCount(rawContent);
        if (mailings != null && mailings > 0) {
            int prevMailings = report.getMailingsCount() != null ? report.getMailingsCount() : 0;
            report.setMailingsCount(prevMailings + mailings);

            int totalMailings = student.getTotalMailings() != null ? student.getTotalMailings() : 0;
            student.setTotalMailings(totalMailings + mailings);
        }

        // 6. Saturday Survey Clients (#клиенты 2)
        Integer newClients = deterministicAiFallbackService.extractNewClientsCount(rawContent);
        if (newClients != null && newClients > 0) {
            report.setNewClientsCount(newClients);
            int totalClients = student.getTotalNewClients() != null ? student.getTotalNewClients() : 0;
            student.setTotalNewClients(totalClients + newClients);
        }

        // 7. Question recognition (#вопрос, direct address "Ксения, ...", and AI extraction)
        String extractedQuestion = deterministicAiFallbackService.extractQuestion(rawContent);
        if (StringUtils.hasText(extractedQuestion)) {
            appendQuestion(report, extractedQuestion);
        }

        // 8. Spring AI 3-bullet reflection & Burnout Sentiment Detection
        ExtractedReportDto aiFacts = settings == null || Boolean.TRUE.equals(settings.getAiSummaryEnabled())
                ? springAiReportExtractor.extractFacts(rawContent, student.getFullName())
                : null;
        if (aiFacts != null) {
            if (aiFacts.reflectionSummary() != null) {
                report.setReflectionEmotions(aiFacts.reflectionSummary().emotions());
                report.setReflectionActions(aiFacts.reflectionSummary().actions());
                report.setReflectionQuestion(aiFacts.reflectionSummary().questionToMentor());

                if (!StringUtils.hasText(extractedQuestion) && StringUtils.hasText(aiFacts.reflectionSummary().questionToMentor())) {
                    appendQuestion(report, aiFacts.reflectionSummary().questionToMentor());
                }
            }

            if (StringUtils.hasText(aiFacts.emotionalStatus())) {
                report.setEmotionalStatus(aiFacts.emotionalStatus());
            }

            if (StringUtils.hasText(aiFacts.aiSummary())) {
                report.setAiSummary(aiFacts.aiSummary());
            }
        }

        if (settings == null || Boolean.TRUE.equals(settings.getZoomAutoDetectEnabled())) {
            completeZoomFromMessage(student, rawContent);
        }

        // 9. Accumulate Raw message text
        String existingRaw = report.getRawMessageText();
        report.setRawMessageText(StringUtils.hasText(existingRaw)
                ? existingRaw + "\n---\n" + rawContent
                : rawContent);

        // Save aggregates
        DailyReport savedReport = dailyReportRepository.save(report);
        studentRepository.save(student);

        // 10. Send caring acknowledgment reply in mini-group
        Long chatId = message.getChat() != null ? message.getChat().getId() : student.getTgChatId();
        String acknowledgement = "Принято на проверку Ксении и Кате! ✍️ Черновик сохранён.";
        String businessConnectionId = message.getBusinessConnectionId();
        if (StringUtils.hasText(businessConnectionId)) {
            telegramSenderService.sendCaringReply(chatId, message.getMessageId(), acknowledgement, businessConnectionId);
        } else {
            telegramSenderService.sendCaringReply(chatId, message.getMessageId(), acknowledgement);
        }

        log.info("Report upserted successfully for student '{}' (#{}): date={}, mailings={}, sales={}, status={}",
                student.getFullName(), student.getId(), today, savedReport.getMailingsCount(), savedReport.getSalesTotal(), savedReport.getStatus());

        return savedReport;
    }

    private void processAttachmentsAndDrafts(DailyReport report, Student student, Message message, String rawContent, boolean deduplicationEnabled) {
        // Video file attachment
        if (message.hasVideo()) {
            String fileId = message.getVideo().getFileId();
            String fileUniqueId = message.getVideo().getFileUniqueId();
            createOrUpdateDraft(report, student, "REEL", "Черновик видео Reels", null, fileId, fileUniqueId, deduplicationEnabled);
            student.setTotalReels((student.getTotalReels() != null ? student.getTotalReels() : 0) + 1);
        }

        // Video note (кружочек)
        if (message.hasVideoNote()) {
            String fileId = message.getVideoNote().getFileId();
            String fileUniqueId = message.getVideoNote().getFileUniqueId();
            createOrUpdateDraft(report, student, "REEL", "Черновик Reels (видеозаметка)", null, fileId, fileUniqueId, deduplicationEnabled);
            student.setTotalReels((student.getTotalReels() != null ? student.getTotalReels() : 0) + 1);
        }

        // PDF document attachment -> Carousel draft
        if (message.hasDocument()) {
            String fileName = message.getDocument().getFileName();
            String fileId = message.getDocument().getFileId();
            String fileUniqueId = message.getDocument().getFileUniqueId();
            String label = StringUtils.hasText(fileName) ? "Черновик карусели: " + fileName : "Черновик карусели (PDF)";
            createOrUpdateDraft(report, student, "CAROUSEL", label, null, fileId, fileUniqueId, deduplicationEnabled);
            student.setTotalCarousels((student.getTotalCarousels() != null ? student.getTotalCarousels() : 0) + 1);
        }

        // Photos attachment -> Carousel draft
        if (message.hasPhoto() && !message.getPhoto().isEmpty()) {
            List<PhotoSize> photos = message.getPhoto();
            PhotoSize largest = photos.stream().max(Comparator.comparing(PhotoSize::getFileSize)).orElse(photos.get(photos.size() - 1));
            createOrUpdateDraft(report, student, "CAROUSEL", "Черновик карусели (фото)", null, largest.getFileId(), largest.getFileUniqueId(), deduplicationEnabled);
            student.setTotalCarousels((student.getTotalCarousels() != null ? student.getTotalCarousels() : 0) + 1);
        }

        // Text drafts matching #контент, #рилс, #карусель, #сторителл
        if (StringUtils.hasText(rawContent)) {
            Matcher m = HASHTAG_DRAFT_PATTERN.matcher(rawContent);
            while (m.find()) {
                String tag = m.group(1).toLowerCase();
                String snippet = m.group(2).trim();

                String draftType;
                String defaultLabel;
                if (tag.contains("рилс")) {
                    draftType = "REEL";
                    defaultLabel = "Черновик Reels: " + (StringUtils.hasText(snippet) ? snippet : "сценарий");
                    student.setTotalReels((student.getTotalReels() != null ? student.getTotalReels() : 0) + 1);
                } else if (tag.contains("карусел")) {
                    draftType = "CAROUSEL";
                    defaultLabel = "Черновик карусели: " + (StringUtils.hasText(snippet) ? snippet : "слайды");
                    student.setTotalCarousels((student.getTotalCarousels() != null ? student.getTotalCarousels() : 0) + 1);
                } else if (tag.contains("стори")) {
                    draftType = "STORYTELL";
                    defaultLabel = "Сценарий сторителлинга: " + (StringUtils.hasText(snippet) ? snippet : "серия историй");
                    student.setTotalStorytells((student.getTotalStorytells() != null ? student.getTotalStorytells() : 0) + 1);
                } else {
                    draftType = "STORYTELL";
                    defaultLabel = "Черновик контента: " + (StringUtils.hasText(snippet) ? snippet : "материал");
                    student.setTotalStorytells((student.getTotalStorytells() != null ? student.getTotalStorytells() : 0) + 1);
                }

                createOrUpdateDraft(report, student, draftType, defaultLabel, null, null, null, deduplicationEnabled);
            }
        }
    }

    private void createOrUpdateDraft(DailyReport report, Student student, String draftType, String label,
                                     String url, String fileTgId, String fileUniqueId, boolean deduplicationEnabled) {
        boolean isDuplicate = false;
        String duplicateDate = null;

        if (deduplicationEnabled && StringUtils.hasText(fileTgId)) {
            // Efficient indexed deduplication check instead of loading entire table
            if (contentDraftRepository.existsByFileTgId(fileTgId)) {
                isDuplicate = true;
                duplicateDate = "ранее";
            }
        }

        ContentDraft draft = ContentDraft.builder()
                .report(report)
                .draftType(draftType)
                .label(label)
                .url(url)
                .fileTgId(fileTgId)
                .isDuplicate(isDuplicate)
                .duplicateDate(duplicateDate)
                .createdAt(Instant.now())
                .build();

        contentDraftRepository.save(draft);
        report.getContentDrafts().add(draft);
    }

    private void processReelsAntiFraud(DailyReport report, Student student, String rawContent, boolean deduplicationEnabled) {
        List<ReelsAntiFraudEngine.ExtractedReel> extractedReels = reelsAntiFraudEngine.extractReels(rawContent);

        for (ReelsAntiFraudEngine.ExtractedReel reel : extractedReels) {
            ReelsAntiFraudEngine.AntiFraudEvaluation eval = reelsAntiFraudEngine.evaluateReel(
                    reel.rawUrl(),
                    reel.shortcode(),
                    reel.canonicalUrl(),
                    student,
                    deduplicationEnabled
            );

            ReelSubmission submission = ReelSubmission.builder()
                    .report(report)
                    .reelUrl(eval.rawUrl())
                    .canonicalUrl(eval.canonicalUrl())
                    .shortcode(eval.shortcode())
                    .reelHash(eval.reelHash())
                    .isValid(eval.isValid())
                    .isDuplicate(eval.isDuplicate())
                    .duplicateDate(eval.duplicateDate())
                    .duplicateOf(eval.duplicateOf())
                    .httpStatusCode(eval.httpStatusCode())
                    .checkedAt(Instant.now())
                    .build();

            reelSubmissionRepository.save(submission);
            report.getReelSubmissions().add(submission);

            // Add corresponding ContentDraft
            String draftLabel = eval.isCrossStudentFraud()
                    ? eval.fraudAlertMessage()
                    : (eval.isDuplicate() ? "Черновик Reels (дубликат)" : "Черновик Reels");

            ContentDraft draft = ContentDraft.builder()
                    .report(report)
                    .draftType("REEL")
                    .label(draftLabel)
                    .url(eval.canonicalUrl())
                    .isDuplicate(eval.isDuplicate())
                    .duplicateDate(eval.duplicateDate())
                    .createdAt(Instant.now())
                    .build();

            contentDraftRepository.save(draft);
            report.getContentDrafts().add(draft);

            if (!eval.isDuplicate()) {
                student.setTotalReels((student.getTotalReels() != null ? student.getTotalReels() : 0) + 1);
            }
        }
    }

    private void completeZoomFromMessage(Student student, String rawContent) {
        if (zoomMeetingRepository == null || student == null || !StringUtils.hasText(rawContent)) return;
        Matcher matcher = Pattern.compile("https?://\\S*zoom\\.us/(?:j/|rec/|rec/share/)\\S+", Pattern.CASE_INSENSITIVE).matcher(rawContent);
        if (!matcher.find()) return;

        ZoomMeeting meeting = zoomMeetingRepository.findFirstByStudentIdAndIsCompletedFalseOrderByScheduledAtAsc(student.getId())
                .orElse(null);
        if (meeting == null) return;

        String link = matcher.group().replaceAll("[),.]+$", "");
        if (link.toLowerCase().contains("/rec/")) meeting.setRecordingUrl(link);
        else meeting.setZoomUrl(link);
        meeting.setIsCompleted(true);
        meeting.setCompletedAt(Instant.now());
        zoomMeetingRepository.save(meeting);
        student.setCompletedZoomsCount(Math.toIntExact(zoomMeetingRepository.countByStudentIdAndIsCompletedTrue(student.getId())));
        student.setNextZoomAt(zoomMeetingRepository.findByStudentIdOrderByCallNumberAscIdAsc(student.getId()).stream()
                .filter(item -> !Boolean.TRUE.equals(item.getIsCompleted()))
                .map(ZoomMeeting::getScheduledAt)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null));
    }

    private void appendQuestion(DailyReport report, String questionText) {
        String timeStr = TIME_FORMATTER.format(Instant.now());
        String formattedSnippet = "[" + timeStr + "] " + questionText.trim();

        if (!StringUtils.hasText(report.getMentorQuestion())) {
            report.setMentorQuestion(formattedSnippet);
        } else {
            report.setMentorQuestion(report.getMentorQuestion() + "\n" + formattedSnippet);
        }
    }
}
