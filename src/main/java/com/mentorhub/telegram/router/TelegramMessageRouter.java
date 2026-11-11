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

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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

        // Filter out Telegram service events (group created, member joined/left, title changed)
        if (isServiceMessage(message)) {
            log.info("Telegram service message ignored (chat={}, msgId={})", chatId, message.getMessageId());
            identifyStudent(chatId, fromId, from.getUserName(), message);
            auditMessageLogRepository.save(AuditMessageLog.builder()
                    .updateId(updateId)
                    .tgChatId(chatId)
                    .tgUserId(fromId)
                    .sourceType(sourceType)
                    .rawPayload("[Service Message: group creation or member update]")
                    .processingStatus("SERVICE_EVENT_IGNORED")
                    .createdAt(Instant.now())
                    .build());
            return;
        }

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

        boolean isMentor = isMentorEcho(fromId);
        boolean isCurator = isCuratorEcho(fromId);

        // Allow Mentor or Curator to set a task or bind a student via command
        if ((isMentor || isCurator) && rawText != null) {
            String lower = rawText.trim().toLowerCase(Locale.ROOT);
            if (lower.startsWith("/задание") || lower.startsWith("/task") || lower.startsWith("/цель") || lower.startsWith("/фокус")) {
                handleMentorSetTask(chatId, message, rawText, isMentor ? "Ксения" : "Катя");
                return;
            }
            if (lower.startsWith("/bind") || lower.startsWith("/link")) {
                handleBindCommand(chatId, message, rawText, isMentor ? "Ксения" : "Катя");
                return;
            }
            log.debug("Mentor/Curator echo filtered: ignoring casual message from {} (tg_id={})",
                    isMentor ? "Ksenia" : "Katya", fromId);
            return;
        }

        // Identify Student by tg_chat_id or tg_user_id or username or group title/command
        Optional<Student> studentOpt = identifyStudent(chatId, fromId, from.getUserName(), message);

        if (studentOpt.isEmpty()) {
            log.warn("Unmatched user encountered: chat_id={}, user_id={}, username='{}'",
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
                boolean isGroup = message.getChat() != null && (message.getChat().isGroupChat() || message.getChat().isSuperGroupChat());
                boolean hasExplicitTrigger = rawText != null && (rawText.startsWith("/") || rawText.contains("#"));
                if (!isGroup || hasExplicitTrigger) {
                    String guidance;
                    if (isGroup) {
                        guidance = "Здравствуйте! Я бот-ассистент платформы Mentor Hub для Ксении и Кати 🌸\n\n" +
                                "Чтобы связать эту группу с ученицей, укажите имя ученицы в названии группы (например: «VIP — Милана») или отправьте команду:\n" +
                                "/bind [Имя ученицы] (например: /bind Милана)";
                    } else {
                        String uName = from.getUserName() != null ? "@" + from.getUserName() : "ID: " + fromId;
                        guidance = "Здравствуйте! Ваш Telegram (" + uName + ") пока не привязан к списку учениц в системе.\n\n" +
                                "Добавьте меня в вашу рабочую мини-группу с Ксенией и Катей, и чат подключится автоматически!";
                    }
                    telegramSenderService.sendCaringReply(chatId, message.getMessageId(), guidance);
                }
            }
            return;
        }

        Student student = studentOpt.get();

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

    private boolean isMentorEcho(Long fromId) {
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

    private boolean isCuratorEcho(Long fromId) {
        if (fromId == null) return false;

        return adminTokenRepository.findAll().stream()
                .anyMatch(t -> "CURATOR".equalsIgnoreCase(t.getRole()) && fromId.equals(t.getTgUserId()));
    }

    private Optional<Student> identifyStudent(Long chatId, Long fromId, String username, Message message) {
        // 1. Try matching by group chatId
        if (chatId != null) {
            Optional<Student> byChat = studentRepository.findByTgChatId(chatId);
            if (byChat.isPresent()) {
                return byChat;
            }
        }

        // 2. Try matching by personal userId
        if (fromId != null) {
            Optional<Student> byUser = studentRepository.findByTgChatId(fromId);
            if (byUser.isPresent()) {
                Student st = byUser.get();
                if (chatId != null && message != null && message.getChat() != null &&
                        (message.getChat().isGroupChat() || message.getChat().isSuperGroupChat())) {
                    st.setTgChatId(chatId);
                    studentRepository.save(st);
                    log.info("Auto-bound student '{}' to group chat {}", st.getFullName(), chatId);
                }
                return byUser;
            }
        }

        // 3. Try matching by username
        if (StringUtils.hasText(username)) {
            String cleanName = username.replace("@", "").trim();
            Optional<Student> byName = studentRepository.findByTgUsername(cleanName);
            if (byName.isPresent()) {
                Student st = byName.get();
                if (chatId != null && message != null && message.getChat() != null &&
                        (message.getChat().isGroupChat() || message.getChat().isSuperGroupChat())) {
                    st.setTgChatId(chatId);
                    studentRepository.save(st);
                    log.info("Auto-bound student '{}' to group chat {}", st.getFullName(), chatId);
                }
                return byName;
            }
        }

        // 4. In a group chat, try matching by group title! (e.g. "VIP — Милана" matches "Милана")
        if (message != null && message.getChat() != null && StringUtils.hasText(message.getChat().getTitle())) {
            String titleLower = message.getChat().getTitle().toLowerCase();
            List<Student> allStudents = studentRepository.findAll();
            for (Student st : allStudents) {
                if (st.getFullName() != null) {
                    String nameLower = st.getFullName().toLowerCase();
                    String firstNameLower = nameLower.split("\\s+")[0];
                    if (titleLower.contains(nameLower) || (firstNameLower.length() >= 3 && titleLower.contains(firstNameLower))) {
                        st.setTgChatId(chatId);
                        studentRepository.save(st);
                        log.info("Auto-bound student '{}' to group chat '{}' ({}) via title match", st.getFullName(), message.getChat().getTitle(), chatId);
                        return Optional.of(st);
                    }
                }
            }
        }

        // 5. Try command binding: /bind <name> or /link <name>
        String rawText = message != null ? (message.getText() != null ? message.getText() : message.getCaption()) : null;
        if (StringUtils.hasText(rawText)) {
            String trimmed = rawText.trim();
            if (trimmed.startsWith("/bind") || trimmed.startsWith("/link")) {
                String target = trimmed.replaceFirst("^/(bind|link)", "").trim().toLowerCase();
                if (StringUtils.hasText(target)) {
                    List<Student> allStudents = studentRepository.findAll();
                    for (Student st : allStudents) {
                        if (st.getFullName() != null && st.getFullName().toLowerCase().contains(target)) {
                            st.setTgChatId(chatId);
                            studentRepository.save(st);
                            log.info("Bound student '{}' to chat {} via /bind command", st.getFullName(), chatId);
                            if (telegramSenderService != null && chatId != null) {
                                telegramSenderService.sendCaringReply(chatId, message.getMessageId(),
                                        "✅ Группа успешно привязана к ученице " + st.getFullName() + "! Теперь все отчёты с хэштегами будут автоматически попадать на проверку Ксении и Кате.");
                            }
                            return Optional.of(st);
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private void handleWelcomeCommand(Long chatId, Long fromId, User from, Message message) {
        if (telegramSenderService == null || chatId == null) return;
        StringBuilder welcome = new StringBuilder();
        welcome.append("Здравствуйте! Я бот-ассистент платформы «Mentor Hub» для наставничества Ксении и куратора Кати 🌸\n\n");
        welcome.append("Я работаю в мини-группах («Ксения + Катя + Ученица + Бот») и принимаю ежедневные отчёты с хэштегами:\n");
        appendRulesSummary(welcome);
        welcome.append("\nКоманды бота:\n");
        welcome.append("• /tasks или /задания — посмотреть регламент и выбрать фокус\n");
        welcome.append("• /task [цель] — зафиксировать свой фокус на сегодня\n\n");
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
        sb.append("Все отчёты мгновенно попадают на проверку наставнику Ксении и куратору Кате 🌸\n\n");
        sb.append("💡 Чтобы зафиксировать фокус на сегодня, выберите кнопку ниже или отправьте:\n");
        sb.append("«Сегодня я буду [ваша цель]» или /task [цель]");

        Optional<Student> studentOpt = studentRepository.findByTgChatId(chatId);
        Long studentId = studentOpt.map(Student::getId).orElse(0L);
        telegramSenderService.sendMorningFocusPoll(chatId, studentId, sb.toString());
    }

    private void appendRulesSummary(StringBuilder sb) {
        if (taskRuleRepository != null) {
            List<TaskRule> rules = taskRuleRepository.findAll().stream()
                    .filter(r -> r.getIsActive() == null || Boolean.TRUE.equals(r.getIsActive()))
                    .sorted(Comparator.comparing(TaskRule::getId))
                    .toList();
            if (!rules.isEmpty()) {
                for (TaskRule rule : rules) {
                    String badge = Boolean.TRUE.equals(rule.getIsRequired()) ? " (Обязательно)" : " (По факту)";
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

        String cleanTask = rawText.replaceFirst("^/(задание|task|цель|фокус)(@[a-zA-Z0-9_]+)?", "").trim();
        if (cleanTask.startsWith(":") || cleanTask.startsWith("-")) {
            cleanTask = cleanTask.substring(1).trim();
        }

        Optional<Student> studentOpt = Optional.empty();
        if (cleanTask.contains(":")) {
            String potentialName = cleanTask.substring(0, cleanTask.indexOf(':')).trim();
            for (Student s : studentRepository.findAll()) {
                if (s.getFullName().toLowerCase(Locale.ROOT).contains(potentialName.toLowerCase(Locale.ROOT))) {
                    studentOpt = Optional.of(s);
                    cleanTask = cleanTask.substring(cleanTask.indexOf(':') + 1).trim();
                    break;
                }
            }
        }

        if (studentOpt.isEmpty()) {
            studentOpt = studentRepository.findByTgChatId(chatId);
        }

        if (studentOpt.isEmpty()) {
            if (telegramSenderService != null) {
                telegramSenderService.sendCaringReply(chatId, message.getMessageId(),
                        "⚠️ Эта группа ещё не привязана к ученице.\n\n" +
                        "Укажите имя ученицы: /bind [Имя] или отправьте: /задание [Имя]: [текст задачи]");
            }
            return;
        }

        if (!StringUtils.hasText(cleanTask)) {
            if (telegramSenderService != null) {
                telegramSenderService.sendCaringReply(chatId, message.getMessageId(),
                        "Пожалуйста, укажите текст задачи после команды:\n/задание [текст задачи для ученицы]");
            }
            return;
        }

        Student student = studentOpt.get();
        telegramReportHandler.handleMorningFocusMessage(student, message, "Задача от наставника " + mentorName + ": " + cleanTask);

        log.info("Mentor {} set task for student '{}' (#{}): {}",
                mentorName, student.getFullName(), student.getId(), cleanTask);

        if (telegramSenderService != null) {
            String reply = "🎯 Наставник " + mentorName + " поставила задачу на сегодня для " + student.getFullName() + ":\n" +
                    "«" + cleanTask + "»\n\n" +
                    "⏰ Ждём выполнение в отчёте до 21:00 🌸";
            telegramSenderService.sendCaringReply(chatId, message.getMessageId(), reply);
        }
    }

    private void handleBindCommand(Long chatId, Message message, String rawText, String actorName) {
        if (chatId == null || !StringUtils.hasText(rawText)) return;
        String targetName = rawText.replaceFirst("^/(bind|link)(@[a-zA-Z0-9_]+)?", "").trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(targetName)) {
            if (telegramSenderService != null) {
                telegramSenderService.sendCaringReply(chatId, message.getMessageId(),
                        "Укажите имя ученицы для привязки:\n/bind [Имя ученицы] (например: /bind Милана)");
            }
            return;
        }

        List<Student> students = studentRepository.findAll();
        Optional<Student> matched = students.stream()
                .filter(s -> s.getFullName().toLowerCase(Locale.ROOT).contains(targetName))
                .findFirst();

        if (matched.isPresent()) {
            Student st = matched.get();
            st.setTgChatId(chatId);
            studentRepository.save(st);
            log.info("Actor {} bound student '{}' to chat {} via /bind command", actorName, st.getFullName(), chatId);
            if (telegramSenderService != null) {
                telegramSenderService.sendCaringReply(chatId, message.getMessageId(),
                        "✅ Группа успешно привязана к ученице " + st.getFullName() + "!\n" +
                        "Теперь бот будет принимать ежедневные отчёты и напоминать о дедлайнах здесь 🌸");
            }
        } else if (telegramSenderService != null) {
            telegramSenderService.sendCaringReply(chatId, message.getMessageId(),
                    "Ученица «" + targetName + "» не найдена в списке потока. Проверьте имя в панели наставника.");
        }
    }
}
