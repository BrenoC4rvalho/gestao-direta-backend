package br.com.gestaodireta.messaging.controller;

import br.com.gestaodireta.messaging.service.TelegramIncomingMessageService;
import br.com.gestaodireta.messaging.telegram.config.TelegramProperties;
import br.com.gestaodireta.messaging.telegram.dto.TelegramDtos.Update;
import br.com.gestaodireta.messaging.telegram.webhook.TelegramUpdateParser;
import br.com.gestaodireta.shared.exception.UnauthorizedException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/messaging/telegram")
public class TelegramWebhookController {
    private final TelegramProperties properties;
    private final ObjectMapper objectMapper;
    private final TelegramUpdateParser parser;
    private final TelegramIncomingMessageService service;

    public TelegramWebhookController(
            TelegramProperties properties,
            ObjectMapper objectMapper,
            TelegramUpdateParser parser,
            TelegramIncomingMessageService service) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.parser = parser;
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public void receive(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false)
                    String secret,
            @RequestBody String payload) {
        validateSecret(secret);
        try {
            parser.parse(objectMapper.readValue(payload, Update.class), payload)
                    .ifPresent(service::receive);
        } catch (JsonProcessingException exception) {
            throw new UnauthorizedException("Invalid Telegram webhook payload");
        }
    }

    private void validateSecret(String received) {
        String expected = properties.getWebhookSecret();
        if (received == null
                || expected == null
                || !MessageDigest.isEqual(
                        received.getBytes(StandardCharsets.UTF_8),
                        expected.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("Invalid Telegram webhook secret");
        }
    }
}
