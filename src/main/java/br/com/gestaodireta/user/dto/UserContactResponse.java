package br.com.gestaodireta.user.dto;

import br.com.gestaodireta.user.enumeration.*;
import java.time.LocalDateTime;
import java.util.List;

public record UserContactResponse(
        Long id,
        String phoneNumber,
        PhoneVerificationStatus phoneVerificationStatus,
        LocalDateTime phoneVerifiedAt,
        PreferredMessagingChannel preferredChannel,
        UserContactStatus status,
        List<UserContactMessagingAccountResponse> messagingAccounts) {}
