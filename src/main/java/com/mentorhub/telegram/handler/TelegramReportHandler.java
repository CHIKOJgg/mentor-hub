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
import java.util.Objects;
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

        int initialDraftsCount = report.getContentDrafts() != null ? report.getContentDrafts().size() : 0;
        int initialReelsCount = report.getReelSubmissions() != null ? report.getReelSubmissions().size() : 0;
        boolean wasAlreadySubmitted = report.getSubmittedAt() != null &&
                ((report.getSalesTotal() != null && report.getSalesTotal().compareTo(BigDecimal.ZERO) > 0)
                        || (report.getMailingsCount() != null && report.getMailingsCount() > 0)
                        || initialDraftsCount > 0
                        || initialReelsCount > 0);

        // In mini-groups, bind the active group chatId to the student automatically
        if (message.getChat() != null && (message.getChat().isGroupChat() || message.getChat().isSuperGroupChat())) {
            student.setTgChatId(message.getChat().getId());
        }

        String text = message.getText();
        String caption = message.getCaption();
        String rawContent = StringUtils.hasText(text) ? text : (StringUtils.hasText(caption) ? caption : "");

        // 1. Process Voice / Audio / VideoNote attachments via Whisper STT
        if (message.hasVoice() && (settings == null || Boolean.TRUE.equals(settings.getAiWhisperEnabled()))) {
            String voiceFileId = message.getVoice().getFileId();
            byte[] audioBytes = telegramSenderService.downloadFileBytes(voiceFileId, 20 * 1024 * 1024);
            String voiceTranscript = audioBytes == null ? "" : springAiReportExtractor.transcribeAudio(audioBytes, "voice.oga");
            if (StringUtils.hasText(voiceTranscript) && !voiceTranscript.startsWith("[")) {
                report.setVoiceTranscript(StringUtils.hasText(report.getVoiceTranscript())
                        ? report.getVoiceTranscript() + "\n" + voiceTranscript
                        : voiceTranscript);
                rawContent = (StringUtils.hasText(rawContent) ? rawContent + "\n" : "") + voiceTranscript;
            }
        }

        if (message.hasVideoNote() && (settings == null || Boolean.TRUE.equals(settings.getAiWhisperEnabled()))) {
            String videoFileId = message.getVideoNote().getFileId();
            byte[] videoBytes = telegramSenderService.downloadFileBytes(videoFileId, 25 * 1024 * 1024);
            String videoTranscript = videoBytes == null ? "" : springAiReportExtractor.transcribeAudio(videoBytes, "videonote.mp4");
            if (StringUtils.hasText(videoTranscript) && !videoTranscript.startsWith("[")) {
                report.setVoiceTranscript(StringUtils.hasText(report.getVoiceTranscript())
                        ? report.getVoiceTranscript() + "\n" + videoTranscript
                        : videoTranscript);
                rawContent = (StringUtils.hasText(rawContent) ? rawContent + "\n" : "") + videoTranscript;
            }
        }

        // 2. Process Content Drafts & Attachments (BEFORE publication)
        processAttachmentsAndDrafts(report, student, message, rawContent, deduplicationEnabled);

        // 3. Process Instagram Reels with Anti-Fraud (SHA-256 deduplication & accessibility)
        processReelsAntiFraud(report, student, rawContent, deduplicationEnabled);

        // 4. Sales amount normalization with deduplication (Russian grouping separators: #продажи 35.500 руб -> 35500.00, 80к -> 80000.00)
        BigDecimal salesAmount = deterministicAiFallbackService.extractSalesAmount(rawContent);
        if (salesAmount != null && salesAmount.compareTo(BigDecimal.ZERO) > 0) {
            String entryDesc = rawContent.length() > 200 ? rawContent.substring(0, 200) : rawContent;
            boolean isDuplicateSale = false;
            if (report.getSalesEntries() != null && !report.getSalesEntries().isEmpty()) {
                for (SalesEntry existing : report.getSalesEntries()) {
                    if (existing.getAmount() != null && existing.getAmount().compareTo(salesAmount) == 0) {
                        String existingDesc = existing.getDescription() != null ? existing.getDescription().trim() : "";
                        String currentDesc = rawContent.trim();
                        if (existingDesc.equals(currentDesc)
                                || (existingDesc.contains("#продажи") && currentDesc.contains("#продажи"))
                                || (existingDesc.isEmpty() && currentDesc.replaceAll("[^0-9]", "").equals(String.valueOf(salesAmount.longValue())))) {
                            isDuplicateSale = true;
                            break;
                        }
                    }
                }
            }

            if (!isDuplicateSale) {
                BigDecimal currentSales = report.getSalesTotal() != null ? report.getSalesTotal() : BigDecimal.ZERO;
                report.setSalesTotal(currentSales.add(salesAmount));

                SalesEntry entry = SalesEntry.builder()
                        .report(report)
                        .student(student)
                        .amount(salesAmount)
                        .description(entryDesc)
                        .createdAt(Instant.now())
                        .build();
                salesEntryRepository.save(entry);
                if (report.getSalesEntries() == null) {
                    report.setSalesEntries(new ArrayList<>());
                }
                report.getSalesEntries().add(entry);

                BigDecimal currentIncome = student.getCurrentIncome() != null ? student.getCurrentIncome() : BigDecimal.ZERO;
                student.setCurrentIncome(currentIncome.add(salesAmount));
            } else {
                log.info("Duplicate sales entry ignored for student '{}' in report #{}: amount={}",
                        student.getFullName(), report.getId(), salesAmount);
            }
        }

        // 5. Mailings / Outreach count (#рассылки 25) - accumulator with duplicate protection
        Integer mailings = deterministicAiFallbackService.extractMailingsCount(rawContent);
        if (mailings != null && mailings > 0) {
            String existingRaw = report.getRawMessageText() != null ? report.getRawMessageText() : "";
            boolean isDuplicateMailing = existingRaw.contains(rawContent.trim())
                    || (existingRaw.contains("#рассылки") && existingRaw.contains(String.valueOf(mailings)));

            if (!isDuplicateMailing) {
                int prevMailings = report.getMailingsCount() != null ? report.getMailingsCount() : 0;
                report.setMailingsCount(prevMailings + mailings);

                int totalMailings = student.getTotalMailings() != null ? student.getTotalMailings() : 0;
                student.setTotalMailings(totalMailings + mailings);
            } else {
                log.info("Duplicate mailing count #{} ignored for student '{}' in report #{}",
                        mailings, student.getFullName(), report.getId());
            }
        }

        // 6. Saturday Survey Clients (#клиенты 2)
        Integer newClients = deterministicAiFallbackService.extractNewClientsCount(rawContent);
        if (newClients != null && newClients > 0) {
            String existingRaw = report.getRawMessageText() != null ? report.getRawMessageText() : "";
            boolean isDuplicateClients = existingRaw.contains(rawContent.trim())
                    || (existingRaw.contains("#клиенты") && existingRaw.contains(String.valueOf(newClients)));
            if (!isDuplicateClients) {
                report.setNewClientsCount(newClients);
                int totalClients = student.getTotalNewClients() != null ? student.getTotalNewClients() : 0;
                student.setTotalNewClients(totalClients + newClients);
            }
        }

        // 7. Question recognition (#вопрос, direct address "Ксения, ...", and AI extraction)
        String extractedQuestion = deterministicAiFallbackService.extractQuestion(rawContent);
        if (StringUtils.hasText(extractedQuestion)) {
            appendQuestion(report, extractedQuestion);
        }

        // 8. Spring AI 3-bullet reflection & Burnout Sentiment Detection
        String cumulativeContext = StringUtils.hasText(report.getRawMessageText())
                ? report.getRawMessageText() + (StringUtils.hasText(rawContent) && !report.getRawMessageText().contains(rawContent) ? "\n---\n" + rawContent : "")
                : rawContent;

        ExtractedReportDto aiFacts = settings == null || Boolean.TRUE.equals(settings.getAiSummaryEnabled())
                ? springAiReportExtractor.extractFacts(cumulativeContext, student.getFullName())
                : null;
        if (aiFacts != null) {
            if (aiFacts.reflectionSummary() != null) {
                if (StringUtils.hasText(aiFacts.reflectionSummary().emotions())) {
                    report.setReflectionEmotions(aiFacts.reflectionSummary().emotions());
                }

                String currentActions = report.getReflectionActions();
                String aiActions = aiFacts.reflectionSummary().actions();
                if (StringUtils.hasText(aiActions)) {
                    if (StringUtils.hasText(currentActions) && currentActions.startsWith("🎯 Фокус дня:")) {
                        report.setReflectionActions(currentActions + "\n✅ Факт: " + aiActions);
                    } else {
                        report.setReflectionActions(aiActions);
                    }
                }

                if (StringUtils.hasText(aiFacts.reflectionSummary().questionToMentor())) {
                    report.setReflectionQuestion(aiFacts.reflectionSummary().questionToMentor());
                    if (!StringUtils.hasText(extractedQuestion)) {
                        appendQuestion(report, aiFacts.reflectionSummary().questionToMentor());
                    }
                }
            }

            if (StringUtils.hasText(aiFacts.emotionalStatus())) {
                report.setEmotionalStatus(aiFacts.emotionalStatus());
            }

            if (StringUtils.hasText(aiFacts.aiSummary())) {
                report.setAiSummary(aiFacts.aiSummary());
            }

            // Natural language sales fallback if hashtag was omitted
            if ((salesAmount == null || salesAmount.compareTo(BigDecimal.ZERO) == 0) &&
                    aiFacts.salesAmount() != null && aiFacts.salesAmount().compareTo(BigDecimal.ZERO) > 0) {
                boolean isDuplicateAiSale = report.getSalesEntries() != null && report.getSalesEntries().stream()
                        .anyMatch(se -> se.getAmount() != null && se.getAmount().compareTo(aiFacts.salesAmount()) == 0);
                if (!isDuplicateAiSale) {
                    BigDecimal currentSales = report.getSalesTotal() != null ? report.getSalesTotal() : BigDecimal.ZERO;
                    report.setSalesTotal(currentSales.add(aiFacts.salesAmount()));
                    SalesEntry entry = SalesEntry.builder()
                            .report(report)
                            .student(student)
                            .amount(aiFacts.salesAmount())
                            .description("AI: " + (rawContent.length() > 180 ? rawContent.substring(0, 180) : rawContent))
                            .createdAt(Instant.now())
                            .build();
                    salesEntryRepository.save(entry);
                    if (report.getSalesEntries() == null) {
                        report.setSalesEntries(new ArrayList<>());
                    }
                    report.getSalesEntries().add(entry);
                    student.setCurrentIncome((student.getCurrentIncome() != null ? student.getCurrentIncome() : BigDecimal.ZERO).add(aiFacts.salesAmount()));
                }
            }

            // Natural language mailings fallback if hashtag was omitted - delta tracking
            if ((mailings == null || mailings == 0) && aiFacts.mailingsCount() != null && aiFacts.mailingsCount() > 0) {
                int currentReportMailings = report.getMailingsCount() != null ? report.getMailingsCount() : 0;
                if (aiFacts.mailingsCount() > currentReportMailings) {
                    int delta = aiFacts.mailingsCount() - currentReportMailings;
                    report.setMailingsCount(aiFacts.mailingsCount());
                    student.setTotalMailings((student.getTotalMailings() != null ? student.getTotalMailings() : 0) + delta);
                }
            }
        }

        if (settings == null || Boolean.TRUE.equals(settings.getZoomAutoDetectEnabled())) {
            completeZoomFromMessage(student, rawContent);
        }

        // 9. Accumulate Raw message text without duplicating identical blocks
        String existingRaw = report.getRawMessageText();
        String trimmedRaw = rawContent.trim();
        if (StringUtils.hasText(existingRaw)) {
            if (!existingRaw.trim().endsWith(trimmedRaw) && !existingRaw.contains(trimmedRaw)) {
                report.setRawMessageText(existingRaw + "\n---\n" + rawContent);
            }
        } else {
            report.setRawMessageText(rawContent);
        }

        int newDraftsAdded = (report.getContentDrafts() != null ? report.getContentDrafts().size() : 0) - initialDraftsCount;
        int newReelsAdded = (report.getReelSubmissions() != null ? report.getReelSubmissions().size() : 0) - initialReelsCount;
        boolean hasSales = salesAmount != null && salesAmount.compareTo(BigDecimal.ZERO) > 0;
        boolean hasMailings = mailings != null && mailings > 0;
        boolean hasClients = newClients != null && newClients > 0;
        boolean hasQuestion = StringUtils.hasText(extractedQuestion);

        boolean isReport = isReportSubmission(message, rawContent, newDraftsAdded, newReelsAdded, hasSales, hasMailings, hasClients, hasQuestion);

        if (isReport) {
            report.setSubmittedAt(Instant.now());
            report.setIsOnTime(isOnTime);
            report.setStatus("PENDING");
            report.setLastTgMessageId(message.getMessageId());

            // 10. Send caring acknowledgment reply in mini-group only when actual report submitted
            Long chatId = message.getChat() != null ? message.getChat().getId() : student.getTgChatId();
            String timeStr = TIME_FORMATTER.format(report.getSubmittedAt());
            String acknowledgement = wasAlreadySubmitted
                    ? "Принято на проверку Ксении и Кате (" + timeStr + ")! ✍️ Черновик обновлён."
                    : "Принято на проверку Ксении и Кате (" + timeStr + ")! ✍️ Черновик сохранён.";
            String businessConnectionId = message.getBusinessConnectionId();
            if (StringUtils.hasText(businessConnectionId)) {
                telegramSenderService.sendCaringReply(chatId, message.getMessageId(), acknowledgement, businessConnectionId);
            } else {
                telegramSenderService.sendCaringReply(chatId, message.getMessageId(), acknowledgement);
            }
        } else {
            log.info("Message from student '{}' scanned silently (not a homework/report submission): '{}'",
                    student.getFullName(), rawContent.length() > 50 ? rawContent.substring(0, 50) + "..." : rawContent);
        }

        // Save aggregates
        DailyReport savedReport = dailyReportRepository.save(report);
        studentRepository.save(student);

        log.info("Report updated for student '{}' (#{}): date={}, mailings={}, sales={}, status={}, isReport={}",
                student.getFullName(), student.getId(), today, savedReport.getMailingsCount(), savedReport.getSalesTotal(), savedReport.getStatus(), isReport);

        return savedReport;
    }

    @Transactional
    public DailyReport handleMorningFocusMessage(Student student, Message message, String rawContent) {
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
                        .build());

        String cleanFocus = rawContent != null ? rawContent.trim() : "";
        report.setReflectionActions("🎯 Фокус дня: " + cleanFocus);
        if (!StringUtils.hasText(report.getAiSummary())) {
            report.setAiSummary("Фокус на сегодня: " + cleanFocus);
        }
        String existingRaw = report.getRawMessageText();
        report.setRawMessageText(StringUtils.hasText(existingRaw)
                ? existingRaw + "\n[Фокус дня]: " + cleanFocus
                : "[Фокус дня]: " + cleanFocus);

        DailyReport savedReport = dailyReportRepository.save(report);

        Long chatId = message.getChat() != null ? message.getChat().getId() : student.getTgChatId();
        String ack = "🎯 Цель на сегодня зафиксирована: «" + cleanFocus + "»!\nЖдём результаты в отчёте до 21:00 🌸";
        String businessConnectionId = message.getBusinessConnectionId();
        if (StringUtils.hasText(businessConnectionId)) {
            telegramSenderService.sendCaringReply(chatId, message.getMessageId(), ack, businessConnectionId);
        } else {
            telegramSenderService.sendCaringReply(chatId, message.getMessageId(), ack);
        }

        log.info("Morning focus set via text for student '{}' (#{}): {}",
                student.getFullName(), student.getId(), cleanFocus);

        return savedReport;
    }

    public boolean isReportSubmission(
            Message message,
            String rawContent,
            int newDraftsAdded,
            int newReelsAdded,
            boolean hasSales,
            boolean hasMailings,
            boolean hasClients,
            boolean hasQuestion) {
        if (message != null && (message.hasVideo() || message.hasVideoNote() || message.hasDocument() || message.hasPhoto() || message.hasVoice() || message.hasAudio())) {
            return true;
        }
        if (newDraftsAdded > 0 || newReelsAdded > 0 || hasSales || hasMailings || hasClients || hasQuestion) {
            return true;
        }
        if (StringUtils.hasText(rawContent)) {
            String lower = rawContent.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("#рилс") || lower.contains("#карусель") || lower.contains("#сторителл")
                    || lower.contains("#контент") || lower.contains("#рассылки") || lower.contains("#продажи")
                    || lower.contains("#вопрос") || lower.contains("#клиенты") || lower.contains("#отчет")
                    || lower.contains("#отчёт") || lower.contains("#дз") || lower.contains("#задание")) {
                return true;
            }
            if (lower.startsWith("отчет") || lower.startsWith("отчёт") || lower.startsWith("дз")
                    || lower.startsWith("задание") || lower.startsWith("домашка") || lower.startsWith("выполнила")) {
                return true;
            }
        }
        return false;
    }

    private void processAttachmentsAndDrafts(DailyReport report, Student student, Message message, String rawContent, boolean deduplicationEnabled) {
        // Video file attachment
        if (message.hasVideo()) {
            String fileId = message.getVideo().getFileId();
            String fileUniqueId = message.getVideo().getFileUniqueId();
            boolean added = createOrUpdateDraft(report, student, "REEL", "Черновик видео Reels", null, fileId, fileUniqueId, deduplicationEnabled);
            if (added) {
                student.setTotalReels((student.getTotalReels() != null ? student.getTotalReels() : 0) + 1);
            }
        }

        // Video note (кружочек)
        if (message.hasVideoNote()) {
            String fileId = message.getVideoNote().getFileId();
            String fileUniqueId = message.getVideoNote().getFileUniqueId();
            boolean added = createOrUpdateDraft(report, student, "REEL", "Черновик Reels (видеозаметка)", null, fileId, fileUniqueId, deduplicationEnabled);
            if (added) {
                student.setTotalReels((student.getTotalReels() != null ? student.getTotalReels() : 0) + 1);
            }
        }

        // PDF document attachment -> Carousel draft
        if (message.hasDocument()) {
            String fileName = message.getDocument().getFileName();
            String fileId = message.getDocument().getFileId();
            String fileUniqueId = message.getDocument().getFileUniqueId();
            String label = StringUtils.hasText(fileName) ? "Черновик карусели: " + fileName : "Черновик карусели (PDF)";
            boolean added = createOrUpdateDraft(report, student, "CAROUSEL", label, null, fileId, fileUniqueId, deduplicationEnabled);
            if (added) {
                student.setTotalCarousels((student.getTotalCarousels() != null ? student.getTotalCarousels() : 0) + 1);
            }
        }

        // Photos attachment -> Carousel draft
        if (message.hasPhoto() && !message.getPhoto().isEmpty()) {
            List<PhotoSize> photos = message.getPhoto();
            PhotoSize largest = photos.stream().max(Comparator.comparing(PhotoSize::getFileSize)).orElse(photos.get(photos.size() - 1));
            boolean added = createOrUpdateDraft(report, student, "CAROUSEL", "Черновик карусели (фото)", null, largest.getFileId(), largest.getFileUniqueId(), deduplicationEnabled);
            if (added) {
                student.setTotalCarousels((student.getTotalCarousels() != null ? student.getTotalCarousels() : 0) + 1);
            }
        }

        // Text drafts matching #контент, #рилс, #карусель, #сторителл
        boolean hasReelsUrl = StringUtils.hasText(rawContent) && !reelsAntiFraudEngine.extractReels(rawContent).isEmpty();
        if (StringUtils.hasText(rawContent)) {
            Matcher m = HASHTAG_DRAFT_PATTERN.matcher(rawContent);
            while (m.find()) {
                String tag = m.group(1).toLowerCase();
                String snippet = m.group(2).trim();

                // If #рилс contains a link to Instagram Reels, let processReelsAntiFraud handle it!
                if (tag.contains("рилс") && (hasReelsUrl || snippet.contains("instagram.com") || snippet.contains("http://") || snippet.contains("https://"))) {
                    continue;
                }

                String draftType;
                String defaultLabel;
                if (tag.contains("рилс")) {
                    draftType = "REEL";
                    defaultLabel = "Черновик Reels: " + (StringUtils.hasText(snippet) ? snippet : "сценарий");
                } else if (tag.contains("карусел")) {
                    draftType = "CAROUSEL";
                    defaultLabel = "Черновик карусели: " + (StringUtils.hasText(snippet) ? snippet : "слайды");
                } else if (tag.contains("стори")) {
                    draftType = "STORYTELL";
                    defaultLabel = "Сценарий сторителлинга: " + (StringUtils.hasText(snippet) ? snippet : "серия историй");
                } else {
                    draftType = "STORYTELL";
                    defaultLabel = "Черновик контента: " + (StringUtils.hasText(snippet) ? snippet : "материал");
                }

                boolean added = createOrUpdateDraft(report, student, draftType, defaultLabel, null, null, null, deduplicationEnabled);
                if (added) {
                    if ("REEL".equals(draftType)) {
                        student.setTotalReels((student.getTotalReels() != null ? student.getTotalReels() : 0) + 1);
                    } else if ("CAROUSEL".equals(draftType)) {
                        student.setTotalCarousels((student.getTotalCarousels() != null ? student.getTotalCarousels() : 0) + 1);
                    } else {
                        student.setTotalStorytells((student.getTotalStorytells() != null ? student.getTotalStorytells() : 0) + 1);
                    }
                }
            }
        }
    }

    private boolean createOrUpdateDraft(DailyReport report, Student student, String draftType, String label,
                                        String url, String fileTgId, String fileUniqueId, boolean deduplicationEnabled) {
        if (report.getContentDrafts() != null) {
            boolean alreadyPresent = report.getContentDrafts().stream().anyMatch(d ->
                    (StringUtils.hasText(fileTgId) && Objects.equals(d.getFileTgId(), fileTgId))
                    || (StringUtils.hasText(url) && Objects.equals(d.getUrl(), url))
                    || (Objects.equals(d.getLabel(), label) && Objects.equals(d.getDraftType(), draftType))
            );
            if (alreadyPresent) {
                log.info("Draft '{}' already present in report #{}, skipping duplicate", label, report.getId());
                return false;
            }
        }

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
        if (report.getContentDrafts() == null) {
            report.setContentDrafts(new ArrayList<>());
        }
        report.getContentDrafts().add(draft);
        return true;
    }

    private void processReelsAntiFraud(DailyReport report, Student student, String rawContent, boolean deduplicationEnabled) {
        List<ReelsAntiFraudEngine.ExtractedReel> extractedReels = reelsAntiFraudEngine.extractReels(rawContent);

        for (ReelsAntiFraudEngine.ExtractedReel reel : extractedReels) {
            // Check if this reel was ALREADY registered in today's report
            boolean alreadyInReport = report.getReelSubmissions() != null && report.getReelSubmissions().stream()
                    .anyMatch(rs -> Objects.equals(rs.getShortcode(), reel.shortcode())
                            || Objects.equals(rs.getCanonicalUrl(), reel.canonicalUrl())
                            || Objects.equals(rs.getReelUrl(), reel.rawUrl()));

            if (alreadyInReport) {
                log.info("Reel '{}' already registered in report #{}, skipping duplicate reel submission",
                        reel.shortcode(), report.getId());
                continue;
            }

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
            if (report.getReelSubmissions() == null) {
                report.setReelSubmissions(new ArrayList<>());
            }
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
            if (report.getContentDrafts() == null) {
                report.setContentDrafts(new ArrayList<>());
            }
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
