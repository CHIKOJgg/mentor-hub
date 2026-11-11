package com.mentorhub.telegram.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Service
public class TelegramSenderService {

    private final TelegramClient telegramClient;

    @Value("${telegram.bot.enabled:true}")
    private boolean botEnabled;

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Autowired
    public TelegramSenderService(@Autowired(required = false) TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public boolean sendCaringReply(Long chatId, Integer replyToMsgId, String text) {
        return sendCaringReply(chatId, replyToMsgId, text, null);
    }

    public boolean sendCaringReply(Long chatId, Integer replyToMsgId, String text, String businessConnectionId) {
        if (!canSend(chatId)) {
            log.debug("Telegram client not available or bot disabled. Caring reply skipped for chat {}", chatId);
            return false;
        }

        try {
            SendMessage.SendMessageBuilder<?, ?> builder = SendMessage.builder()
                    .chatId(chatId.toString())
                    .replyToMessageId(replyToMsgId)
                    .text(text);
            if (businessConnectionId != null && !businessConnectionId.isBlank()) {
                builder.businessConnectionId(businessConnectionId);
            }

            telegramClient.execute(builder.build());
            log.info("Sent caring reply to chat {} (reply to #{})", chatId, replyToMsgId);
            return true;
        } catch (TelegramApiException ex) {
            log.warn("Failed to send caring reply to chat {}: {}", chatId, ex.getMessage());
            return false;
        }
    }

    public boolean sendReminder(Long chatId, String text) {
        return sendStudentMessage(chatId, null, text);
    }

    public boolean sendStudentMessage(Long chatId, String businessConnectionId, String text) {
        if (!canSend(chatId)) {
            log.debug("Telegram client not available or bot disabled. Reminder skipped for chat {}", chatId);
            return false;
        }

        try {
            SendMessage.SendMessageBuilder<?, ?> builder = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text);
            if (businessConnectionId != null && !businessConnectionId.isBlank()) {
                builder.businessConnectionId(businessConnectionId);
            }

            telegramClient.execute(builder.build());
            log.info("Sent Telegram message to chat {} (business={})", chatId, businessConnectionId != null);
            return true;
        } catch (TelegramApiException ex) {
            log.warn("Failed to send Telegram message to chat {}: {}", chatId, ex.getMessage());
            return false;
        }
    }

    public boolean sendBusinessReply(Long chatId, String businessConnectionId, String text) {
        if (businessConnectionId == null || businessConnectionId.isBlank()) {
            log.warn("Cannot send a Business reply without business_connection_id (chat {})", chatId);
            return false;
        }
        return sendStudentMessage(chatId, businessConnectionId, text);
    }

    public boolean sendSaturdaySurveyPoll(Long chatId, Long studentId, String text) {
        return sendSaturdaySurveyPoll(chatId, studentId, text, null);
    }

    public boolean sendMorningFocusPoll(Long chatId, Long studentId, String text) {
        return sendMorningFocusPoll(chatId, studentId, text, null);
    }

    public boolean sendMorningFocusPoll(Long chatId, Long studentId, String text, String businessConnectionId) {
        if (!canSend(chatId)) {
            log.debug("Telegram client not available. Morning focus poll skipped for chat {}", chatId);
            return false;
        }

        try {
            InlineKeyboardRow row1 = new InlineKeyboardRow(
                    InlineKeyboardButton.builder().text("🎬 Снять Reels").callbackData("focus:reels:" + studentId).build(),
                    InlineKeyboardButton.builder().text("✉️ 20+ рассылок").callbackData("focus:outreach:" + studentId).build()
            );
            InlineKeyboardRow row2 = new InlineKeyboardRow(
                    InlineKeyboardButton.builder().text("💎 Упаковка / оффер").callbackData("focus:offer:" + studentId).build(),
                    InlineKeyboardButton.builder().text("💰 Продажи / созвон").callbackData("focus:sales:" + studentId).build()
            );

            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(List.of(row1, row2));

            SendMessage.SendMessageBuilder builder = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .replyMarkup(keyboard);
            if (businessConnectionId != null && !businessConnectionId.isBlank()) {
                builder.businessConnectionId(businessConnectionId);
            }

            telegramClient.execute(builder.build());
            log.info("Sent Morning focus poll with inline buttons to chat {} for student #{}", chatId, studentId);
            return true;
        } catch (TelegramApiException ex) {
            log.warn("Failed to send Morning focus poll to chat {}: {}", chatId, ex.getMessage());
            return false;
        }
    }

    public boolean sendSaturdaySurveyPoll(Long chatId, Long studentId, String text, String businessConnectionId) {
        if (!canSend(chatId)) {
            log.debug("Telegram client not available. Saturday survey poll skipped for chat {}", chatId);
            return false;
        }

        try {
            InlineKeyboardRow row = new InlineKeyboardRow(
                    InlineKeyboardButton.builder().text("0").callbackData("survey:clients:" + studentId + ":0").build(),
                    InlineKeyboardButton.builder().text("1").callbackData("survey:clients:" + studentId + ":1").build(),
                    InlineKeyboardButton.builder().text("2").callbackData("survey:clients:" + studentId + ":2").build(),
                    InlineKeyboardButton.builder().text("3").callbackData("survey:clients:" + studentId + ":3").build(),
                    InlineKeyboardButton.builder().text("4").callbackData("survey:clients:" + studentId + ":4").build(),
                    InlineKeyboardButton.builder().text("5+").callbackData("survey:clients:" + studentId + ":5").build()
            );

            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(List.of(row));

            SendMessage message = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .replyMarkup(keyboard)
                    .businessConnectionId(businessConnectionId)
                    .build();

            telegramClient.execute(message);
            log.info("Sent Saturday survey poll with inline buttons to chat {} for student #{}", chatId, studentId);
            return true;
        } catch (TelegramApiException ex) {
            log.warn("Failed to send Saturday survey poll to chat {}: {}", chatId, ex.getMessage());
            return false;
        }
    }

    public boolean answerCallbackQuery(String callbackQueryId, String text) {
        if (telegramClient == null || !botEnabled) {
            return false;
        }

        try {
            AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .text(text)
                    .build();

            telegramClient.execute(answer);
            return true;
        } catch (TelegramApiException ex) {
            log.warn("Failed to answer callback query {}: {}", callbackQueryId, ex.getMessage());
            return false;
        }
    }

    public boolean editMessageText(Long chatId, Integer messageId, String text) {
        if (!canSend(chatId) || messageId == null) {
            return false;
        }

        try {
            EditMessageText edit = EditMessageText.builder()
                    .chatId(chatId.toString())
                    .messageId(messageId)
                    .text(text)
                    .build();

            telegramClient.execute(edit);
            return true;
        } catch (TelegramApiException ex) {
            log.warn("Failed to edit message #{} in chat {}: {}", messageId, chatId, ex.getMessage());
            return false;
        }
    }

    public boolean checkBotConnection() {
        if (!isConfigured()) return false;
        try {
            telegramClient.execute(new GetMe());
            return true;
        } catch (TelegramApiException ex) {
            log.warn("Telegram bot connectivity check failed: {}", ex.getMessage());
            return false;
        }
    }

    public byte[] downloadFileBytes(String fileId, int maxBytes) {
        if (!isConfigured() || fileId == null || fileId.isBlank() || maxBytes < 1) return null;
        try (InputStream stream = telegramClient.downloadFileAsStream(fileId);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (stream == null) return null;
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    log.warn("Telegram attachment {} exceeds size limit of {} bytes", fileId, maxBytes);
                    return null;
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (TelegramApiException | IOException ex) {
            log.warn("Unable to download Telegram file {}: {}", fileId, ex.getMessage());
            return null;
        }
    }

    private boolean canSend(Long chatId) {
        return isConfigured() && chatId != null;
    }

    public boolean isConfigured() {
        return telegramClient != null && botEnabled && botToken != null
                && !botToken.isBlank() && !botToken.startsWith("mock");
    }
}
