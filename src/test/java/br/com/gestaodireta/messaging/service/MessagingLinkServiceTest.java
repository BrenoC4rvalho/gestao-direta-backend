package br.com.gestaodireta.messaging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import br.com.gestaodireta.messaging.domain.MessagingAccount;
import br.com.gestaodireta.messaging.enumeration.*;
import br.com.gestaodireta.messaging.repository.ContactVerificationCodeRepository;
import br.com.gestaodireta.messaging.repository.MessagingAccountRepository;
import br.com.gestaodireta.user.service.UserContactService;
import java.lang.reflect.Field;
import java.time.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MessagingLinkServiceTest {
    private final Instant now = Instant.parse("2026-07-23T12:00:00Z");

    @Mock private UserContactService contacts;
    @Mock private ContactVerificationCodeRepository codes;
    @Mock private MessagingAccountRepository accounts;
    @Mock private MessagingConversationService conversations;
    @Mock private PasswordEncoder encoder;

    private MessagingLinkService service;
    private MessagingAccount account;

    @BeforeEach
    void setUp() throws Exception {
        service =
                new MessagingLinkService(
                        contacts,
                        codes,
                        accounts,
                        conversations,
                        encoder,
                        Clock.fixed(now, ZoneOffset.UTC));
        account = account();
        when(accounts.findWithLockById(account.getId())).thenReturn(Optional.of(account));
    }

    @Test
    void shouldIncrementAccountAttemptsAfterInvalidLink() {
        prepareNoCodes();
        assertThat(service.link(account, "123456"))
                .isEqualTo(MessagingLinkResult.LINK_CODE_NOT_FOUND);

        assertThat(account.getLinkAttemptCount()).isEqualTo(1);
        assertThat(account.getLastLinkAttemptAt())
                .isEqualTo(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
    }

    @Test
    void shouldTemporarilyBlockAccountOnFifthInvalidLink() {
        prepareNoCodes();
        for (int attempt = 0; attempt < 5; attempt++) {
            service.link(account, "123456");
        }

        assertThat(account.getLinkAttemptCount()).isEqualTo(5);
        assertThat(account.getLinkBlockedUntil())
                .isEqualTo(LocalDateTime.ofInstant(now.plusSeconds(900), ZoneOffset.UTC));
    }

    @Test
    void shouldRejectAttemptDuringTemporaryBlock() {
        account.setLinkBlockedUntil(LocalDateTime.ofInstant(now.plusSeconds(60), ZoneOffset.UTC));

        assertThat(service.link(account, "123456"))
                .isEqualTo(MessagingLinkResult.TEMPORARILY_BLOCKED);
        assertThat(account.getLinkAttemptCount()).isZero();
    }

    @Test
    void shouldResetExpiredAttemptWindowBeforeNewInvalidAttempt() {
        prepareNoCodes();
        account.setLinkAttemptCount(4);
        account.setLastLinkAttemptAt(
                LocalDateTime.ofInstant(now.minusSeconds(901), ZoneOffset.UTC));

        service.link(account, "123456");

        assertThat(account.getLinkAttemptCount()).isEqualTo(1);
        assertThat(account.getLinkBlockedUntil()).isNull();
    }

    private void prepareNoCodes() {
        when(codes.findWithLockByVerificationTypeAndChannelAndStatus(
                        eq(ContactVerificationType.MESSAGING_ACCOUNT_LINK),
                        eq(MessagingChannel.TELEGRAM),
                        eq(ContactVerificationStatus.ACTIVE)))
                .thenReturn(List.of());
    }

    private MessagingAccount account() throws Exception {
        MessagingAccount value = new MessagingAccount();
        Field id = MessagingAccount.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(value, 1L);
        value.setChannel(MessagingChannel.TELEGRAM);
        value.setExternalUserId("user");
        value.setExternalChatId("chat");
        value.setStatus(MessagingAccountStatus.PENDING);
        return value;
    }
}
