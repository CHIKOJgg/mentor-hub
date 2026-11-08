package com.mentorhub.telegram.router;

import com.mentorhub.entity.AdminToken;
import com.mentorhub.entity.AuditMessageLog;
import com.mentorhub.entity.MentorSetting;
import com.mentorhub.entity.Student;
import com.mentorhub.repository.AdminTokenRepository;
import com.mentorhub.repository.AuditMessageLogRepository;
import com.mentorhub.repository.MentorSettingRepository;
import com.mentorhub.repository.StudentRepository;
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
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Instant;
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

        // Welcome / Help handling
        if (rawText != null && (rawText.startsWith("/start") || rawText.startsWith("/help"))) {
            if (telegramSenderService != null && chatId != null) {
                String welcomeMsg = "Здравствуйте! Я бот-ассистент платформы «Mentor Hub» для наставничества Ксении и куратора Кати.\n\n" +
                        "Я работаю в мини-группах («Ксения + Катя + Ученица + Бот») и принимаю ежедневные отчёты с хэштегами:\n" +
                        "• #рилс — черновик видео Reels на проверку\n" +
                        "• #карусель — черновик карусели (текст / PDF)\n" +
                        "• #сторителл — сценарий историй\n" +
                        "• #рассылки 25 — количество отправок в Direct\n" +
                        "• #продажи 45000 — сумма закрытой сделки\n" +
                        "• #вопрос [текст или войс] — рефлексия и вопрос ментору\n" +
                        "• #клиенты — новые клиенты за неделю\n\n" +
                        "Все сданные материалы автоматически отправляются на вычитку в панель наставника.\n" +
                        "Ваш Telegram ID: " + fromId + "\n" +
                        "Ваш Username: @" + (from.getUserName() != null ? from.getUserName() : "не указан");
                telegramSenderService.sendCaringReply(chatId, message.getMessageId(), welcomeMsg);
            }
            return;
        }

        // Filter out echo from Mentor Ksenia
        if (isMentorEcho(fromId)) {
            log.debug("Mentor echo filtered: ignoring message from Ksenia (tg_id={})", fromId);
            return;
        }

        // Filter out echo from Curator Katya
        if (isCuratorEcho(fromId)) {
            log.debug("Curator echo filtered: ignoring message from Katya (tg_id={})", fromId);
            return;
        }

        // Identify Student by tg_chat_id or tg_user_id or username
        Optional<Student> studentOpt = identifyStudent(chatId, fromId, from.getUserName());

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
                String uName = from.getUserName() != null ? "@" + from.getUserName() : "ID: " + fromId;
                String guidance = "Здравствуйте! Ваш Telegram (" + uName + ") пока не привязан к списку учениц в системе.\n\n" +
                        "Чтобы отчёты попадали в дашборд Ксении и Кати:\n" +
                        "1. Добавьте меня в вашу рабочую мини-группу с Ксенией и Катей.\n" +
                        "2. Куратор Катя зафиксирует ваш Telegram в списке потока.\n" +
                        "(Ваш Telegram ID: " + fromId + ")";
                telegramSenderService.sendCaringReply(chatId, message.getMessageId(), guidance);
            }
            return;
        }

        Student student = studentOpt.get();

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

    private Optional<Student> identifyStudent(Long chatId, Long fromId, String username) {
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
                return byUser;
            }
        }

        // 3. Try matching by username
        if (StringUtils.hasText(username)) {
            String cleanName = username.replace("@", "").trim();
            Optional<Student> byName = studentRepository.findByTgUsername(cleanName);
            if (byName.isPresent()) {
                return byName;
            }
        }

        return Optional.empty();
    }
}
