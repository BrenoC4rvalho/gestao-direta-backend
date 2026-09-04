package br.com.gestaodireta.messaging.telegram.webhook;

import br.com.gestaodireta.messaging.dto.IncomingAudioAttachment;
import br.com.gestaodireta.messaging.dto.IncomingMessagingMessage;
import br.com.gestaodireta.messaging.enumeration.*;
import br.com.gestaodireta.messaging.telegram.dto.TelegramDtos.*;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TelegramUpdateParser {
    public Optional<IncomingMessagingMessage> parse(Update update, String rawPayload) {
        if (update == null
                || update.updateId() == null
                || update.message() == null
                || update.message().chat() == null
                || update.message().from() == null
                || !"private".equals(update.message().chat().type())) return Optional.empty();
        Message m = update.message();
        if (m.text() != null && !m.text().isBlank()) {
            return incomingText(update, m, rawPayload);
        }
        if (m.voice() != null && m.voice().fileId() != null && !m.voice().fileId().isBlank()) {
            return incomingVoice(update, m, rawPayload);
        }
        return Optional.empty();
    }

    private Optional<IncomingMessagingMessage> incomingText(
            Update update, Message m, String rawPayload) {
        String displayName = displayName(m.from(), m.chat());
        return Optional.of(
                new IncomingMessagingMessage(
                        MessagingChannel.TELEGRAM,
                        String.valueOf(update.updateId()),
                        m.messageId() == null ? null : String.valueOf(m.messageId()),
                        String.valueOf(m.from().id()),
                        String.valueOf(m.chat().id()),
                        m.from().username(),
                        displayName,
                        MessagingMessageType.TEXT,
                        m.text(),
                        null,
                        Instant.ofEpochSecond(m.date()),
                        rawPayload));
    }

    private Optional<IncomingMessagingMessage> incomingVoice(
            Update update, Message m, String rawPayload) {
        String displayName = displayName(m.from(), m.chat());
        Voice voice = m.voice();
        return Optional.of(
                new IncomingMessagingMessage(
                        MessagingChannel.TELEGRAM,
                        String.valueOf(update.updateId()),
                        m.messageId() == null ? null : String.valueOf(m.messageId()),
                        String.valueOf(m.from().id()),
                        String.valueOf(m.chat().id()),
                        m.from().username(),
                        displayName,
                        MessagingMessageType.VOICE,
                        "",
                        new IncomingAudioAttachment(
                                voice.fileId(),
                                voice.duration(),
                                voice.mimeType(),
                                voice.fileSize()),
                        Instant.ofEpochSecond(m.date()),
                        rawPayload));
    }

    private String displayName(User user, Chat chat) {
        String name = String.join(" ", value(user.firstName()), value(user.lastName())).trim();
        if (!name.isBlank()) return name;
        if (user.username() != null && !user.username().isBlank()) return user.username();
        return chat.title();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
