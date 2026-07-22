package br.com.gestaodireta.messaging.dto;

import br.com.gestaodireta.messaging.enumeration.MessagingChannel;
import jakarta.validation.constraints.NotNull;

public record CreateMessagingLinkCodeRequest(@NotNull MessagingChannel channel) {}
