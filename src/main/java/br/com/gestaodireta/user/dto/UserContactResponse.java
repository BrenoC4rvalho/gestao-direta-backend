package br.com.gestaodireta.user.dto;

import br.com.gestaodireta.user.enumeration.*;
import java.util.List;

public record UserContactResponse(
        Long id,
        String phoneNumber,
        PhoneVerificationStatus phoneVerificationStatus,
        PreferredMessagingChannel preferredChannel,
        UserContactStatus status,
        List<UserContactMessagingAccountResponse> messagingAccounts) {}
