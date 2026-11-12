package com.mentorhub.telegram.config;

import com.mentorhub.telegram.MentorBotConsumer;
import com.mentorhub.telegram.router.TelegramMessageRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
@Configuration
public class TelegramBotConfiguration {

    @Bean
    @ConditionalOnMissingBean(TelegramClient.class)
    @ConditionalOnExpression("'${telegram.bot.enabled:false}' == 'true' && !'${telegram.bot.token:}'.isEmpty() && !'${telegram.bot.token:}'.startsWith('mock')")
    public TelegramClient telegramClient(
            @Value("${telegram.bot.token:}") String botToken
    ) {
        log.info("Creating OkHttpTelegramClient bean");
        return new OkHttpTelegramClient(botToken);
    }

    @Bean
    @ConditionalOnMissingBean(MentorBotConsumer.class)
    @ConditionalOnExpression("'${telegram.bot.enabled:false}' == 'true' && '${telegram.bot.mode:long-polling}' == 'long-polling' && !'${telegram.bot.token:}'.isEmpty() && !'${telegram.bot.token:}'.startsWith('mock')")
    public MentorBotConsumer fallbackLongpollingConsumer(
            TelegramMessageRouter router,
            @Value("${telegram.bot.token:}") String botToken
    ) {
        log.info("Registering fallback MentorBotConsumer for mode 'long-polling'");
        return new MentorBotConsumer(router, botToken);
    }
}
