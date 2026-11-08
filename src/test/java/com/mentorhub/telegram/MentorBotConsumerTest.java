package com.mentorhub.telegram;

import com.mentorhub.telegram.router.TelegramMessageRouter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.assertj.core.api.Assertions.assertThat;

class MentorBotConsumerTest {

    @Test
    @DisplayName("MentorBotConsumer consumes update and delegates to TelegramMessageRouter")
    void consume_delegatesToRouter() {
        final Update[] routedUpdate = new Update[1];
        TelegramMessageRouter stubRouter = new TelegramMessageRouter(null, null, null, null, null, null) {
            @Override
            public void routeUpdate(Update update) {
                routedUpdate[0] = update;
            }
        };

        MentorBotConsumer consumer = new MentorBotConsumer(stubRouter);
        ReflectionTestUtils.setField(consumer, "botToken", "test-token-123");

        assertThat(consumer.getBotToken()).isEqualTo("test-token-123");
        assertThat(consumer.getUpdatesConsumer()).isSameAs(consumer);

        Update update = new Update();
        update.setUpdateId(300);

        consumer.consume(update);

        assertThat(routedUpdate[0]).isEqualTo(update);
    }
}
