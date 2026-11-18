package com.mentorhub.telegram.router;

import com.mentorhub.entity.AdminToken;
import com.mentorhub.entity.AuditMessageLog;
import com.mentorhub.entity.MentorSetting;
import com.mentorhub.entity.Student;
import com.mentorhub.entity.TaskRule;
import com.mentorhub.repository.AdminTokenRepository;
import com.mentorhub.repository.AuditMessageLogRepository;
import com.mentorhub.repository.MentorSettingRepository;
import com.mentorhub.repository.StudentRepository;
import com.mentorhub.repository.TaskRuleRepository;
import com.mentorhub.scheduler.CallbackQueryHandler;
import com.mentorhub.service.StudentService;
import com.mentorhub.telegram.handler.TelegramReportHandler;
import com.mentorhub.telegram.service.TelegramSenderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.business.BusinessConnection;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TelegramMessageRouter {

    private final StudentRepository studentRepository;
    private final MentorSettingRepository mentorSettingRepository;
    private final AdminTokenRepository adminTokenRepository;
    private final AuditMessageLogRepository auditMessageLogRepository;
    private final TelegramReportHandler telegramReportHandler;
    private final CallbackQueryHandler callbackQueryHandler;
    private final TelegramSenderService telegramSenderService;

    @Autowired(required = false)
    private TaskRuleRepository taskRuleRepository;

    @Autowired(required = false)
    private StudentService studentService;

    private static final ZoneId MINSK_ZONE = ZoneId.of("Europe/Minsk");

    private static final Pattern BOT_QUERY_PATTERN =
            Pattern.compile("(?ui)(?:^|[^\\p{L}\\p{N}_])(бот|bot)(?:[^\\p{L}\\p{N}_]|$)");
    private static final Pattern GREETING_WORDS_PATTERN =
            Pattern.compile("(?ui)(привет|приветик|здравствуй|здравствуйте|добрый день|доброе утро|добрый вечер|ку|хай|йоу|хэй|хеллоу|hello|hi)");

    public TelegramMessageRouter(
            StudentRepository studentRepository,
            MentorSettingRepository mentorSettingRepository,
            AdminTokenRepository adminTokenRepository,
            AuditMessageLogRepository auditMessageLogRepository,
            TelegramReportHandler telegramReportHandler,
            CallbackQueryHandler callbackQueryHandler) {
        this(studentRepository, mentorSettingRepository, adminTokenRepository, auditMessageLogRepository, telegramReportHandler, callbackQueryHandler, null);
    }

    @Autowired
    public TelegramMessageRouter(
            StudentRepository studentRepository,
            MentorSettingRepository mentorSettingRepository,
            AdminTokenRepository adminTokenRepository,
            AuditMessageLogRepository auditMessageLogRepository,
            TelegramReportHandler telegramReportHandler,
            CallbackQueryHandler callbackQueryHandler,
            @Autowired(required = false) TelegramSenderService telegramSenderService) {
        this.studentRepository = studentRepository;
        this.mentorSettingRepository = mentorSettingRepository;
        this.adminTokenRepository = adminTokenRepository;
        this.auditMessageLogRepository = auditMessageLogRepository;
        this.telegramReportHandler = telegramReportHandler;
        this.callbackQueryHandler = callbackQueryHandler;
        this.telegramSenderService = telegramSenderService;
    }

    @Transactional
    public void routeUpdate(Update update) {
        if (update == null) {
            return;
        }

        if (update.hasBusinessConnection()) {
            handleBusinessConnection(update.getBusinessConnection(), update.getUpdateId());
            return;
        }

        // 1. Handle interactive callback queries (Saturday survey inline buttons)
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery(), update.getUpdateId());
            return;
        }

        // 2. Extract message from various sources
        Message message = null;
        String sourceType = "DIRECT_BOT";

        if (update.hasBusinessMessage()) {
            message = update.getBusinessMessage();
            sourceType = "BUSINESS_1ON1";
        } else if (update.hasEditedBusinessMessage()) {
            message = update.getEditedBuinessMessage();
            sourceType = "BUSINESS_1ON1";
        } else if (update.hasMessage()) {
            message = update.getMessage();
            if (message.getChat() != null && (message.getChat().isGroupChat() || message.getChat().isSuperGroupChat())) {
                sourceType = "MINI_GROUP";
            } else {
                sourceType = "DIRECT_BOT";
            }
        } else if (update.hasEditedMessage()) {
            message = update.getEditedMessage();
            sourceType = "MINI_GROUP";
        }

        if (message == null) {
            log.debug("Update ID {} has no message content to route", update.getUpdateId());
            return;
        }

        routeIncomingMessage(message, update.getUpdateId() != null ? update.getUpdateId().longValue() : 0L, sourceType);
    }

    private void handleBusinessConnection(BusinessConnection connection, Integer updateId) {
        if (connection == null || connection.getUserChatId() == null || !StringUtils.hasText(connection.getId())) {
            log.warn("Ignoring incomplete Telegram business_connection update {}", updateId);
            return;
        }
        MentorSetting setting = mentorSettingRepository.findByMentorTgId(connection.getUserChatId())
                .or(() -> mentorSettingRepository.findFirstByOrderByIdAsc())
                .orElseGet(() -> MentorSetting.builder()
                        .mentorTgId(connection.getUserChatId())
                        .mentorName(connection.getUser() != null && StringUtils.hasText(connection.getUser().getFirstName())
                                ? connection.getUser().getFirstName()
                                : "Наставник")
                        .build());
        boolean enabled = Boolean.TRUE.equals(connection.getIsEnabled());
        setting.setBusinessConnectionId(enabled ? connection.getId() : null);
        setting.setIsBusinessConnected(enabled);
        mentorSettingRepository.save(setting);
        auditMessageLogRepository.save(AuditMessageLog.builder()
                .updateId(updateId == null ? 0L : updateId.longValue())
                .tgChatId(connection.getUserChatId())
                .tgUserId(connection.getUser() == null ? null : connection.getUser().getId())
                .sourceType("BUSINESS_CONNECTION")
                .rawPayload(connection.getId())
                .processingStatus(enabled ? "CONNECTED" : "DISCONNECTED")
                .createdAt(Instant.now())
                .build());
        log.info("Telegram Business connection updated: enabled={}", enabled);
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery, Integer updateId) {
        try {
            boolean handled = callbackQueryHandler.handleCallbackQuery(callbackQuery);
            log.info("Processed CallbackQuery ID {}: handled={}", callbackQuery.getId(), handled);

            auditMessageLogRepository.save(AuditMessageLog.builder()
                    .updateId(updateId != null ? updateId.longValue() : 0L)
                    .tgChatId(callbackQuery.getMessage() != null ? callbackQuery.getMessage().getChatId() : null)
                    .tgUserId(callbackQuery.getFrom() != null ? callbackQuery.getFrom().getId() : null)
                    .sourceType("CALLBACK_QUERY")
                    .rawPayload(callbackQuery.getData())
                    .processingStatus(handled ? "PROCESSED" : "UNMATCHED_QUERY")
                    .createdAt(Instant.now())
                    .build());
        } catch (Exception ex) {
            log.error("Error processing callback query ID {}: {}", callbackQuery.getId(), ex.getMessage(), ex);
        }
    }

    public void routeIncomingMessage(Message message, Long updateId, String sourceType) {
        // Telegram puts the inviter in Message.from and the actual joiners in
        // new_chat_members. Never use the inviter as the student identity.
        if (isServiceMessage(message)) {
            Long chatId = message.getChat() != null ? message.getChat().getId() : null;
            handleNewChatMembers(message, updateId, sourceType, chatId);
            auditMessageLogRepository.save(AuditMessageLog.builder()
                    .updateId(updateId == null ? 0L : updateId)
                    .tgChatId(chatId)
                    .tgUserId(message.getFrom() == null ? null : message.getFrom().getId())
                    .sourceType(sourceType)
                    .rawPayload("[Telegram service event]")
                    .processingStatus("SERVICE_EVENT_IGNORED")
                    .createdAt(Instant.now())
                    .build());
            return;
        }

        User from = message.getFrom();
        if (from == null) {
            log.debug("Message without author ignored: msgId={}", message.getMessageId());
            return;
        }

        // Filter out bot messages
        if (Boolean.TRUE.equals(from.getIsBot())) {
            log.debug("Bot echo ignored: from bot id={}, username={}", from.getId(), from.getUserName());
            return;
        }

        Long fromId = from.getId();
        Long chatId = message.getChat() != null ? message.getChat().getId() : null;
        String rawText = message.getText() != null ? message.getText() : message.getCaption();

        // Welcome / Help / Rules handling
        if (rawText != null) {
            String lower = rawText.trim().toLowerCase(Locale.ROOT);
            if (lower.startsWith("/start") || lower.startsWith("/help")) {
                handleWelcomeCommand(chatId, fromId, from, message);
                return;
            }
            if (lower.startsWith("/rules") || lower.startsWith("/tasks") || lower.startsWith("/задания")
                    || lower.startsWith("/хэштеги") || lower.startsWith("/хештеги") || lower.startsWith("/регламент")) {
                handleRulesCommand(chatId, message);
                return;
            }
        }

        // Allow binding group to student via command
        if (rawText != null) {
            String lower = rawText.trim().toLowerCase(Locale.ROOT);
            if (lower.startsWith("/bind") || lower.startsWith("/link")) {
                handleBindCommand(chatId, message, rawText, from.getUserName() != null ? "@" + from.getUserName() : "User " + fromId);
                return;
            }
        }

        boolean isMentor = isMentorEcho(from);
        boolean isCurator = isCuratorEcho(from);

        // Allow Mentor or Curator to set a task or plan via command
        if ((isMentor || isCurator) && rawText != null) {
            String lower = rawText.trim().toLowerCase(Locale.ROOT);
            if (lower.startsWith("/план") || lower.startsWith("/plan") || lower.startsWith("/задачи")) {
                handleMentorSetPlan(chatId, message, rawText, isMentor ? "Ксения" : "Катя");
                return;
            }
            if (lower.startsWith("/день") || lower.startsWith("/day")) {
                handleMentorSetDay(chatId, message, rawText, isMentor ? "Ксения" : "Катя");
                return;
            }
            if (lower.startsWith("/задание") || lower.startsWith("/задача") || lower.startsWith("/task") || lower.startsWith("/цель") || lower.startsWith("/фокус")) {
                handleMentorSetTask(chatId, message, rawText, isMentor ? "Ксения" : "Катя");
                return;
            }
            if (lower.equals("/тишина") || lower.startsWith("/тишина") || lower.startsWith("/ack") || lower.startsWith("/silent")) {
                handleToggleAckCommand(chatId, message, lower, isMentor ? "Ксения" : "Катя");
                return;
            }
            if (telegramReportHandler.isMeetingMessage(message)) {
                Optional<Student> studentForMeeting = identifyStudent(chatId, fromId, from.getUserName(), message);
                if (studentForMeeting.isPresent() && telegramReportHandler.processMeetingMessage(studentForMeeting.get(), message)) {
                    auditMessageLogRepository.save(AuditMessageLog.builder()
                            .updateId(updateId)
                            .tgChatId(chatId)
                            .tgUserId(fromId)
                            .sourceType(sourceType)
                            .rawPayload(rawText)
                            .processingStatus("ZOOM_LINK_CAPTURED")
                            .createdAt(Instant.now())
                            .build());
                    return;
                }
            }
            log.debug("Mentor/Curator echo filtered: ignoring casual message from {} (tg_id={})",
                    isMentor ? "Ksenia" : "Katya", fromId);
            return;
        }

        // Identify Student by tg_chat_id or tg_user_id or username or group title/command
        Optional<Student> studentOpt = identifyStudent(chatId, fromId, from.getUserName(), message);

        if (studentOpt.isEmpty()) {
            boolean isGroup = message.getChat() != null && (message.getChat().isGroupChat() || message.getChat().isSuperGroupChat());
            if (isGroup) {
                // If it is mentor or curator, NEVER register them as a student!
                if (isMentor || isCurator) {
                    log.debug("Mentor/Curator message in unlinked group: ignoring auto-registration");
                    return;
                }

                // Check if an unlinked active student matching this name or title already exists in DB
                String incomingName = telegramFullName(from);
                String normalizedName = normalizePersonName(incomingName);
                Optional<Student> matchedByName = studentRepository.findAll().stream()
                        .filter(s -> Boolean.TRUE.equals(s.getIsActive())
                                && (s.getTgChatId() == null || s.getTgChatId().equals(chatId))
                                && normalizePersonName(s.getFullName()).equals(normalizedName))
                        .findFirst();

                if (matchedByName.isEmpty() && StringUtils.hasText(message.getChat().getTitle())) {
                    matchedByName = findStudentByChatTitle(message.getChat().getTitle());
                }

                Student student;
                if (matchedByName.isPresent()) {
                    student = matchedByName.get();
                    student.setTgChatId(chatId);
                    if (!StringUtils.hasText(student.getTgUsername()) && StringUtils.hasText(from.getUserName())) {
                        student.setTgUsername(from.getUserName().replace("@", "").trim());
                    }
                    student.setIsActive(true);
                    student = studentRepository.save(student);
                    log.info("Auto-bound existing student '{}' (#{}) to Telegram group {} on incoming message",
                            student.getFullName(), student.getId(), chatId);
                } else {
                    student = newStudentFromTelegram(from);
                    if (StringUtils.hasText(message.getChat().getTitle())) {
                        String titleUpper = message.getChat().getTitle().toUpperCase(Locale.ROOT);
                        if (titleUpper.contains("VIP") || titleUpper.contains("ВИП")) {
                            student.setTariff("VIP");
                        }
                    }
                    student.setTgChatId(chatId);
                    student.setIsActive(true);
                    student.setIsPaused(false);
                    student.setStartDate(LocalDate.now());
                    student = studentRepository.save(student);
                    log.info("Auto-registered new student '{}' (#{}) from Telegram group '{}' ({}) on incoming message",
                            student.getFullName(), student.getId(), message.getChat().getTitle(), chatId);
                }

                auditMessageLogRepository.save(AuditMessageLog.builder()
                        .updateId(updateId)
                        .tgChatId(chatId)
                        .tgUserId(fromId)
                        .sourceType(sourceType)
                        .rawPayload(rawText != null ? rawText : "[Auto-Bound Student on Message]")
                        .processingStatus("AUTO_BOUND_STUDENT")
                        .createdAt(Instant.now())
                        .build());

                if (telegramSenderService != null) {
                    String welcomeMsg = "🌸 Привет, " + student.getFullName() + "! Я бот-помощник наставника Ксении.\n\n" +
                            "Я автоматически подключила твою рабочую группу к системе! Жду твои ежедневные отчеты с хэштегами:\n" +
                            "• #рилс / #видео\n" +
                            "• #карусель / #пост\n" +
                            "• #сторителл / #сторис\n" +
                            "• #рассылки / #трафик\n" +
                            "• #продажи / #диалоги\n" +
                            "• #вопрос (для срочной помощи)\n\n" +
                            "Также можешь присылать голосовые или кружочки — я всё расшифрую и передам наставнику Ксении 🚀";
                    telegramSenderService.sendCaringReply(chatId, message.getMessageId(), welcomeMsg);
                }

                if (!isReportMessage(message, rawText)) {
                    return;
                }

                studentOpt = Optional.of(student);
            } else {
                log.warn("Unmatched user encountered in private chat: chat_id={}, user_id={}, username='{}'",
                        chatId, fromId, from.getUserName());

                auditMessageLogRepository.save(AuditMessageLog.builder()
                        .updateId(updateId)
                        .tgChatId(chatId)
                        .tgUserId(fromId)
                        .sourceType(sourceType)
                        .rawPayload(rawText != null ? rawText : "[Attachment / Empty]")
                        .processingStatus("UNMATCHED_USER")
                        .errorMessage("Student not found for chat " + chatId + " / user " + fromId)
                        .createdAt(Instant.now())
                        .build());

                if (telegramSenderService != null && chatId != null) {
                    boolean hasExplicitTrigger = rawText != null && (rawText.contains("#") || rawText.startsWith("/"));
                    boolean hasMedia = message.getVoice() != null || message.getVideoNote() != null;
                    if (hasExplicitTrigger || hasMedia) {
                        String uName = from.getUserName() != null ? "@" + from.getUserName() : "ID: " + fromId;
                        String guidance = "Здравствуйте! Ваш Telegram (" + uName + ") пока не привязан к списку учениц в системе.\n\n" +
                                "Добавьте меня в вашу рабочую мини-группу с наставником Ксенией, и чат подключится автоматически!";
                        telegramSenderService.sendCaringReply(chatId, message.getMessageId(), guidance);
                    }
                }
                return;
            }
        }

        Student student = studentOpt.get();
        if (Boolean.FALSE.equals(student.getIsActive())) {
            student.setIsActive(true);
            student = studentRepository.save(student);
            log.info("Reactivated inactive student '{}' (#{}), tgChatId={}", student.getFullName(), student.getId(), chatId);
        }

        // Check if message is a friendly casual greeting ("Привет бот", "Бот привет", etc.)
        if (rawText != null && isCasualGreeting(rawText)) {
            MentorSetting settings = mentorSettingRepository != null
                    ? mentorSettingRepository.findFirstByOrderByIdAsc().orElse(null)
                    : null;
            boolean reportAckEnabled = settings != null && Boolean.TRUE.equals(settings.getReportAckEnabled());

            if (telegramSenderService != null && chatId != null) {
                if (!reportAckEnabled) {
                    telegramSenderService.setMessageReaction(chatId, message.getMessageId(), "👋");
                } else {
                    String greetingReply = "🌸 Привет, " + student.getFullName() + "! Я на связи и готова принимать твои отчёты.\n\n" +
                            "⏰ Дедлайн сдачи отчётов: ежедневно до 21:00\n" +
                            "📌 Хэштеги для сдачи отчёта:\n" +
                            "• #рилс — видео или черновик Reels на проверку\n" +
                            "• #карусель — текст слайдов или PDF\n" +
                            "• #сторителл — сценарий историй\n" +
                            "• #рассылки [число] — количество отправок в Direct\n" +
                            "• #продажи [сумма] — сумма сделки\n" +
                            "• #вопрос — вопрос или рефлексия для наставника Ксении\n\n" +
                            "Также можешь присылать голосовые или кружочки — я всё расшифрую и передам наставнику Ксении! 🚀";
                    telegramSenderService.sendCaringReply(chatId, message.getMessageId(), greetingReply);
                }
            }
            return;
        }

        // Check if message is student's daily focus / reply to morning push
        if (isMorningFocusMessage(message, rawText)) {
            try {
                telegramReportHandler.handleMorningFocusMessage(student, message, rawText);

                auditMessageLogRepository.save(AuditMessageLog.builder()
                        .updateId(updateId)
                        .tgChatId(chatId)
                        .tgUserId(fromId)
                        .sourceType(sourceType)
                        .rawPayload(rawText)
                        .processingStatus("FOCUS_SET")
                        .createdAt(Instant.now())
                        .build());
            } catch (Exception ex) {
                log.error("Failed to process morning focus for student '{}' (#{}), msgId={}: {}",
                        student.getFullName(), student.getId(), message.getMessageId(), ex.getMessage(), ex);
            }
            return;
        }

        // Check if message is a meeting schedule (#созвон) or meeting link
        if (telegramReportHandler.isMeetingMessage(message)) {
            try {
                if (telegramReportHandler.processMeetingMessage(student, message)) {
                    auditMessageLogRepository.save(AuditMessageLog.builder()
                            .updateId(updateId)
                            .tgChatId(chatId)
                            .tgUserId(fromId)
                            .sourceType(sourceType)
                            .rawPayload(rawText)
                            .processingStatus("ZOOM_LINK_CAPTURED")
                            .createdAt(Instant.now())
                            .build());
                    return;
                }
            } catch (Exception ex) {
                log.error("Failed to process meeting message for student '{}' (#{}), msgId={}: {}",
                        student.getFullName(), student.getId(), message.getMessageId(), ex.getMessage(), ex);
            }
        }

        try {
            telegramReportHandler.handleStudentMessage(student, message);

            auditMessageLogRepository.save(AuditMessageLog.builder()
                    .updateId(updateId)
                    .tgChatId(chatId)
                    .tgUserId(fromId)
                    .sourceType(sourceType)
                    .rawPayload(rawText != null ? rawText : "[Attachment / Empty]")
                    .processingStatus("PROCESSED")
                    .createdAt(Instant.now())
                    .build());
        } catch (Exception ex) {
            log.error("Failed to process report message for student '{}' (#{}), msgId={}: {}",
                    student.getFullName(), student.getId(), message.getMessageId(), ex.getMessage(), ex);

            auditMessageLogRepository.save(AuditMessageLog.builder()
                    .updateId(updateId)
                    .tgChatId(chatId)
                    .tgUserId(fromId)
                    .sourceType(sourceType)
                    .rawPayload(rawText)
                    .processingStatus("ERROR")
                    .errorMessage(ex.getMessage())
                    .createdAt(Instant.now())
                    .build());
        }
    }

    public static boolean isServiceMessage(Message message) {
        if (message == null) return false;
        return Boolean.TRUE.equals(message.getGroupchatCreated())
                || Boolean.TRUE.equals(message.getSuperGroupCreated())
                || Boolean.TRUE.equals(message.getChannelChatCreated())
                || (message.getNewChatMembers() != null && !message.getNewChatMembers().isEmpty())
                || message.getLeftChatMember() != null
                || message.getPinnedMessage() != null
                || message.getNewChatTitle() != null
                || message.getNewChatPhoto() != null
                || Boolean.TRUE.equals(message.getDeleteChatPhoto());
    }

    private void handleNewChatMembers(Message message, Long updateId, String sourceType, Long groupChatId) {
        if (message == null || groupChatId == null || message.getChat() == null
                || !(message.getChat().isGroupChat() || message.getChat().isSuperGroupChat())
                || message.getNewChatMembers() == null || message.getNewChatMembers().isEmpty()) {
            return;
        }

        // One student per group. If a previous buggy bind linked the inviter,
        // an exact match for the newly added student repairs it automatically.
        Optional<Student> alreadyBound = studentRepository.findByTgChatId(groupChatId);

        for (User member : message.getNewChatMembers()) {
            if (member == null || member.getId() == null) {
                continue;
            }

            if (Boolean.TRUE.equals(member.getIsBot())) {
                handleBotAddedToGroup(message, groupChatId);
                continue;
            }

            if (isMentorEcho(member) || isCuratorEcho(member)) {
                continue;
            }

            Optional<Student> matched = Optional.empty();
            if (StringUtils.hasText(member.getUserName())) {
                String cleanUsername = member.getUserName().replace("@", "").trim();
                matched = studentRepository.findByTgUsername(cleanUsername);
                if (matched.isEmpty()) {
                    matched = studentRepository.findAll().stream()
                            .filter(student -> StringUtils.hasText(student.getTgUsername())
                                    && student.getTgUsername().equalsIgnoreCase(cleanUsername))
                            .findFirst();
                }
            }
            if (matched.isEmpty()) {
                String incomingName = telegramFullName(member);
                String normalizedName = normalizePersonName(incomingName);
                matched = studentRepository.findAll().stream()
                        .filter(student -> normalizePersonName(student.getFullName()).equals(normalizedName))
                        .findFirst();
            }

            if (alreadyBound.isPresent() && matched.isPresent()
                    && java.util.Objects.equals(alreadyBound.get().getId(), matched.get().getId())) {
                log.info("Join event is already linked to student #{}", alreadyBound.get().getId());
                return;
            }

            Student student = matched.orElseGet(() -> newStudentFromTelegram(member));
            Long existingChatId = student.getTgChatId();
            if (existingChatId != null && existingChatId < 0 && !existingChatId.equals(groupChatId)) {
                log.warn("Student #{} is already linked to another group {}; refusing to reassign", student.getId(), existingChatId);
                auditMessageLogRepository.save(AuditMessageLog.builder()
                        .updateId(updateId == null ? 0L : updateId)
                        .tgChatId(groupChatId)
                        .tgUserId(member.getId())
                        .sourceType("TELEGRAM_AUTO_BIND")
                        .rawPayload("[Student already linked to another group]")
                        .processingStatus("AUTO_BIND_CONFLICT")
                        .createdAt(Instant.now())
                        .build());
                return;
            }

            if (alreadyBound.isPresent()) {
                Student previous = alreadyBound.get();
                previous.setTgChatId(null);
                studentRepository.saveAndFlush(previous);
            }
            student.setTgChatId(groupChatId);
            student.setIsActive(true);
            if (!StringUtils.hasText(student.getTgUsername()) && StringUtils.hasText(member.getUserName())) {
                student.setTgUsername(member.getUserName().replace("@", "").trim());
            }
            Student saved = studentRepository.save(student);
            log.info("Automatically linked Telegram group {} to student #{} ({})", groupChatId, saved.getId(), saved.getFullName());
            auditMessageLogRepository.save(AuditMessageLog.builder()
                    .updateId(updateId == null ? 0L : updateId)
                    .tgChatId(groupChatId)
                    .tgUserId(member.getId())
                    .sourceType("TELEGRAM_AUTO_BIND")
                    .rawPayload("[New group member automatically linked]")
                    .processingStatus("AUTO_BOUND")
                    .createdAt(Instant.now())
                    .build());
            return;
        }
    }

    private Student newStudentFromTelegram(User member) {
        if (member != null && (isMentorEcho(member) || isCuratorEcho(member))) {
            throw new IllegalArgumentException("Cannot create student for mentor or curator");
        }
        String fullName = telegramFullName(member);
        String[] nameParts = fullName.trim().split("\\s+");
        String avatar = nameParts.length == 1
                ? nameParts[0].substring(0, 1).toUpperCase(Locale.ROOT)
                : (nameParts[0].substring(0, 1) + nameParts[nameParts.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
        return Student.builder()
                .fullName(fullName)
                .avatarText(avatar)
                .tgUsername(StringUtils.hasText(member.getUserName()) ? member.getUserName().replace("@", "").trim() : null)
                .niche("Не указана")
                .initialIncome(BigDecimal.ZERO)
                .currentIncome(BigDecimal.ZERO)
                .build();
    }

    private String telegramFullName(User member) {
        String firstName = StringUtils.hasText(member.getFirstName()) ? member.getFirstName().trim() : "Ученица";
        String lastName = StringUtils.hasText(member.getLastName()) ? member.getLastName().trim() : "";
        return (firstName + " " + lastName).trim();
    }

    private String normalizePersonName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public static boolean isReportMessage(Message message, String rawText) {
        if (message != null && (message.hasVideo() || message.hasDocument() || message.hasPhoto())) {
            return true;
        }
        if (StringUtils.hasText(rawText)) {
            String lower = rawText.trim().toLowerCase(Locale.ROOT);
            if (lower.contains("#рилс") || lower.contains("#видео") || lower.contains("#карусель")
                    || lower.contains("#пост") || lower.contains("#сторителл") || lower.contains("#сторис")
                    || lower.contains("#контент") || lower.contains("#рассылки") || lower.contains("#рассылка")
                    || lower.contains("#трафик") || lower.contains("#продажи") || lower.contains("#продажа")
                    || lower.contains("#диалоги") || lower.contains("#вопрос") || lower.contains("#клиенты")
                    || lower.contains("#отчет") || lower.contains("#отчёт") || lower.contains("#дз")
                    || lower.contains("#задание") || lower.contains("#reels") || lower.contains("#reel")
                    || lower.contains("#stories") || lower.contains("#story")) {
                return true;
            }
            if (lower.startsWith("отчет") || lower.startsWith("отчёт") || lower.startsWith("дз")
                    || lower.startsWith("задание") || lower.startsWith("домашка") || lower.startsWith("выполнила")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCasualGreeting(String rawText) {
        if (!StringUtils.hasText(rawText)) return false;
        String lower = rawText.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("/") || lower.contains("#")) return false;

        boolean mentionsBot = BOT_QUERY_PATTERN.matcher(lower).find() || lower.contains("@");
        if (!mentionsBot) {
            return false;
        }
        return GREETING_WORDS_PATTERN.matcher(lower).find();
    }

    public static boolean isMorningFocusMessage(Message message, String rawContent) {
        if (!StringUtils.hasText(rawContent)) return false;
        String lower = rawContent.trim().toLowerCase(java.util.Locale.ROOT);

        // 1. Check if replying to the bot's morning focus prompt
        Message replyTo = message != null ? message.getReplyToMessage() : null;
        if (replyTo != null && StringUtils.hasText(replyTo.getText())) {
            String replyLower = replyTo.getText().toLowerCase(java.util.Locale.ROOT);
            if (replyLower.contains("доброе утро") && (replyLower.contains("главный результат") || replyLower.contains("главную цель") || replyLower.contains("отчет") || replyLower.contains("отчёт") || replyLower.contains("дедлайн") || replyLower.contains("фокус"))) {
                return true;
            }
        }

        // 2. Check focus keywords
        return lower.startsWith("сегодня я буду")
                || lower.startsWith("сегодня буду")
                || lower.startsWith("мой фокус")
                || lower.startsWith("фокус на сегодня")
                || lower.startsWith("фокус дня")
                || lower.startsWith("цель на сегодня")
                || lower.startsWith("план на сегодня")
                || lower.startsWith("сегодня планирую")
                || lower.startsWith("сегодня в планах")
                || lower.startsWith("сегодня сделаю")
                || lower.startsWith("главный результат:")
                || lower.startsWith("главная цель:")
                || lower.startsWith("/task")
                || lower.startsWith("/цель")
                || lower.startsWith("/фокус");
    }

    public boolean isMentorEcho(User from) {
        if (from == null) return false;
        Long fromId = from.getId();

        Long mentorTgId = mentorSettingRepository.findFirstByOrderByIdAsc()
                .map(MentorSetting::getMentorTgId)
                .orElse(null);

        if (fromId != null && fromId.equals(mentorTgId)) {
            return true;
        }

        if (fromId != null && adminTokenRepository.findAll().stream()
                .anyMatch(t -> "MENTOR".equalsIgnoreCase(t.getRole()) && fromId.equals(t.getTgUserId()))) {
            return true;
        }

        String username = from.getUserName() != null ? from.getUserName().toLowerCase(Locale.ROOT) : "";
        String firstName = from.getFirstName() != null ? from.getFirstName().toLowerCase(Locale.ROOT) : "";
        String lastName = from.getLastName() != null ? from.getLastName().toLowerCase(Locale.ROOT) : "";
        String fullName = (firstName + " " + lastName).trim();

        if (username.equals("sidarenka_ks") || username.contains("sidarenka") || username.contains("ksenia")
                || fullName.contains("ksenia") || fullName.contains("sidarenka") || fullName.contains("сидоренко")
                || fullName.contains("бьюти наставник") || fullName.contains("ксинус") || fullName.contains("наставник")) {
            if (fromId != null) {
                registerMentorTelegramId(fromId);
            }
            return true;
        }

        return false;
    }

    public boolean isMentorEcho(Long fromId) {
        if (fromId == null) return false;

        Long mentorTgId = mentorSettingRepository.findFirstByOrderByIdAsc()
                .map(MentorSetting::getMentorTgId)
                .orElse(11223344L);

        if (fromId.equals(mentorTgId)) {
            return true;
        }

        return adminTokenRepository.findAll().stream()
                .anyMatch(t -> "MENTOR".equalsIgnoreCase(t.getRole()) && fromId.equals(t.getTgUserId()));
    }

    private void registerMentorTelegramId(Long fromId) {
        try {
            mentorSettingRepository.findFirstByOrderByIdAsc().ifPresent(setting -> {
                if (!fromId.equals(setting.getMentorTgId())) {
                    setting.setMentorTgId(fromId);
                    mentorSettingRepository.save(setting);
                    log.info("Auto-registered mentor Telegram user ID: {}", fromId);
                }
            });
            adminTokenRepository.findAll().stream()
                    .filter(t -> "MENTOR".equalsIgnoreCase(t.getRole()) && !fromId.equals(t.getTgUserId()))
                    .forEach(t -> {
                        t.setTgUserId(fromId);
                        adminTokenRepository.save(t);
                    });
        } catch (Exception ex) {
            log.warn("Failed to auto-persist mentor Telegram user ID {}: {}", fromId, ex.getMessage());
        }
    }

    public boolean isCuratorEcho(User from) {
        if (from == null) return false;
        Long fromId = from.getId();

        if (fromId != null && adminTokenRepository.findAll().stream()
                .anyMatch(t -> "CURATOR".equalsIgnoreCase(t.getRole()) && fromId.equals(t.getTgUserId()))) {
            return true;
        }

        String username = from.getUserName() != null ? from.getUserName().toLowerCase(Locale.ROOT) : "";
        String firstName = from.getFirstName() != null ? from.getFirstName().toLowerCase(Locale.ROOT) : "";
        String lastName = from.getLastName() != null ? from.getLastName().toLowerCase(Locale.ROOT) : "";
        String fullName = (firstName + " " + lastName).trim();

        if (username.contains("katerin") || username.contains("katya") || username.contains("popova")
                || fullName.contains("катерина") || fullName.contains("катя") || fullName.contains("попова")
                || fullName.contains("куратор")) {
            if (fromId != null) {
                registerCuratorTelegramId(fromId);
            }
            return true;
        }

        return false;
    }

    public boolean isCuratorEcho(Long fromId) {
        if (fromId == null) return false;

        return adminTokenRepository.findAll().stream()
                .anyMatch(t -> "CURATOR".equalsIgnoreCase(t.getRole()) && fromId.equals(t.getTgUserId()));
    }

    private void registerCuratorTelegramId(Long fromId) {
        try {
            adminTokenRepository.findAll().stream()
                    .filter(t -> "CURATOR".equalsIgnoreCase(t.getRole()) && !fromId.equals(t.getTgUserId()))
                    .forEach(t -> {
                        t.setTgUserId(fromId);
                        adminTokenRepository.save(t);
                        log.info("Auto-registered curator Telegram user ID: {}", fromId);
                    });
        } catch (Exception ex) {
            log.warn("Failed to auto-persist curator Telegram user ID {}: {}", fromId, ex.getMessage());
        }
    }

    private Optional<Student> identifyStudent(Long chatId, Long fromId, String username, Message message) {
        boolean isGroupChat = message != null && message.getChat() != null
                && (message.getChat().isGroupChat() || message.getChat().isSuperGroupChat());

        // NEVER identify Mentor or Curator as a student!
        if (message != null && message.getFrom() != null && (isMentorEcho(message.getFrom()) || isCuratorEcho(message.getFrom()))) {
            return Optional.empty();
        }

        // 1. Prefer author's Telegram numeric ID
        if (fromId != null) {
            Optional<Student> byUser = studentRepository.findByTgChatId(fromId);
            if (byUser.isPresent()) {
                Student st = byUser.get();
                if (isGroupChat) {
                    bindGroupToIdentifiedStudent(st, chatId);
                }
                return byUser;
            }
        }

        // 2. Match registered Telegram username (case-insensitive)
        if (StringUtils.hasText(username)) {
            String cleanName = username.replace("@", "").trim();
            Optional<Student> byName = studentRepository.findByTgUsername(cleanName);
            if (byName.isEmpty()) {
                byName = studentRepository.findAll().stream()
                        .filter(student -> StringUtils.hasText(student.getTgUsername())
                                && student.getTgUsername().replace("@", "").trim().equalsIgnoreCase(cleanName))
                        .findFirst();
            }
            if (byName.isPresent()) {
                Student st = byName.get();
                if (isGroupChat) {
                    bindGroupToIdentifiedStudent(st, chatId);
                }
                return byName;
            }
        }

        // 3. Fall back to group link
        if (chatId != null) {
            Optional<Student> byChat = studentRepository.findByTgChatId(chatId);
            if (byChat.isPresent()) {
                return byChat;
            }
        }

        // 4. In a group chat, try matching by group title with diminutive/stem support!
        if (isGroupChat && message != null && message.getChat() != null && StringUtils.hasText(message.getChat().getTitle())) {
            Optional<Student> byTitle = findStudentByChatTitle(message.getChat().getTitle());
            if (byTitle.isPresent()) {
                Student st = byTitle.get();
                bindGroupToIdentifiedStudent(st, chatId);
                return byTitle;
            }
        }

        // 5. Try command binding: /bind <name> or /link <name>
        String rawText = message != null ? (message.getText() != null ? message.getText() : message.getCaption()) : null;
        if (StringUtils.hasText(rawText)) {
            String trimmed = rawText.trim();
            if (trimmed.startsWith("/bind") || trimmed.startsWith("/link")) {
                String target = trimmed.replaceFirst("^/(bind|link)(@[a-zA-Z0-9_]+)?", "").trim();
                Optional<Student> matched = findStudentByQuery(target);
                if (matched.isPresent()) {
                    Student st = matched.get();
                    st.setTgChatId(chatId);
                    studentRepository.save(st);
                    log.info("Bound student '{}' to chat {} via /bind command", st.getFullName(), chatId);
                    if (telegramSenderService != null && chatId != null) {
                        telegramSenderService.sendCaringReply(chatId, message.getMessageId(),
                                "✅ Группа успешно привязана к ученице " + st.getFullName() + "! Теперь все отчёты с хэштегами будут автоматически попадать на проверку наставнику Ксении 🌸");
                    }
                    return Optional.of(st);
                }
            }
        }

        return Optional.empty();
    }

    private void bindGroupToIdentifiedStudent(Student student, Long groupChatId) {
        if (student == null || groupChatId == null) return;
        Long currentChatId = student.getTgChatId();
        if (currentChatId != null && currentChatId < 0 && !currentChatId.equals(groupChatId)) {
            log.warn("Student #{} is already linked to another group {}; refusing to reassign", student.getId(), currentChatId);
            return;
        }
        if (groupChatId.equals(currentChatId)) return;

        studentRepository.findByTgChatId(groupChatId)
                .filter(previous -> !java.util.Objects.equals(previous.getId(), student.getId()))
                .ifPresent(previous -> {
                    previous.setTgChatId(null);
                    studentRepository.saveAndFlush(previous);
                });
        student.setTgChatId(groupChatId);
        studentRepository.save(student);
        log.info("Auto-bound student '{}' to group chat {}", student.getFullName(), groupChatId);
    }

    public Optional<Student> findStudentByChatTitle(String title) {
        if (!StringUtils.hasText(title)) return Optional.empty();
        String titleLower = title.toLowerCase(Locale.ROOT);
        List<Student> activeStudents = studentRepository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .toList();

        for (Student st : activeStudents) {
            if (st.getFullName() != null) {
                String fullNameLower = st.getFullName().toLowerCase(Locale.ROOT);
                String firstNameLower = fullNameLower.split("\\s+")[0];
                if (titleMatchesStudent(titleLower, fullNameLower, firstNameLower)) {
                    return Optional.of(st);
                }
            }
        }
        return Optional.empty();
    }

    private boolean titleMatchesStudent(String titleLower, String fullNameLower, String firstNameLower) {
        if (!StringUtils.hasText(titleLower)) return false;
        if (titleLower.contains(fullNameLower)) return true;
        if (firstNameLower.length() >= 3 && titleLower.contains(firstNameLower)) return true;

        String studentStem = getDiminutiveStem(firstNameLower);
        if (StringUtils.hasText(studentStem) && titleLower.contains(studentStem)) {
            return true;
        }

        String[] words = titleLower.split("[\\s|—–\\-_,.]+");
        for (String word : words) {
            String wordStem = getDiminutiveStem(word);
            if (StringUtils.hasText(wordStem) && (firstNameLower.startsWith(wordStem) || word.startsWith(studentStem))) {
                return true;
            }
        }

        return false;
    }

    private String getDiminutiveStem(String name) {
        if (name == null || name.length() < 3) return "";
        String clean = name.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("вероник")) return "вероник";
        if (clean.startsWith("валер") || clean.startsWith("лер")) return "лер";
        if (clean.startsWith("елизавет") || clean.startsWith("лиз")) return "лиз";
        if (clean.startsWith("анастас") || clean.startsWith("наст")) return "наст";
        if (clean.startsWith("александр") || clean.startsWith("саш")) return "саш";
        if (clean.startsWith("дарь") || clean.startsWith("даш")) return "даш";
        if (clean.startsWith("карин")) return "карин";
        if (clean.startsWith("полин")) return "полин";
        if (clean.startsWith("милан")) return "милан";
        if (clean.startsWith("виктор") || clean.startsWith("вик")) return "вик";
        if (clean.startsWith("анн") || clean.startsWith("ан")) return "ан";
        if (clean.startsWith("юли") || clean.startsWith("юл")) return "юл";
        if (clean.startsWith("мари") || clean.startsWith("маш")) return "маш";
        if (clean.startsWith("софи") || clean.startsWith("сон")) return "сон";
        if (clean.startsWith("екатерин") || clean.startsWith("кат")) return "кат";
        if (clean.startsWith("ксени") || clean.startsWith("ксюш")) return "ксюш";
        return clean.length() >= 4 ? clean.substring(0, 4) : clean;
    }

    private void handleBotAddedToGroup(Message message, Long groupChatId) {
        if (groupChatId == null || telegramSenderService == null) return;

        String title = message.getChat() != null ? message.getChat().getTitle() : null;
        Optional<Student> matched = Optional.empty();
        if (StringUtils.hasText(title)) {
            matched = findStudentByChatTitle(title);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🌸 Всем привет! Я бот-помощник платформы «Mentor Hub» наставника Ксении (@sidarenka_ks)!\n\n");

        if (matched.isPresent()) {
            Student st = matched.get();
            bindGroupToIdentifiedStudent(st, groupChatId);
            sb.append("✅ Рабочая группа успешно привязана к ученице: ").append(st.getFullName()).append("! 🚀\n\n");
        } else {
            sb.append("✅ Рабочая группа подключена к системе!\n");
            sb.append("💡 Чтобы завершить привязку, ученице достаточно написать любое сообщение в этот чат (или наставнику ввести `/bind [Имя]`).\n\n");
        }

        sb.append("⏰ Дедлайн сдачи отчётов: ежедневно до 21:00\n");
        sb.append("📋 Посмотреть регламент и хэштеги: /tasks\n");
        sb.append("🎯 Поставить задачу на день: /задание [текст задачи]\n\n");
        sb.append("Желаю продуктивной работы и высоких чеков! ✨");

        telegramSenderService.sendCaringReply(groupChatId, message.getMessageId(), sb.toString());
        log.info("Sent welcome message upon bot addition to group '{}' ({})", title, groupChatId);
    }

    private void handleWelcomeCommand(Long chatId, Long fromId, User from, Message message) {
        if (telegramSenderService == null || chatId == null) return;
        StringBuilder welcome = new StringBuilder();
        welcome.append("Здравствуйте! Я бот-помощник платформы «Mentor Hub» наставника Ксении 🌸\n\n");
        welcome.append("Я работаю в рабочих группах («Ксения + Катя + Ученица + Бот») и принимаю ежедневные отчёты с хэштегами:\n");
        appendRulesSummary(welcome);
        welcome.append("\nКоманды бота:\n");
        welcome.append("• /tasks или /задания — посмотреть регламент и выбрать фокус\n");
        welcome.append("• /task [цель] — зафиксировать свой фокус на сегодня\n");
        welcome.append("• /задание [цель] — наставнику поставить задачу ученице\n\n");
        welcome.append("Ваш Telegram ID: ").append(fromId).append("\n");
        welcome.append("Ваш Username: @").append(from != null && from.getUserName() != null ? from.getUserName() : "не указан");
        telegramSenderService.sendCaringReply(chatId, message.getMessageId(), welcome.toString());
    }

    private void handleRulesCommand(Long chatId, Message message) {
        if (telegramSenderService == null || chatId == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("📋 Регламент отчётов и задания для учениц:\n\n");
        appendRulesSummary(sb);
        sb.append("\n⏰ Дедлайн сдачи отчёта: ежедневно до 21:00\n");
        sb.append("Все отчёты мгновенно попадают на проверку наставнику Ксении 🌸\n\n");
        sb.append("💡 Чтобы зафиксировать фокус на сегодня, отправьте в чат:\n");
        sb.append("#фокус [ваша цель на день]");

        telegramSenderService.sendCaringReply(chatId, message.getMessageId(), sb.toString());
    }

    private void appendRulesSummary(StringBuilder sb) {
        if (taskRuleRepository != null) {
            List<TaskRule> rules = taskRuleRepository.findAll().stream()
                    .filter(r -> r.getIsActive() == null || Boolean.TRUE.equals(r.getIsActive()))
                    .sorted(Comparator.comparing(TaskRule::getId))
                    .toList();
            if (!rules.isEmpty()) {
                for (TaskRule rule : rules) {
                    String badge = Boolean.TRUE.equals(rule.getIsRequired()) ? " (Обязательно)" : " (Необязательно)";
                    sb.append("• ").append(rule.getHashtag()).append(" — ").append(rule.getTitle()).append(badge).append("\n");
                    if (StringUtils.hasText(rule.getDescription())) {
                        sb.append("  ").append(rule.getDescription()).append("\n");
                    }
                    if (StringUtils.hasText(rule.getExample())) {
                        sb.append("  Пример: ").append(rule.getExample()).append("\n");
                    }
                }
                return;
            }
        }
        sb.append("• #рилс — черновик видео Reels на проверку\n");
        sb.append("• #карусель — черновик карусели (текст / PDF)\n");
        sb.append("• #сторителл — сценарий историй\n");
        sb.append("• #рассылки 25 — количество отправок в Direct\n");
        sb.append("• #продажи 45000 — сумма закрытой сделки\n");
        sb.append("• #вопрос [текст или войс] — рефлексия и вопрос ментору\n");
        sb.append("• #клиенты — новые клиенты за неделю\n");
    }

    private void handleMentorSetTask(Long chatId, Message message, String rawText, String mentorName) {
        if (chatId == null || !StringUtils.hasText(rawText)) return;

        String cleanTask = rawText.replaceFirst("^/(задание|задача|task|цель|фокус)(@[a-zA-Z0-9_]+)?", "").trim();
        if (cleanTask.startsWith(":") || cleanTask.startsWith("-")) {
            cleanTask = cleanTask.substring(1).trim();
        }

        Optional<Student> studentOpt = Optional.empty();
        if (cleanTask.contains(":")) {
            String potentialName = cleanTask.substring(0, cleanTask.indexOf(':')).trim();
            studentOpt = findStudentByQuery(potentialName);
            if (studentOpt.isPresent()) {
                cleanTask = cleanTask.substring(cleanTask.indexOf(':') + 1).trim();
            }
        }

        if (studentOpt.isEmpty()) {
            studentOpt = studentRepository.findByTgChatId(chatId);
        }

        if (studentOpt.isEmpty() && message.getChat() != null && StringUtils.hasText(message.getChat().getTitle())) {
            studentOpt = findStudentByChatTitle(message.getChat().getTitle());
            studentOpt.ifPresent(st -> bindGroupToIdentifiedStudent(st, chatId));
        }

        if (studentOpt.isEmpty()) {
            if (telegramSenderService != null) {
                telegramSenderService.sendCaringReply(chatId, message.getMessageId(),
                        "⚠️ Эта группа ещё не привязана к ученице.\n\n" +
                        "Укажите имя ученицы: `/bind [Имя]` или отправьте: `/задание [Имя]: [текст задачи]`");
            }
            return;
        }

        if (!StringUtils.hasText(cleanTask)) {
            if (telegramSenderService != null) {
                telegramSenderService.sendCaringReply(chatId, message.getMessageId(),
                        "Пожалуйста, укажите текст задачи после команды:\n`/задание [текст задачи для ученицы]`\n" +
                        "Например: `/задание Снять 1 рилс до 18:00 и сделать 25 рассылок`");
            }
            return;
        }

        Student student = studentOpt.get();
        String mentorTitle = resolveMentorTitle(mentorName);
        telegramReportHandler.recordDailyFocusSilently(student, "Задача от " + mentorTitle + ": " + cleanTask);

        log.info("Mentor {} set task for student '{}' (#{}): {}",
                mentorName, student.getFullName(), student.getId(), cleanTask);

        MentorSetting settings = mentorSettingRepository != null
                ? mentorSettingRepository.findFirstByOrderByIdAsc().orElse(null)
                : null;
        boolean reportAckEnabled = settings != null && Boolean.TRUE.equals(settings.getReportAckEnabled());

        if (telegramSenderService != null) {
            boolean reacted = telegramSenderService.setMessageReaction(chatId, message.getMessageId(), "🎯");
            if (!reportAckEnabled) {
                if (!reacted) {
                    String shortAck = "🎯 Задача принята: «" + cleanTask + "» 🌸";
                    telegramSenderService.sendSilentReply(chatId, message.getMessageId(), shortAck);
                }
            } else {
                String reply = "🎯 " + mentorTitle + " поставила задачу на сегодня для " + student.getFullName() + ":\n" +
                        "«" + cleanTask + "»\n\n" +
                        "⏰ Ждём выполнение в отчёте до 21:00 🌸";
                telegramSenderService.sendSilentReply(chatId, message.getMessageId(), reply);
            }
        }
    }

    private void handleMentorSetPlan(Long chatId, Message message, String rawText, String mentorName) {
        if (chatId == null || !StringUtils.hasText(rawText)) return;

        String cleanPlan = rawText.replaceFirst("^/(план|plan|задачи)(@[a-zA-Z0-9_]+)?", "").trim();
        if (cleanPlan.startsWith(":") || cleanPlan.startsWith("-")) {
            cleanPlan = cleanPlan.substring(1).trim();
        }

        Optional<Student> studentOpt = Optional.empty();
        if (cleanPlan.contains(":\n") || (cleanPlan.contains(":") && cleanPlan.indexOf(':') < 30)) {
            String potentialName = cleanPlan.substring(0, cleanPlan.indexOf(':')).trim();
            studentOpt = findStudentByQuery(potentialName);
            if (studentOpt.isPresent()) {
                cleanPlan = cleanPlan.substring(cleanPlan.indexOf(':') + 1).trim();
            }
        }

        if (studentOpt.isEmpty()) {
            studentOpt = studentRepository.findByTgChatId(chatId);
        }

        if (studentOpt.isEmpty() && message.getChat() != null && StringUtils.hasText(message.getChat().getTitle())) {
            studentOpt = findStudentByChatTitle(message.getChat().getTitle());
            studentOpt.ifPresent(st -> bindGroupToIdentifiedStudent(st, chatId));
        }

        if (studentOpt.isEmpty()) {
            if (telegramSenderService != null) {
                telegramSenderService.sendCaringReply(chatId, message.getMessageId(),
                        "⚠️ Эта группа ещё не привязана к ученице.\n\n" +
                        "Укажите имя ученицы: `/bind [Имя]` или отправьте: `/план [Имя]: [текст плана]`");
            }
            return;
        }

        Student student = studentOpt.get();
        ZoneId studentZone = resolveStudentZone(student);
        LocalDate studentToday = LocalDate.now(studentZone);

        if (!StringUtils.hasText(cleanPlan)) {
            if (telegramSenderService != null) {
                if (StringUtils.hasText(student.getTaskPlanRaw())) {
                    Map<Integer, String> plan = StudentService.parseTaskPlan(student.getTaskPlanRaw());
                    int activeDay = studentService != null ? studentService.getCurrentDayNumber(student, studentToday) : 1;
                    LocalDate startDate = student.getTaskPlanStartDate() != null ? student.getTaskPlanStartDate() : studentToday;
                    StringBuilder sb = new StringBuilder();
                    sb.append("📋 Персональный план для ").append(student.getFullName());
                    sb.append(formatPlanHeaderSpan(plan, startDate, studentToday)).append(":\n\n");
                    for (Map.Entry<Integer, String> entry : plan.entrySet()) {
                        int dayNum = entry.getKey();
                        LocalDate dayDate = startDate.plusDays(dayNum - 1);
                        String dateLabel = formatDayDateLabel(dayDate, studentToday);
                        if (dayDate.equals(studentToday)) {
                            sb.append("👉 *День ").append(dayNum).append(" (").append(dateLabel).append(")*: ").append(entry.getValue()).append("\n");
                        } else if (dayDate.isBefore(studentToday)) {
                            sb.append("✔️ День ").append(dayNum).append(" (").append(dateLabel).append("): ").append(entry.getValue()).append("\n");
                        } else {
                            sb.append("▫️ День ").append(dayNum).append(" (").append(dateLabel).append("): ").append(entry.getValue()).append("\n");
                        }
                    }
                    sb.append("\n💡 Сменить день: `/день [номер]` (например: `/день 2`)\n");
                    sb.append("💡 Чтобы задать новый план, отправьте: `/план` и далее текст плана по дням");
                    telegramSenderService.sendSilentReply(chatId, message.getMessageId(), sb.toString());
                } else {
                    telegramSenderService.sendCaringReply(chatId, message.getMessageId(),
                            "📋 У ученицы " + student.getFullName() + " пока не задан индивидуальный план.\n\n" +
                            "Скопируйте план из заметок прямо сюда после `/план`:\n" +
                            "```\n/план\n" +
                            "1 день - сторис челлендж + 7 тредс + прогрев\n" +
                            "2 день - сторис челлендж + 5 тредс + прогрев + рилс\n" +
                            "3 день - пост + сторис челлендж\n" +
                            "4 день - рилс + пост\n```\n" +
                            "Бот сохранит его и будет каждое утро в 09:00 отправлять задачу текущего дня 🌸");
                }
            }
            return;
        }

        StudentService.PlanSchedule schedule = StudentService.parsePlanWithSchedule(cleanPlan, studentToday, studentZone);
        LocalDate startDate = schedule.startDate();
        String finalPlanText = schedule.cleanPlan();
        Map<Integer, String> parsed = schedule.days();

        student.setTaskPlanRaw(finalPlanText);
        student.setCurrentTaskDay(1);
        student.setTaskPlanStartDate(startDate);
        studentRepository.save(student);

        if (startDate.equals(studentToday)) {
            String day1Task = parsed.getOrDefault(1, finalPlanText.lines().findFirst().orElse(finalPlanText));
            String mentorTitle = resolveMentorTitle(mentorName);
            telegramReportHandler.recordDailyFocusSilently(student, "План от " + mentorTitle + " (День 1): " + day1Task);
        }

        log.info("Mentor {} set multi-day task plan for student '{}' (#{}): {} days parsed, starting {}",
                mentorName, student.getFullName(), student.getId(), parsed.size(), startDate);

        MentorSetting settings = mentorSettingRepository != null
                ? mentorSettingRepository.findFirstByOrderByIdAsc().orElse(null)
                : null;
        boolean reportAckEnabled = settings != null && Boolean.TRUE.equals(settings.getReportAckEnabled());

        if (telegramSenderService != null) {
            boolean reacted = telegramSenderService.setMessageReaction(chatId, message.getMessageId(), "👍");
            if (!reportAckEnabled) {
                // Quiet mode: reaction is the confirmation! If reaction failed, send a short 1-line silent message
                if (!reacted) {
                    String shortAck = "📋 План сохранён на " + parsed.size() + " дн. (старт: " + formatStartLabel(startDate, studentToday) + ") 🌸";
                    telegramSenderService.sendSilentReply(chatId, message.getMessageId(), shortAck);
                }
            } else {
                String overview = formatPlanOverview(mentorName, student, startDate, parsed, studentToday);
                telegramSenderService.sendSilentReply(chatId, message.getMessageId(), overview);
            }
        }
    }

    private void handleMentorSetDay(Long chatId, Message message, String rawText, String mentorName) {
        if (chatId == null || !StringUtils.hasText(rawText)) return;
        String numStr = rawText.replaceFirst("^/(день|day)(@[a-zA-Z0-9_]+)?", "").trim();
        Optional<Student> studentOpt = studentRepository.findByTgChatId(chatId);
        if (studentOpt.isEmpty()) {
            if (telegramSenderService != null) {
                telegramSenderService.sendCaringReply(chatId, message.getMessageId(),
                        "⚠️ Команда доступна только в рабочей группе ученицы.");
            }
            return;
        }
        Student student = studentOpt.get();
        int targetDay;
        try {
            targetDay = Integer.parseInt(numStr);
        } catch (NumberFormatException e) {
            if (telegramSenderService != null) {
                telegramSenderService.sendCaringReply(chatId, message.getMessageId(),
                        "Укажите номер дня. Например: `/день 2`");
            }
            return;
        }
        ZoneId studentZone = resolveStudentZone(student);
        LocalDate studentToday = LocalDate.now(studentZone);
        student.setCurrentTaskDay(targetDay);
        student.setTaskPlanStartDate(studentToday);
        studentRepository.save(student);

        String todayTask = studentService != null ? studentService.getTodayTask(student, studentToday) : null;
        if (StringUtils.hasText(todayTask)) {
            telegramReportHandler.recordDailyFocusSilently(student, "Задача (День " + targetDay + "): " + todayTask);
        }

        log.info("Mentor {} switched current day to {} for student '{}'",
                mentorName, targetDay, student.getFullName());

        MentorSetting settings = mentorSettingRepository != null
                ? mentorSettingRepository.findFirstByOrderByIdAsc().orElse(null)
                : null;
        boolean reportAckEnabled = settings != null && Boolean.TRUE.equals(settings.getReportAckEnabled());

        if (telegramSenderService != null) {
            boolean reacted = telegramSenderService.setMessageReaction(chatId, message.getMessageId(), "👍");
            if (!reportAckEnabled) {
                if (!reacted) {
                    String shortReply = "🗓 Текущий день переключён на День " + targetDay +
                            (StringUtils.hasText(todayTask) ? " («" + todayTask + "»)" : "") + " 🌸";
                    telegramSenderService.sendSilentReply(chatId, message.getMessageId(), shortReply);
                }
            } else {
                String reply = "🎯 Для ученицы " + student.getFullName() + " установлен День " + targetDay + "!\n" +
                        (StringUtils.hasText(todayTask) ? "Задача на сегодня: «" + todayTask + "» 🌸" : "");
                telegramSenderService.sendSilentReply(chatId, message.getMessageId(), reply);
            }
        }
    }

    private ZoneId resolveStudentZone(Student student) {
        if (student != null && StringUtils.hasText(student.getTimezone())) {
            try {
                return ZoneId.of(student.getTimezone().trim());
            } catch (Exception ignored) {}
        }
        return MINSK_ZONE;
    }

    private static String formatStartLabel(LocalDate startDate, LocalDate today) {
        if (startDate == null || today == null) return "завтра";
        long diff = ChronoUnit.DAYS.between(today, startDate);
        if (diff == 0) return "сегодня";
        if (diff == 1) return "завтра (" + startDate.format(DateTimeFormatter.ofPattern("dd.MM")) + ")";
        if (diff == 2) return "послезавтра (" + startDate.format(DateTimeFormatter.ofPattern("dd.MM")) + ")";
        String dow = getShortRussianDayOfWeek(startDate.getDayOfWeek());
        return dow + ", " + startDate.format(DateTimeFormatter.ofPattern("dd.MM"));
    }

    private static String formatDayDateLabel(LocalDate dayDate, LocalDate today) {
        if (dayDate == null || today == null) return "";
        long diff = ChronoUnit.DAYS.between(today, dayDate);
        if (diff == 0) return "сегодня";
        if (diff == 1) return "завтра, " + dayDate.format(DateTimeFormatter.ofPattern("dd.MM"));
        if (diff == -1) return "вчера, " + dayDate.format(DateTimeFormatter.ofPattern("dd.MM"));
        String dow = getShortRussianDayOfWeek(dayDate.getDayOfWeek());
        return dow + ", " + dayDate.format(DateTimeFormatter.ofPattern("dd.MM"));
    }

    private static String getShortRussianDayOfWeek(DayOfWeek dow) {
        if (dow == null) return "";
        return switch (dow) {
            case MONDAY -> "пн";
            case TUESDAY -> "вт";
            case WEDNESDAY -> "ср";
            case THURSDAY -> "чт";
            case FRIDAY -> "пт";
            case SATURDAY -> "сб";
            case SUNDAY -> "вс";
        };
    }

    private static String resolveMentorTitle(String mentorName) {
        if (mentorName != null && mentorName.trim().equalsIgnoreCase("Катя")) {
            return "Куратор Катя";
        }
        return "Наставник Ксения";
    }

    private static String formatPlanHeaderSpan(Map<Integer, String> plan, LocalDate startDate, LocalDate today) {
        if (plan == null || plan.isEmpty()) return "";
        int maxDay = plan.keySet().stream().max(Integer::compareTo).orElse(plan.size());
        LocalDate effectiveStart = startDate != null ? startDate : today;
        String startLabel = formatStartLabel(effectiveStart, today);
        if (plan.size() == maxDay) {
            return " (на " + plan.size() + " дн., старт: " + startLabel + ")";
        } else {
            LocalDate endDate = effectiveStart.plusDays(maxDay - 1);
            return " (" + plan.size() + " задач до " + endDate.format(DateTimeFormatter.ofPattern("dd.MM")) + ", старт: " + startLabel + ")";
        }
    }

    private static String formatPlanOverview(
            String mentorName,
            Student student,
            LocalDate startDate,
            Map<Integer, String> plan,
            LocalDate today) {
        String mentorTitle = resolveMentorTitle(mentorName);
        StringBuilder sb = new StringBuilder();
        sb.append("📋 ").append(mentorTitle).append(" сохранила персональный план для ").append(student.getFullName());
        if (plan == null || plan.isEmpty()) {
            return sb.append(".\n").toString();
        }
        sb.append(formatPlanHeaderSpan(plan, startDate, today)).append(":\n\n");
        for (Map.Entry<Integer, String> entry : plan.entrySet()) {
            int dayNum = entry.getKey();
            LocalDate dayDate = startDate != null ? startDate.plusDays(dayNum - 1) : today.plusDays(dayNum - 1);
            String dateLabel = formatDayDateLabel(dayDate, today);
            if (dayDate.equals(today)) {
                sb.append("👉 *День ").append(dayNum).append(" (").append(dateLabel).append(")*: ").append(entry.getValue()).append("\n");
            } else if (dayDate.isBefore(today)) {
                sb.append("✔️ День ").append(dayNum).append(" (").append(dateLabel).append("): ").append(entry.getValue()).append("\n");
            } else {
                sb.append("▫️ День ").append(dayNum).append(" (").append(dateLabel).append("): ").append(entry.getValue()).append("\n");
            }
        }
        sb.append("\n⏰ Бот будет автоматически каждое утро присылать задачу текущего дня, а вечером напоминать о дедлайне 🌸");
        return sb.toString();
    }

    public Optional<Student> findStudentByQuery(String query) {
        if (!StringUtils.hasText(query)) return Optional.empty();
        String clean = query.trim().toLowerCase(Locale.ROOT).replace("@", "");
        List<Student> students = studentRepository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .toList();

        // 1. Numeric ID match (e.g. "1" or "st-1")
        String numeric = clean.replace("st-", "");
        if (numeric.matches("\\d+")) {
            try {
                long targetId = Long.parseLong(numeric);
                Optional<Student> byId = students.stream().filter(s -> java.util.Objects.equals(s.getId(), targetId)).findFirst();
                if (byId.isPresent()) return byId;
            } catch (NumberFormatException ignored) {}
        }

        // 2. Match tg_username
        Optional<Student> byUsername = students.stream()
                .filter(s -> s.getTgUsername() != null && s.getTgUsername().toLowerCase(Locale.ROOT).contains(clean))
                .findFirst();
        if (byUsername.isPresent()) return byUsername;

        // 3. Match full name or first name
        Optional<Student> byName = students.stream()
                .filter(s -> s.getFullName() != null && s.getFullName().toLowerCase(Locale.ROOT).contains(clean))
                .findFirst();
        if (byName.isPresent()) return byName;

        // 4. Match diminutive / stem
        String queryStem = getDiminutiveStem(clean);
        if (StringUtils.hasText(queryStem)) {
            Optional<Student> byStem = students.stream()
                    .filter(s -> s.getFullName() != null && getDiminutiveStem(s.getFullName().toLowerCase(Locale.ROOT)).equals(queryStem))
                    .findFirst();
            if (byStem.isPresent()) return byStem;
        }

        return Optional.empty();
    }

    private void handleBindCommand(Long chatId, Message message, String rawText, String actorName) {
        if (chatId == null || !StringUtils.hasText(rawText)) return;
        String query = rawText.replaceFirst("^/(bind|link)(@[a-zA-Z0-9_]+)?", "").trim();
        if (!StringUtils.hasText(query)) {
            if (telegramSenderService != null) {
                List<Student> unlinked = studentRepository.findAll().stream()
                        .filter(s -> Boolean.TRUE.equals(s.getIsActive()) && s.getTgChatId() == null)
                        .toList();
                StringBuilder sb = new StringBuilder();
                sb.append("📋 Чтобы привязать группу к ученице, укажите её имя:\n");
                sb.append("`/bind [Имя или ник]`\n\n");
                if (!unlinked.isEmpty()) {
                    sb.append("Свободные ученицы потока без привязанных групп:\n");
                    for (Student s : unlinked) {
                        sb.append("• ").append(s.getFullName());
                        if (StringUtils.hasText(s.getTgUsername())) {
                            sb.append(" (@").append(s.getTgUsername()).append(")");
                        }
                        sb.append("\n");
                    }
                    sb.append("\nПример: `/bind ").append(unlinked.get(0).getFullName().split("\\s+")[0]).append("`");
                } else {
                    sb.append("Пример: `/bind Вероника` или `/bind yxxtg`");
                }
                telegramSenderService.sendCaringReply(chatId, message.getMessageId(), sb.toString());
            }
            return;
        }

        Optional<Student> matched = findStudentByQuery(query);

        if (matched.isPresent()) {
            Student st = matched.get();

            Optional<Student> currentChatBinding = studentRepository.findByTgChatId(chatId);
            if (currentChatBinding.isPresent()) {
                Student currentlyBound = currentChatBinding.get();
                if (java.util.Objects.equals(currentlyBound.getId(), st.getId())) {
                    log.info("Student '{}' is already bound to chat {}", st.getFullName(), chatId);
                    if (telegramSenderService != null) {
                        telegramSenderService.sendCaringReply(chatId, message.getMessageId(),
                                "ℹ️ Эта группа уже привязана к ученице " + st.getFullName() + ". Повторная привязка не требуется 🌸");
                    }
                    return;
                } else {
                    boolean isPrivileged = message.getFrom() != null && (isMentorEcho(message.getFrom()) || isCuratorEcho(message.getFrom()));
                    boolean isOldInactive = Boolean.FALSE.equals(currentlyBound.getIsActive());

                    if (isPrivileged || isOldInactive) {
                        log.info("Privileged actor {} re-binding chat {} from student #{} ({}) to student #{} ({})",
                                actorName, chatId, currentlyBound.getId(), currentlyBound.getFullName(), st.getId(), st.getFullName());
                        currentlyBound.setTgChatId(null);
                        studentRepository.saveAndFlush(currentlyBound);
                    } else {
                        log.warn("Refusing to bind chat {} to student #{}; it is already bound to student #{}",
                                chatId, st.getId(), currentlyBound.getId());
                        if (telegramSenderService != null) {
                            telegramSenderService.sendCaringReply(chatId, message.getMessageId(),
                                    "⚠️ Эта группа уже привязана к другой ученице. Привязка не изменена; обратитесь к наставнику.");
                        }
                        return;
                    }
                }
            }

            Long existingChatId = st.getTgChatId();
            if (existingChatId != null && existingChatId < 0 && !existingChatId.equals(chatId)) {
                boolean isPrivileged = message.getFrom() != null && (isMentorEcho(message.getFrom()) || isCuratorEcho(message.getFrom()));
                if (isPrivileged) {
                    log.info("Privileged actor {} moving student #{} from group {} to {}", actorName, st.getId(), existingChatId, chatId);
                } else {
                    log.warn("Refusing to rebind student #{} from group {} to group {}", st.getId(), existingChatId, chatId);
                    if (telegramSenderService != null) {
                        telegramSenderService.sendCaringReply(chatId, message.getMessageId(),
                                "⚠️ Ученица " + st.getFullName() + " уже привязана к другой группе. Привязка не изменена; обратитесь к наставнику.");
                    }
                    return;
                }
            }

            st.setTgChatId(chatId);
            st.setIsActive(true);
            if (message.getFrom() != null && StringUtils.hasText(message.getFrom().getUserName())) {
                if (!isMentorEcho(message.getFrom()) && !isCuratorEcho(message.getFrom())) {
                    if (!StringUtils.hasText(st.getTgUsername())) {
                        st.setTgUsername(message.getFrom().getUserName().replace("@", "").trim());
                    }
                }
            }
            studentRepository.save(st);
            log.info("Actor {} bound student '{}' (#{}) to chat {} via /bind command", actorName, st.getFullName(), st.getId(), chatId);
            if (telegramSenderService != null) {
                telegramSenderService.sendCaringReply(chatId, message.getMessageId(),
                        "✅ Группа успешно привязана к ученице " + st.getFullName() + "!\n" +
                        "Теперь бот будет принимать ежедневные отчёты и напоминать о дедлайнах здесь 🌸");
            }
        } else if (telegramSenderService != null) {
            telegramSenderService.sendCaringReply(chatId, message.getMessageId(),
                    "Ученица «" + query + "» не найдена в списке потока. Проверьте имя или Telegram-ник в панели наставника.");
        }
    }

    private void handleToggleAckCommand(Long chatId, Message message, String lower, String actorName) {
        if (chatId == null) return;
        MentorSetting setting = mentorSettingRepository != null
                ? mentorSettingRepository.findFirstByOrderByIdAsc().orElse(null)
                : null;
        if (setting == null) {
            setting = MentorSetting.builder().reportAckEnabled(false).build();
        }

        boolean targetState;
        if (lower.startsWith("/тишина") || lower.startsWith("/silent")) {
            if (lower.contains("выкл") || lower.contains("off") || lower.contains("false") || lower.contains("disable")) {
                targetState = true;
            } else {
                targetState = false;
            }
        } else if (lower.startsWith("/ack")) {
            if (lower.contains("on") || lower.contains("вкл") || lower.contains("true") || lower.contains("enable")) {
                targetState = true;
            } else if (lower.contains("off") || lower.contains("выкл") || lower.contains("false") || lower.contains("disable")) {
                targetState = false;
            } else {
                targetState = !Boolean.TRUE.equals(setting.getReportAckEnabled());
            }
        } else {
            targetState = false;
        }

        setting.setReportAckEnabled(targetState);
        mentorSettingRepository.save(setting);
        log.info("Actor {} updated reportAckEnabled to {} via command: {}", actorName, targetState, lower);

        if (telegramSenderService != null) {
            String reply = targetState
                    ? "✍️ Уведомления «Принято на проверку» включены. Бот будет отправлять подтверждения в чат при сдаче отчётов."
                    : "🤫 Тихий режим включён! Бот молча принимает и сохраняет все отчёты, черновики и продажи без сообщений «Принято на проверку» в чате.";
            telegramSenderService.sendCaringReply(chatId, message.getMessageId(), reply);
        }
    }
}
