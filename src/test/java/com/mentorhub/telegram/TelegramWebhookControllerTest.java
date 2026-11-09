package com.mentorhub.telegram;

import com.mentorhub.telegram.router.TelegramMessageRouter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramWebhookControllerTest {

    @Test
    @DisplayName("Webhook with matching secret token returns 200 OK and routes update")
    void handleWebhook_validSecret() {
        final Update[] routedUpdate = new Update[1];
        TelegramMessageRouter stubRouter = new TelegramMessageRouter(null, null, null, null, null, null) {
            @Override
            public void routeUpdate(Update update) {
                routedUpdate[0] = update;
            }
        };

        TelegramWebhookController controller = new TelegramWebhookController(stubRouter);
        ReflectionTestUtils.setField(controller, "configuredSecretToken", "my-secret-token");

        Update update = new Update();
        update.setUpdateId(101);

        ResponseEntity<Void> response = controller.handleWebhook("my-secret-token", update);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(routedUpdate[0]).isEqualTo(update);
    }

    @Test
    @DisplayName("Webhook with mismatched secret token returns 403 Forbidden")
    void handleWebhook_invalidSecret() {
        final Update[] routedUpdate = new Update[1];
        TelegramMessageRouter stubRouter = new TelegramMessageRouter(null, null, null, null, null, null) {
            @Override
            public void routeUpdate(Update update) {
                routedUpdate[0] = update;
            }
        };

        TelegramWebhookController controller = new TelegramWebhookController(stubRouter);
        ReflectionTestUtils.setField(controller, "configuredSecretToken", "my-secret-token");

        Update update = new Update();
        update.setUpdateId(102);

        ResponseEntity<Void> response = controller.handleWebhook("wrong-token", update);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(routedUpdate[0]).isNull();
    }

    @Test
    @DisplayName("Webhook with unconfigured secret token rejects requests (returns 403 Forbidden)")
    void handleWebhook_missingConfiguredSecret() {
        final Update[] routedUpdate = new Update[1];
        TelegramMessageRouter stubRouter = new TelegramMessageRouter(null, null, null, null, null, null) {
            @Override
            public void routeUpdate(Update update) {
                routedUpdate[0] = update;
            }
        };

        TelegramWebhookController controller = new TelegramWebhookController(stubRouter);
        ReflectionTestUtils.setField(controller, "configuredSecretToken", "");

        Update update = new Update();
        update.setUpdateId(103);

        ResponseEntity<Void> response = controller.handleWebhook(null, update);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(routedUpdate[0]).isNull();
    }
}
