package com.mentorhub.telegram.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

@Slf4j
@Service
public class TelegramSenderService {

    private final TelegramClient telegramClient;

    @Value("${telegram.bot.enabled:true}")
    private boolean botEnabled;

    @Autowired
    public TelegramSenderService(@Autowired(required = false) TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public boolean sendCaringReply(Long chatId, Integer replyToMsgId, String text) {
        if (!canSend(chatId)) {
            log.debug("Telegram client not available or bot disabled. Caring reply skipped for chat {}", chatId);
            return false;
        }

        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId.toString())
                    .replyToMessageId(replyToMsgId)
                    .text(text)
                    .build();

            telegramClient.execute(message);
            log.info("Sent caring reply to chat {} (reply to #{})", chatId, replyToMsgId);
            return true;
        } catch (TelegramApiException ex) {
            log.warn("Failed to send caring reply to chat {}: {}", chatId, ex.getMessage());
            return false;
        }
    }

    public boolean sendReminder(Long chatId, String text) {
        if (!canSend(chatId)) {
            log.debug("Telegram client not available or bot disabled. Reminder skipped for chat {}", chatId);
            return false;
        }

        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .build();

            telegramClient.execute(message);
            log.info("Sent evening reminder to chat {}", chatId);
            return true;
        } catch (TelegramApiException ex) {
            log.warn("Failed to send reminder to chat {}: {}", chatId, ex.getMessage());
            return false;
        }
    }

    public boolean sendSaturdaySurveyPoll(Long chatId, Long studentId, String text) {
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

    private boolean canSend(Long chatId) {
        return telegramClient != null && botEnabled && chatId != null;
    }
}
