package com.mentorhub.telegram;

import com.mentorhub.telegram.router.TelegramMessageRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final TelegramMessageRouter telegramMessageRouter;

    @Value("${telegram.bot.webhook.secret-token:}")
    private String configuredSecretToken;

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
            @RequestBody Update update
    ) {
        if (StringUtils.hasText(configuredSecretToken)) {
            if (!configuredSecretToken.equals(secretToken)) {
                log.warn("Unauthorized webhook request: secret token mismatch");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        if (update != null) {
            telegramMessageRouter.routeUpdate(update);
        }

        return ResponseEntity.ok().build();
    }
}
