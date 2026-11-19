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
import java.net.URI;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)https?://[^\\s<>\\\"']+");

    private static final Pattern MEETING_TIME_PATTERN = Pattern.compile(
            "(?:в\\s*)?(\\b[0-2]?\\d):([0-5]\\d)\\b"
    );
    private static final Pattern MEETING_HOUR_PATTERN = Pattern.compile(
            "в\\s*(\\b[0-2]?\\d)\\b(?!\\s*[:.])"
    );
    private static final Pattern MEETING_NUM_DATE_PATTERN = Pattern.compile(
            "\\b(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{2,4}))?\\b"
    );
    private static final Pattern MEETING_RU_DATE_PATTERN = Pattern.compile(
            "\\b(\\d{1,2})\\s+([а-яё]+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final String[] RU_MONTHS = {
            "январ", "феврал", "март", "апрел", "ма", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр"
    };

    private static final Map<String, DayOfWeek> RU_WEEKDAYS = Map.of(
            "понедельник", DayOfWeek.MONDAY,
            "вторник", DayOfWeek.TUESDAY,
            "сред", DayOfWeek.WEDNESDAY,
            "четверг", DayOfWeek.THURSDAY,
            "пятниц", DayOfWeek.FRIDAY,
            "суббот", DayOfWeek.SATURDAY,
            "воскресень", DayOfWeek.SUNDAY
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
    private final Map<String, Instant> processedMediaGroups = new ConcurrentHashMap<>();

    private void cleanupMediaGroups() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(10));
        processedMediaGroups.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

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
        boolean reportAckEnabled = settings != null && Boolean.TRUE.equals(settings.getReportAckEnabled());

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

        // In mini-groups, bind the active group chatId to the student safely
        if (message.getChat() != null && (message.getChat().isGroupChat() || message.getChat().isSuperGroupChat())) {
            Long currentChatId = message.getChat().getId();
            if (student.getTgChatId() == null) {
                Optional<Student> existingStudentWithChat = studentRepository.findByTgChatId(currentChatId);
                if (existingStudentWithChat.isEmpty() || Objects.equals(existingStudentWithChat.get().getId(), student.getId())) {
                    student.setTgChatId(currentChatId);
                } else {
                    log.warn("Telegram group {} is already assigned to student #{} ({}); avoiding duplicate key error for student #{}",
                            currentChatId, existingStudentWithChat.get().getId(), existingStudentWithChat.get().getFullName(), student.getId());
                }
            }
        }

        String text = message.getText();
        String caption = message.getCaption();
        String rawContent = StringUtils.hasText(text) ? text : (StringUtils.hasText(caption) ? caption : "");

        // 1. Process Voice / Audio / VideoNote attachments via Whisper STT
        if (message.hasVoice() && (settings == null || Boolean.TRUE.equals(settings.getAiWhisperEnabled()))) {
            String voiceFileId = message.getVoice().getFileId();
            byte[] audioBytes = telegramSenderService.downloadFileBytes(voiceFileId, 20 * 1024 * 1024);
            String voiceTranscript = audioBytes == null ? "" : springAiReportExtractor.transcribeAudio(audioBytes, "voice.ogg");
            boolean isErrorStatus = voiceTranscript.startsWith("[Сбой") || voiceTranscript.startsWith("[Таймаут");
            if (StringUtils.hasText(voiceTranscript) && !isErrorStatus) {
                String existing = report.getVoiceTranscript();
                if (existing == null || !existing.contains(voiceTranscript)) {
                    report.setVoiceTranscript(StringUtils.hasText(existing)
                            ? existing + "\n" + voiceTranscript
                            : voiceTranscript);
                }
                rawContent = (StringUtils.hasText(rawContent) ? rawContent + "\n" : "") + voiceTranscript;
            }
        }

        if (message.hasAudio() && (settings == null || Boolean.TRUE.equals(settings.getAiWhisperEnabled()))) {
            String audioFileId = message.getAudio().getFileId();
            byte[] audioBytes = telegramSenderService.downloadFileBytes(audioFileId, 25 * 1024 * 1024);
            String audioTranscript = audioBytes == null ? "" : springAiReportExtractor.transcribeAudio(audioBytes, "audio.mp3");
            boolean isErrorStatus = audioTranscript.startsWith("[Сбой") || audioTranscript.startsWith("[Таймаут");
            if (StringUtils.hasText(audioTranscript) && !isErrorStatus) {
                String existing = report.getVoiceTranscript();
                if (existing == null || !existing.contains(audioTranscript)) {
                    report.setVoiceTranscript(StringUtils.hasText(existing)
                            ? existing + "\n" + audioTranscript
                            : audioTranscript);
                }
                rawContent = (StringUtils.hasText(rawContent) ? rawContent + "\n" : "") + audioTranscript;
            }
        }

        if (message.hasVideoNote() && (settings == null || Boolean.TRUE.equals(settings.getAiWhisperEnabled()))) {
            String videoFileId = message.getVideoNote().getFileId();
            byte[] videoBytes = telegramSenderService.downloadFileBytes(videoFileId, 25 * 1024 * 1024);
            String videoTranscript = videoBytes == null ? "" : springAiReportExtractor.transcribeAudio(videoBytes, "videonote.mp4");
            boolean isErrorStatus = videoTranscript.startsWith("[Сбой") || videoTranscript.startsWith("[Таймаут");
            if (StringUtils.hasText(videoTranscript) && !isErrorStatus) {
                String existing = report.getVoiceTranscript();
                if (existing == null || !existing.contains(videoTranscript)) {
                    report.setVoiceTranscript(StringUtils.hasText(existing)
                            ? existing + "\n" + videoTranscript
                            : videoTranscript);
                }
                rawContent = (StringUtils.hasText(rawContent) ? rawContent + "\n" : "") + videoTranscript;
            }
        }

        // 2. Process Content Drafts & Attachments (BEFORE publication)
        processAttachmentsAndDrafts(report, student, message, rawContent, deduplicationEnabled);

        // 3. Process Instagram Reels with Anti-Fraud (SHA-256 deduplication & accessibility)
        processReelsAntiFraud(report, student, rawContent, deduplicationEnabled);

        // 4. Sales amount normalization (#продажи 60000 vs #продажа 50BYN, #оплата 100, #предоплата 50, #чек 50)
        boolean hasSalesTag = StringUtils.hasText(rawContent) &&
                (rawContent.contains("#продажи") || rawContent.contains("#продажа")
                        || rawContent.contains("#оплата") || rawContent.contains("#предоплата")
                        || rawContent.contains("#чек") || rawContent.toLowerCase(java.util.Locale.ROOT).contains("продаж"));
        BigDecimal salesAmount = hasSalesTag ? deterministicAiFallbackService.extractSalesAmount(rawContent) : null;
        if (hasSalesTag && salesAmount != null && salesAmount.compareTo(BigDecimal.ZERO) > 0) {
            String entryDesc = rawContent.length() > 200 ? rawContent.substring(0, 200) : rawContent;
            String lowerContent = rawContent.toLowerCase(java.util.Locale.ROOT);
            boolean isPluralSnapshot = lowerContent.contains("#продажи") || lowerContent.contains("#отчет") || lowerContent.contains("#отчёт");
            boolean isIndividualSale = !isPluralSnapshot && (lowerContent.contains("#продажа") || lowerContent.contains("#оплата")
                    || lowerContent.contains("#предоплата") || lowerContent.contains("#чек"));

            if (report.getSalesEntries() == null) {
                report.setSalesEntries(new ArrayList<>());
            }

            BigDecimal currentTotal = report.getSalesTotal() != null ? report.getSalesTotal() : BigDecimal.ZERO;

            if (isIndividualSale) {
                // Individual sale event (#продажа 50BYN, #оплата 100):
                // Deduplicate if identical message text already recorded
                boolean isDuplicateSale = report.getSalesEntries().stream()
                        .anyMatch(se -> entryDesc.equals(se.getDescription()));
                if (!isDuplicateSale) {
                    BigDecimal newTotal = currentTotal.add(salesAmount);
                    report.setSalesTotal(newTotal);
                    student.setCurrentIncome((student.getCurrentIncome() != null ? student.getCurrentIncome() : BigDecimal.ZERO).add(salesAmount));
                    SalesEntry entry = SalesEntry.builder()
                            .report(report)
                            .student(student)
                            .amount(salesAmount)
                            .description(entryDesc)
                            .createdAt(Instant.now())
                            .build();
                    salesEntryRepository.save(entry);
                    report.getSalesEntries().add(entry);
                    log.info("Recorded individual sale: +{} (total now={}) for student '{}'",
                            salesAmount, newTotal, student.getFullName());
                }
            } else {
                // Daily total snapshot (#продажи 60000 or summary #отчет):
                // If salesAmount exceeds current total, update to salesAmount and add delta
                if (salesAmount.compareTo(currentTotal) > 0) {
                    BigDecimal delta = salesAmount.subtract(currentTotal);
                    report.setSalesTotal(salesAmount);
                    student.setCurrentIncome((student.getCurrentIncome() != null ? student.getCurrentIncome() : BigDecimal.ZERO).add(delta));
                    SalesEntry entry = SalesEntry.builder()
                            .report(report)
                            .student(student)
                            .amount(delta)
                            .description(entryDesc)
                            .createdAt(Instant.now())
                            .build();
                    salesEntryRepository.save(entry);
                    report.getSalesEntries().add(entry);
                    log.info("Report sales snapshot updated total to {}: +{} for student '{}'",
                            salesAmount, delta, student.getFullName());
                }
            }
        }

        // 5. Mailings / Outreach count (#рассылки 40 or #отчет 10 рассылок, #трафик 20, #диалоги 10)
        boolean hasMailingsTag = StringUtils.hasText(rawContent) &&
                (rawContent.contains("#рассылки") || rawContent.contains("#рассылка")
                        || rawContent.contains("#трафик") || rawContent.contains("#диалоги")
                        || rawContent.toLowerCase(java.util.Locale.ROOT).contains("рассыл")
                        || rawContent.contains("#отчет") || rawContent.contains("#отчёт"));
        Integer mailings = hasMailingsTag ? deterministicAiFallbackService.extractMailingsCount(rawContent) : null;
        if (hasMailingsTag && mailings != null && mailings >= 0) {
            int prevMailings = report.getMailingsCount() != null ? report.getMailingsCount() : 0;
            int targetMailings = Math.max(prevMailings, mailings);
            int deltaMailings = targetMailings - prevMailings;
            report.setMailingsCount(targetMailings);

            int totalMailings = student.getTotalMailings() != null ? student.getTotalMailings() : 0;
            student.setTotalMailings(totalMailings + deltaMailings);
            log.info("Report mailings updated: prev={}, new={}, delta={} for student '{}'",
                    prevMailings, targetMailings, deltaMailings, student.getFullName());
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
        if (isValidQuestion(extractedQuestion)) {
            appendQuestion(report, extractedQuestion);
        }

        // 8. Spring AI 3-bullet reflection & Burnout Sentiment Detection
        boolean isReportMessage = StringUtils.hasText(rawContent) &&
                (rawContent.contains("#рассылки") || rawContent.contains("#продажи") || rawContent.contains("#рилс") || rawContent.contains("#карусель") || rawContent.contains("#сторителл") || rawContent.contains("#отчет") || rawContent.contains("#отчёт"));

        String aiContext;
        if (isReportMessage) {
            aiContext = rawContent;
            if (StringUtils.hasText(report.getVoiceTranscript()) && !rawContent.contains(report.getVoiceTranscript())) {
                aiContext = aiContext + "\n[Голосовое сообщение]: " + report.getVoiceTranscript();
            }
        } else {
            aiContext = StringUtils.hasText(report.getRawMessageText())
                    ? report.getRawMessageText() + (StringUtils.hasText(rawContent) && !report.getRawMessageText().contains(rawContent) ? "\n---\n" + rawContent : "")
                    : rawContent;
        }

        ExtractedReportDto aiFacts = settings == null || Boolean.TRUE.equals(settings.getAiSummaryEnabled())
                ? springAiReportExtractor.extractFacts(aiContext, student.getFullName(), student.getCurrency())
                : null;
        if (aiFacts != null) {
            if (aiFacts.reflectionSummary() != null) {
                if (StringUtils.hasText(aiFacts.reflectionSummary().emotions())) {
                    report.setReflectionEmotions(aiFacts.reflectionSummary().emotions());
                }

                String currentActions = report.getReflectionActions();
                String aiActions = aiFacts.reflectionSummary().actions();
                if (StringUtils.hasText(aiActions)) {
                    String cleanAiActions = aiActions.trim().replaceFirst("^✅\\s*Факт:\\s*", "");
                    String focusLine = extractFocusLine(currentActions);
                    if (StringUtils.hasText(focusLine)) {
                        report.setReflectionActions(focusLine + "\n✅ Факт: " + cleanAiActions);
                    } else {
                        report.setReflectionActions("✅ Факт: " + cleanAiActions);
                    }
                }

                if (aiFacts.reflectionSummary().questionToMentor() != null
                        && isValidQuestion(aiFacts.reflectionSummary().questionToMentor())) {
                    String cleanQ = aiFacts.reflectionSummary().questionToMentor().trim();
                    report.setReflectionQuestion(cleanQ);
                    if (!StringUtils.hasText(extractedQuestion)) {
                        appendQuestion(report, cleanQ);
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

        boolean hasMediaAttachment = message != null && (message.hasVideo() || message.hasDocument() || message.hasPhoto());
        boolean hasNewReportContent = newDraftsAdded > 0 || newReelsAdded > 0 || hasSales || hasMailings || hasClients || hasQuestion;
        boolean isDuplicateMediaSubmission = hasMediaAttachment && !hasNewReportContent;
        boolean isReport = !isDuplicateMediaSubmission &&
                isReportSubmission(message, rawContent, newDraftsAdded, newReelsAdded, hasSales, hasMailings, hasClients, hasQuestion);

        String mediaGroupId = message != null ? message.getMediaGroupId() : null;
        boolean isAlbumFollowup = false;
        if (StringUtils.hasText(mediaGroupId)) {
            cleanupMediaGroups();
            isAlbumFollowup = processedMediaGroups.putIfAbsent(mediaGroupId, Instant.now()) != null;
        }

        if (isReport) {
            report.setSubmittedAt(Instant.now());
            report.setIsOnTime(isOnTime);
            report.setStatus("PENDING");
            report.setLastTgMessageId(message.getMessageId());

            if (!isAlbumFollowup) {
                // 10. Send caring acknowledgment reply in mini-group only when actual report submitted and reportAckEnabled is true
                if (reportAckEnabled) {
                    Long chatId = message.getChat() != null ? message.getChat().getId() : student.getTgChatId();
                    String timeStr = TIME_FORMATTER.format(report.getSubmittedAt());
                    String acknowledgement = wasAlreadySubmitted
                            ? "Принято на проверку (" + timeStr + ")! ✍️ Черновик обновлён."
                            : "Принято на проверку (" + timeStr + ")! ✍️ Черновик сохранён.";
                    String businessConnectionId = message.getBusinessConnectionId();
                    if (StringUtils.hasText(businessConnectionId)) {
                        telegramSenderService.sendCaringReply(chatId, message.getMessageId(), acknowledgement, businessConnectionId);
                    } else {
                        telegramSenderService.sendCaringReply(chatId, message.getMessageId(), acknowledgement);
                    }
                } else {
                    Long chatId = message.getChat() != null ? message.getChat().getId() : student.getTgChatId();
                    if (telegramSenderService != null && message.getMessageId() != null) {
                        telegramSenderService.setMessageReaction(chatId, message.getMessageId(), "👍");
                    }
                    log.info("Report acknowledgment reply skipped (reportAckEnabled=false) for student '{}'", student.getFullName());
                }
            } else {
                log.info("Media group update {} for student '{}' merged silently into report without duplicate ack/reaction",
                        mediaGroupId, student.getFullName());
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
    public DailyReport recordDailyFocusSilently(Student student, String focusText) {
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

        String cleanFocus = focusText != null ? focusText.trim() : "";
        report.setReflectionActions("🎯 Фокус дня: " + cleanFocus);
        if (!StringUtils.hasText(report.getAiSummary())) {
            report.setAiSummary("Фокус на сегодня: " + cleanFocus);
        }
        String existingRaw = report.getRawMessageText();
        report.setRawMessageText(StringUtils.hasText(existingRaw)
                ? existingRaw + "\n[Фокус дня]: " + cleanFocus
                : "[Фокус дня]: " + cleanFocus);

        return dailyReportRepository.save(report);
    }

    @Transactional
    public DailyReport handleMorningFocusMessage(Student student, Message message, String rawContent) {
        DailyReport savedReport = recordDailyFocusSilently(student, rawContent);
        String cleanFocus = rawContent != null ? rawContent.trim() : "";

        Long chatId = message.getChat() != null ? message.getChat().getId() : student.getTgChatId();
        MentorSetting settings = mentorSettingRepository != null
                ? mentorSettingRepository.findFirstByOrderByIdAsc()
                        .or(() -> mentorSettingRepository.findAll().stream().findFirst())
                        .orElse(null)
                : null;
        boolean reportAckEnabled = settings != null && Boolean.TRUE.equals(settings.getReportAckEnabled());

        if (reportAckEnabled) {
            String ack = "🎯 Цель на сегодня зафиксирована: «" + cleanFocus + "»!\nЖдём результаты в отчёте до 21:00 🌸";
            String businessConnectionId = message.getBusinessConnectionId();
            if (StringUtils.hasText(businessConnectionId)) {
                telegramSenderService.sendCaringReply(chatId, message.getMessageId(), ack, businessConnectionId);
            } else {
                telegramSenderService.sendCaringReply(chatId, message.getMessageId(), ack);
            }
        } else {
            // Quiet mode: react silently with target/thumbs up on the student's message
            if (telegramSenderService != null && message.getMessageId() != null) {
                telegramSenderService.setMessageReaction(chatId, message.getMessageId(), "🎯");
            }
            log.info("Morning focus acknowledgment skipped (reportAckEnabled=false) for student '{}'", student.getFullName());
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
        // Video files, documents, and photos are explicit content draft submissions.
        // Voice messages, audio, and video notes (кружочки) are communication/reflection media
        // and should NOT automatically trigger report submission unless they contain report hashtags or keywords.
        if (message != null && (message.hasVideo() || message.hasDocument() || message.hasPhoto())) {
            return true;
        }
        if (newDraftsAdded > 0 || newReelsAdded > 0 || hasSales || hasMailings || hasClients || hasQuestion) {
            return true;
        }
        if (StringUtils.hasText(rawContent)) {
            String lower = rawContent.trim().toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("#рилс") || lower.contains("#видео") || lower.contains("#карусель")
                    || lower.contains("#пост") || lower.contains("#сторителл") || lower.contains("#сторис")
                    || lower.contains("#контент") || lower.contains("#рассылки") || lower.contains("#рассылка")
                    || lower.contains("#трафик") || lower.contains("#диалоги") || lower.contains("#продажи")
                    || lower.contains("#продажа") || lower.contains("#оплата") || lower.contains("#предоплата")
                    || lower.contains("#чек") || lower.contains("#вопрос") || lower.contains("#клиенты")
                    || lower.contains("#отчет") || lower.contains("#отчёт") || lower.contains("#дз")
                    || lower.contains("#задание") || lower.contains("#reels") || lower.contains("#stories")) {
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

        // Voice messages and video notes (кружочки) should NOT be registered as ContentDrafts (Reels),
        // they are communication/reflection media and scanned silently unless accompanied by report hashtags.

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

        // Photos attachment -> Carousel draft (exclude sales check/receipt photos and standalone question screenshots)
        if (message.hasPhoto() && !message.getPhoto().isEmpty()) {
            String lower = rawContent != null ? rawContent.toLowerCase(java.util.Locale.ROOT) : "";
            boolean isSalesProofOrQuestion = (lower.contains("#чек") || lower.contains("#оплата") || lower.contains("#предоплата")
                    || lower.contains("#продажа") || lower.contains("#продажи") || lower.contains("#вопрос"))
                    && !lower.contains("#карусель") && !lower.contains("#пост") && !lower.contains("#контент");
            if (!isSalesProofOrQuestion) {
                List<PhotoSize> photos = message.getPhoto();
                PhotoSize largest = photos.stream().max(Comparator.comparing(PhotoSize::getFileSize)).orElse(photos.get(photos.size() - 1));
                boolean added = createOrUpdateDraft(report, student, "CAROUSEL", "Черновик карусели (фото)", null, largest.getFileId(), largest.getFileUniqueId(), deduplicationEnabled);
                if (added) {
                    boolean isSubsequentAlbumPhoto = message.getMediaGroupId() != null
                            && report.getContentDrafts() != null
                            && report.getContentDrafts().stream().filter(d -> "CAROUSEL".equals(d.getDraftType())).count() > 1;
                    if (!isSubsequentAlbumPhoto) {
                        student.setTotalCarousels((student.getTotalCarousels() != null ? student.getTotalCarousels() : 0) + 1);
                    }
                }
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

            // Natural language content counts in summary report (e.g. #отчет 10 рассылок 3 рилса 5 публикаций)
            String lower = rawContent.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("#отчет") || lower.contains("#отчёт") || lower.startsWith("отчет") || lower.startsWith("отчёт")) {
                Integer reelsCount = deterministicAiFallbackService.extractReelsCount(rawContent);
                if (reelsCount != null && reelsCount > 0) {
                    String label = "Отчёт: " + reelsCount + " Reels";
                    boolean added = createOrUpdateDraft(report, student, "REEL", label, null, null, null, deduplicationEnabled);
                    if (added) {
                        student.setTotalReels((student.getTotalReels() != null ? student.getTotalReels() : 0) + reelsCount);
                    }
                }
                Integer postsCount = deterministicAiFallbackService.extractPostsCount(rawContent);
                if (postsCount != null && postsCount > 0) {
                    String label = "Отчёт: " + postsCount + " публикаций";
                    boolean added = createOrUpdateDraft(report, student, "CAROUSEL", label, null, null, null, deduplicationEnabled);
                    if (added) {
                        student.setTotalCarousels((student.getTotalCarousels() != null ? student.getTotalCarousels() : 0) + postsCount);
                    }
                }
            }
        }
    }

    private boolean createOrUpdateDraft(DailyReport report, Student student, String draftType, String label,
                                        String url, String fileTgId, String fileUniqueId, boolean deduplicationEnabled) {
        if (report.getContentDrafts() != null) {
            boolean textOnlyDraft = !StringUtils.hasText(fileTgId)
                    && !StringUtils.hasText(fileUniqueId)
                    && !StringUtils.hasText(url);
            boolean alreadyPresent = report.getContentDrafts().stream().anyMatch(d ->
                    (StringUtils.hasText(fileTgId) && Objects.equals(d.getFileTgId(), fileTgId))
                    || (StringUtils.hasText(fileUniqueId) && Objects.equals(d.getFileUniqueId(), fileUniqueId))
                    || (StringUtils.hasText(url) && (Objects.equals(d.getUrl(), url) || (d.getLabel() != null && d.getLabel().contains(url))))
                    || (StringUtils.hasText(url) && d.getUrl() != null && (d.getUrl().contains(url) || url.contains(d.getUrl())))
                    || (textOnlyDraft && Objects.equals(d.getLabel(), label) && Objects.equals(d.getDraftType(), draftType))
                    || (textOnlyDraft && label != null && d.getLabel() != null && (label.contains(d.getLabel()) || d.getLabel().contains(label)))
            );
            if (alreadyPresent) {
                log.info("Draft '{}' already present in report #{}, skipping duplicate", label, report.getId());
                return false;
            }
        }

        boolean isDuplicate = false;
        String duplicateDate = null;

        if (deduplicationEnabled && StringUtils.hasText(fileUniqueId)
                && contentDraftRepository.existsByFileUniqueId(fileUniqueId)) {
            isDuplicate = true;
            duplicateDate = "ранее";
        } else if (deduplicationEnabled && StringUtils.hasText(fileTgId)) {
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
                .fileUniqueId(fileUniqueId)
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

            // Add corresponding ContentDraft if not already present
            boolean draftAlreadyExists = report.getContentDrafts() != null && report.getContentDrafts().stream()
                    .anyMatch(d -> (eval.canonicalUrl() != null && (Objects.equals(d.getUrl(), eval.canonicalUrl()) || (d.getLabel() != null && d.getLabel().contains(eval.canonicalUrl()))))
                            || (eval.rawUrl() != null && (Objects.equals(d.getUrl(), eval.rawUrl()) || (d.getLabel() != null && d.getLabel().contains(eval.rawUrl()))))
                            || (eval.shortcode() != null && ((d.getUrl() != null && d.getUrl().contains(eval.shortcode())) || (d.getLabel() != null && d.getLabel().contains(eval.shortcode())))));

            if (!draftAlreadyExists) {
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
            }

            if (!eval.isDuplicate()) {
                student.setTotalReels((student.getTotalReels() != null ? student.getTotalReels() : 0) + 1);
            }
        }
    }

    public boolean isMeetingMessage(Message message) {
        if (message == null) return false;
        String rawContent = message.getText() != null ? message.getText() : message.getCaption();
        if (!StringUtils.hasText(rawContent)) return false;
        String lower = rawContent.toLowerCase(Locale.ROOT);
        return lower.contains("#созвон") || lower.contains("#zoom") || lower.contains("#встреча")
                || lower.startsWith("/созвон") || lower.startsWith("/meeting") || lower.startsWith("/zoom")
                || containsVideoMeetingLink(message);
    }

    @Transactional
    public boolean captureZoomLinkFromMessage(Student student, Message message) {
        return processMeetingMessage(student, message);
    }

    @Transactional
    public boolean processMeetingMessage(Student student, Message message) {
        if (zoomMeetingRepository == null || student == null || message == null) return false;
        MentorSetting settings = mentorSettingRepository != null
                ? mentorSettingRepository.findFirstByOrderByIdAsc()
                        .or(() -> mentorSettingRepository.findAll().stream().findFirst())
                        .orElse(null)
                : null;
        if (settings != null && !Boolean.TRUE.equals(settings.getZoomAutoDetectEnabled())) return false;
        String rawContent = message.getText() != null ? message.getText() : message.getCaption();
        if (!StringUtils.hasText(rawContent)) return false;

        String lower = rawContent.toLowerCase(Locale.ROOT);
        boolean hasMeetingTag = lower.contains("#созвон") || lower.contains("#zoom") || lower.contains("#встреча")
                || lower.startsWith("/созвон") || lower.startsWith("/meeting") || lower.startsWith("/zoom");

        Matcher matcher = URL_PATTERN.matcher(rawContent);
        String link = null;
        boolean isRecording = false;
        while (matcher.find()) {
            String candidate = matcher.group().replaceAll("[),.;!?]+$", "");
            try {
                URI uri = URI.create(candidate);
                String host = uri.getHost();
                if (!isSupportedMeetingHost(host)) continue;
                link = candidate;
                String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
                boolean isZoom = "zoom.us".equalsIgnoreCase(host) || host.toLowerCase(Locale.ROOT).endsWith(".zoom.us");
                isRecording = isZoom && (path.contains("/rec/") || path.contains("/recording/"));
                break;
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed URLs embedded in a message.
            }
        }

        ZoneId studentZone = resolveStudentZone(student);
        Instant scheduledAt = parseMeetingDateTime(rawContent, studentZone);

        // If neither link nor scheduled time nor hashtag was found, not a meeting message
        if (link == null && scheduledAt == null && !hasMeetingTag) {
            return false;
        }

        final String capturedLink = link;
        List<ZoomMeeting> sessions = zoomMeetingRepository.findByStudentIdOrderByCallNumberAscIdAsc(student.getId());
        Long chatId = message.getChat() != null ? message.getChat().getId() : null;
        Integer messageId = message.getMessageId();
        if (chatId != null && messageId != null && sessions.stream().anyMatch(session ->
                Objects.equals(session.getSourceChatId(), chatId) && Objects.equals(session.getSourceMessageId(), messageId))) {
            return true;
        }

        Instant messageAt = message.getDate() != null
                ? Instant.ofEpochSecond(message.getDate().longValue())
                : Instant.now();
        final boolean recordingLink = isRecording;

        ZoomMeeting meeting = sessions.stream()
                .filter(session -> recordingLink
                        ? Objects.equals(session.getRecordingUrl(), capturedLink)
                        : (capturedLink != null && Objects.equals(session.getZoomUrl(), capturedLink)))
                .findFirst()
                .orElseGet(() -> sessions.stream()
                .filter(session -> recordingLink
                        ? StringUtils.hasText(session.getZoomUrl()) && !StringUtils.hasText(session.getRecordingUrl())
                        : !Boolean.TRUE.equals(session.getIsCompleted()))
                .findFirst()
                .orElseGet(() -> {
                    int nextCallNumber = sessions.stream()
                            .map(ZoomMeeting::getCallNumber)
                            .filter(Objects::nonNull)
                            .max(Integer::compareTo)
                            .orElse(0) + 1;
                    return ZoomMeeting.builder()
                            .student(student)
                            .callNumber(nextCallNumber)
                            .meetingTitle("Консультация #" + nextCallNumber)
                            .isCompleted(false)
                            .build();
                }));

        if (recordingLink) {
            meeting.setRecordingUrl(capturedLink);
            meeting.setIsCompleted(true);
            meeting.setCompletedAt(messageAt);
        } else {
            if (capturedLink != null) {
                meeting.setZoomUrl(capturedLink);
                meeting.setLinkSentAt(messageAt);
            }
            if (scheduledAt != null) {
                meeting.setScheduledAt(scheduledAt);
            }
        }

        meeting.setSourceChatId(chatId);
        meeting.setSourceMessageId(messageId);
        String evidenceUrl = buildTelegramMessageUrl(message);
        if (StringUtils.hasText(evidenceUrl)) meeting.setEvidenceUrl(evidenceUrl);
        zoomMeetingRepository.save(meeting);

        // Recalculate nextZoomAt and completedZoomsCount on student if recording or schedule changed
        if (recordingLink || scheduledAt != null) {
            student.setCompletedZoomsCount(Math.toIntExact(zoomMeetingRepository.countByStudentIdAndIsCompletedTrue(student.getId())));
            student.setNextZoomAt(zoomMeetingRepository.findByStudentIdOrderByCallNumberAscIdAsc(student.getId()).stream()
                    .filter(item -> !Boolean.TRUE.equals(item.getIsCompleted()))
                    .map(ZoomMeeting::getScheduledAt)
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(null));
            studentRepository.save(student);
        }

        // Send caring confirmation to the group chat only when reportAckEnabled is true; otherwise react quietly
        if (telegramSenderService != null && chatId != null) {
            boolean reportAckEnabled = settings != null && Boolean.TRUE.equals(settings.getReportAckEnabled());
            if (!reportAckEnabled) {
                telegramSenderService.setMessageReaction(chatId, messageId, "📅");
                log.info("Meeting confirmation text skipped (reportAckEnabled=false), reaction set for student '{}'", student.getFullName());
            } else {
                String businessConnectionId = settings != null && Boolean.TRUE.equals(settings.getIsBusinessConnected())
                        ? settings.getBusinessConnectionId()
                        : null;
                if (recordingLink) {
                    String reply = "📼 Запись созвона для " + student.getFullName() + " сохранена в карточку! 🌸";
                    telegramSenderService.sendCaringReply(chatId, messageId, reply, businessConnectionId);
                } else if (scheduledAt != null) {
                    DateTimeFormatter df = DateTimeFormatter.ofPattern("dd.MM (EE)", new Locale("ru"));
                    DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");
                    LocalDateTime ldt = LocalDateTime.ofInstant(scheduledAt, studentZone);
                    String linkPart = capturedLink != null ? "\n🔗 Ссылка: " + capturedLink : "";
                    String reply = String.format(
                            "📅 Созвон с %s назначен на %s в %s! 🌸\nДобавила в график и карточку ученицы.%s",
                            student.getFullName(),
                            df.format(ldt),
                            tf.format(ldt),
                            linkPart
                    );
                    telegramSenderService.sendCaringReply(chatId, messageId, reply, businessConnectionId);
                } else if (capturedLink != null) {
                    String reply = "🔗 Ссылка на созвон для " + student.getFullName() + " сохранена в карточку! 🌸\n" + capturedLink;
                    telegramSenderService.sendCaringReply(chatId, messageId, reply, businessConnectionId);
                }
            }
        }

        return true;
    }

    public Instant parseMeetingDateTime(String text, ZoneId zone) {
        if (!StringUtils.hasText(text)) return null;
        String lower = text.toLowerCase(Locale.ROOT);

        // 1. Parse time
        LocalTime targetTime = null;
        Matcher mTime = MEETING_TIME_PATTERN.matcher(lower);
        if (mTime.find()) {
            int h = Integer.parseInt(mTime.group(1));
            int m = Integer.parseInt(mTime.group(2));
            if (h >= 0 && h < 24 && m >= 0 && m < 60) {
                targetTime = LocalTime.of(h, m);
            }
        } else {
            Matcher mHour = MEETING_HOUR_PATTERN.matcher(lower);
            if (mHour.find()) {
                int h = Integer.parseInt(mHour.group(1));
                if (h >= 0 && h < 24) {
                    targetTime = LocalTime.of(h, 0);
                }
            }
        }

        // 2. Parse date
        LocalDate targetDate = null;
        if (lower.contains("послезавтра")) {
            targetDate = LocalDate.now(zone).plusDays(2);
        } else if (lower.contains("завтра")) {
            targetDate = LocalDate.now(zone).plusDays(1);
        } else if (lower.contains("сегодня")) {
            targetDate = LocalDate.now(zone);
        }

        if (targetDate == null) {
            Matcher mNum = MEETING_NUM_DATE_PATTERN.matcher(lower);
            if (mNum.find()) {
                int d = Integer.parseInt(mNum.group(1));
                int mo = Integer.parseInt(mNum.group(2));
                int y = mNum.group(3) != null ? Integer.parseInt(mNum.group(3)) : LocalDate.now(zone).getYear();
                if (y < 100) y += 2000;
                if (mo >= 1 && mo <= 12 && d >= 1 && d <= 31) {
                    try {
                        targetDate = LocalDate.of(y, mo, d);
                    } catch (Exception ignored) {}
                }
            }
        }

        if (targetDate == null) {
            Matcher mRu = MEETING_RU_DATE_PATTERN.matcher(lower);
            if (mRu.find()) {
                int d = Integer.parseInt(mRu.group(1));
                String monStr = mRu.group(2);
                for (int i = 0; i < RU_MONTHS.length; i++) {
                    if (monStr.startsWith(RU_MONTHS[i])) {
                        try {
                            targetDate = LocalDate.of(LocalDate.now(zone).getYear(), i + 1, d);
                        } catch (Exception ignored) {}
                        break;
                    }
                }
            }
        }

        if (targetDate == null) {
            for (Map.Entry<String, DayOfWeek> entry : RU_WEEKDAYS.entrySet()) {
                if (lower.contains(entry.getKey())) {
                    LocalDate base = LocalDate.now(zone);
                    DayOfWeek targetDow = entry.getValue();
                    int diff = (targetDow.getValue() - base.getDayOfWeek().getValue() + 7) % 7;
                    if (diff == 0) diff = 7;
                    targetDate = base.plusDays(diff);
                    break;
                }
            }
        }

        if (targetDate == null && targetTime == null) {
            return null;
        }

        if (targetDate == null) {
            LocalDate today = LocalDate.now(zone);
            LocalDateTime candidate = LocalDateTime.of(today, targetTime);
            if (candidate.isBefore(LocalDateTime.now(zone))) {
                targetDate = today.plusDays(1);
            } else {
                targetDate = today;
            }
        }

        if (targetTime == null) {
            targetTime = LocalTime.of(15, 0); // Default to 15:00 if date given without time
        }

        return LocalDateTime.of(targetDate, targetTime).atZone(zone).toInstant();
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

    public boolean containsVideoMeetingLink(Message message) {
        if (message == null) return false;
        String rawContent = message.getText() != null ? message.getText() : message.getCaption();
        if (!StringUtils.hasText(rawContent)) return false;
        Matcher matcher = URL_PATTERN.matcher(rawContent);
        while (matcher.find()) {
            try {
                if (isSupportedMeetingHost(URI.create(matcher.group().replaceAll("[),.;!?]+$", "")).getHost())) return true;
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed URLs embedded in a message.
            }
        }
        return false;
    }

    private boolean isSupportedMeetingHost(String host) {
        if (!StringUtils.hasText(host)) return false;
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return normalizedHost.equals("zoom.us") || normalizedHost.endsWith(".zoom.us")
                || normalizedHost.equals("meet.google.com")
                || normalizedHost.equals("telemost.yandex.ru") || normalizedHost.equals("telemost.yandex.com");
    }

    private String buildTelegramMessageUrl(Message message) {
        if (message.getChat() == null || message.getMessageId() == null) return null;
        String username = message.getChat().getUserName();
        if (StringUtils.hasText(username)) return "https://t.me/" + username + "/" + message.getMessageId();
        Long chatId = message.getChat().getId();
        if (chatId != null && message.getChat().isSuperGroupChat() && chatId.toString().startsWith("-100")) {
            return "https://t.me/c/" + chatId.toString().replaceFirst("^-100", "") + "/" + message.getMessageId();
        }
        return null;
    }

    private boolean isValidQuestion(String question) {
        if (!StringUtils.hasText(question)) return false;
        String q = question.trim().toLowerCase();
        return !q.equals("null") && !q.equals("none") && !q.equals("nil") && !q.equals("нет")
                && !q.equals("нет вопросов") && !q.equals("вопросов нет") && !q.equals("n/a")
                && !q.equals("undefined") && !q.equals("-");
    }

    private String extractFocusLine(String currentActions) {
        if (!StringUtils.hasText(currentActions)) return null;
        for (String line : currentActions.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("🎯 Фокус дня:") || trimmed.startsWith("🎯")) {
                return trimmed;
            }
        }
        return null;
    }

    private void appendQuestion(DailyReport report, String questionText) {
        if (!isValidQuestion(questionText)) {
            return;
        }
        String cleanQuestion = questionText.trim();
        String existingQuestions = report.getMentorQuestion();
        if (StringUtils.hasText(existingQuestions) && existingQuestions.contains(cleanQuestion)) {
            return;
        }

        String timeStr = TIME_FORMATTER.format(Instant.now());
        String formattedSnippet = "[" + timeStr + "] " + cleanQuestion;

        if (!StringUtils.hasText(existingQuestions)) {
            report.setMentorQuestion(formattedSnippet);
        } else {
            report.setMentorQuestion(existingQuestions + "\n" + formattedSnippet);
        }
    }
}
