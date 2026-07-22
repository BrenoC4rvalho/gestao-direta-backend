package br.com.gestaodireta.messaging.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.gestaodireta.messaging.telegram.dto.TelegramDtos.Chat;
import br.com.gestaodireta.messaging.telegram.dto.TelegramDtos.Message;
import br.com.gestaodireta.messaging.telegram.dto.TelegramDtos.Update;
import br.com.gestaodireta.messaging.telegram.dto.TelegramDtos.User;
import br.com.gestaodireta.messaging.telegram.webhook.TelegramUpdateParser;
import org.junit.jupiter.api.Test;

class TelegramUpdateParserTest {
    private final TelegramUpdateParser parser = new TelegramUpdateParser();

    @Test
    void shouldParsePrivateTextUpdate() {
        Update update =
                new Update(
                        99L,
                        new Message(
                                7L,
                                1_700_000_000L,
                                new Chat(10L, "private", null, null, null, null),
                                new User(11L, false, "Maria", "Silva", "maria", "pt-br"),
                                "Olá"));
        var result = parser.parse(update, "{}");
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().providerUpdateId()).isEqualTo("99");
        assertThat(result.orElseThrow().displayName()).isEqualTo("Maria Silva");
        assertThat(result.orElseThrow().receivedAt().getEpochSecond()).isEqualTo(1_700_000_000L);
    }

    @Test
    void shouldIgnoreNonTextUpdate() {
        Update update =
                new Update(
                        99L,
                        new Message(
                                7L,
                                1_700_000_000L,
                                new Chat(10L, "private", null, null, null, null),
                                new User(11L, false, "Maria", null, null, null),
                                null));
        assertThat(parser.parse(update, "{}")).isEmpty();
    }
}
