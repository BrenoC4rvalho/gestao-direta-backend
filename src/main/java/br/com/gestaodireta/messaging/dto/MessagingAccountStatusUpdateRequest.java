package br.com.gestaodireta.messaging.dto;

import br.com.gestaodireta.messaging.enumeration.MessagingAccountStatus;
import jakarta.validation.constraints.NotNull;

public record MessagingAccountStatusUpdateRequest(@NotNull MessagingAccountStatus status) {}
