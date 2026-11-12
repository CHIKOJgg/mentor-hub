package com.mentorhub.telegram;

import com.mentorhub.telegram.router.TelegramMessageRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
@Component
@ConditionalOnExpression("'${telegram.bot.enabled:false}' == 'true' && '${telegram.bot.mode:long-polling}' == 'long-polling' && !'${telegram.bot.token:}'.isEmpty() && !'${telegram.bot.token:}'.startsWith('mock')")
public class MentorBotConsumer implements LongPollingSingleThreadUpdateConsumer, SpringLongPollingBot {

    private final TelegramMessageRouter telegramMessageRouter;
    private final String botToken;

    public MentorBotConsumer(TelegramMessageRouter telegramMessageRouter) {
        this(telegramMessageRouter, "");
    }

    @Autowired
    public MentorBotConsumer(
            TelegramMessageRouter telegramMessageRouter,
            @Value("${telegram.bot.token:}") String botToken
    ) {
        this.telegramMessageRouter = telegramMessageRouter;
        this.botToken = botToken != null ? botToken : "";
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        try {
            telegramMessageRouter.routeUpdate(update);
        } catch (Exception ex) {
            log.error("Error consuming Telegram update: {}", update != null ? update.getUpdateId() : null, ex);
        }
    }
}
