package br.com.gestaodireta.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import br.com.gestaodireta.auth.dto.PasswordRecoveryRequest;
import br.com.gestaodireta.auth.dto.PasswordRecoveryResetRequest;
import br.com.gestaodireta.auth.dto.PasswordRecoveryVerifyRequest;
import br.com.gestaodireta.auth.entity.PasswordRecoveryCode;
import br.com.gestaodireta.auth.entity.PasswordResetToken;
import br.com.gestaodireta.auth.enumeration.PasswordRecoveryCodeStatus;
import br.com.gestaodireta.auth.repository.PasswordRecoveryCodeRepository;
import br.com.gestaodireta.auth.repository.PasswordResetTokenRepository;
import br.com.gestaodireta.messaging.domain.MessagingAccount;
import br.com.gestaodireta.messaging.enumeration.MessagingAccountStatus;
import br.com.gestaodireta.messaging.enumeration.MessagingChannel;
import br.com.gestaodireta.messaging.repository.MessagingAccountRepository;
import br.com.gestaodireta.messaging.service.MessagingConversationService;
import br.com.gestaodireta.messaging.service.OutgoingMessagingService;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

    @Mock private UserRepository userRepository;
    @Mock private PasswordRecoveryCodeRepository codeRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private MessagingAccountRepository messagingAccountRepository;
    @Mock private MessagingConversationService messagingConversationService;
    @Mock private OutgoingMessagingService outgoingMessagingService;
    @Mock private PasswordEncoder passwordEncoder;

    private PasswordRecoveryService service;

    @BeforeEach
    void setUp() {
        service =
                new PasswordRecoveryService(
                        userRepository,
                        codeRepository,
                        tokenRepository,
                        messagingAccountRepository,
                        messagingConversationService,
                        outgoingMessagingService,
                        passwordEncoder,
                        clock,
                        10,
                        3,
                        15,
                        3);
    }

    @Test
    void shouldKeepRequestNeutralWhenUserDoesNotExist() {
        service.request(new PasswordRecoveryRequest("missing@example.com"));

        verifyNoInteractions(
                codeRepository,
                tokenRepository,
                messagingAccountRepository,
                messagingConversationService,
                outgoingMessagingService);
    }

    @Test
    void shouldBlockCodeAfterMaximumInvalidAttempts() {
        User user = activeUser();
        PasswordRecoveryCode code = activeCode(user);
        code.setMaxAttempts(1);
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(codeRepository.findFirstByUserIdOrderByRequestedAtDesc(any()))
                .thenReturn(Optional.of(code));
        when(passwordEncoder.matches("000000", code.getCodeHash())).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                service.verify(
                                        new PasswordRecoveryVerifyRequest(
                                                user.getEmail(), "000000")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Código bloqueado.");

        assertThat(code.getStatus()).isEqualTo(PasswordRecoveryCodeStatus.BLOCKED);
        assertThat(code.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void shouldRejectAnExpiredCodeAndRecordItsState() {
        User user = activeUser();
        PasswordRecoveryCode code = activeCode(user);
        code.setExpiresAt(LocalDateTime.now(clock).minusSeconds(1));
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(codeRepository.findFirstByUserIdOrderByRequestedAtDesc(any()))
                .thenReturn(Optional.of(code));

        assertThatThrownBy(
                        () ->
                                service.verify(
                                        new PasswordRecoveryVerifyRequest(
                                                user.getEmail(), "000000")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Código expirado.");

        assertThat(code.getStatus()).isEqualTo(PasswordRecoveryCodeStatus.EXPIRED);
    }

    @Test
    void shouldResetPasswordInvalidateCredentialsAndIncreaseVersion() {
        User user = activeUser();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash("hashed-token");
        token.setExpiresAt(LocalDateTime.now(clock).plusMinutes(5));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(tokenRepository.findByUserIdAndUsedAtIsNullAndInvalidatedAtIsNull(any()))
                .thenReturn(List.of());
        when(codeRepository.findByUserIdAndStatus(any(), eq(PasswordRecoveryCodeStatus.ACTIVE)))
                .thenReturn(List.of());
        when(passwordEncoder.matches("Changed1!", user.getPassword())).thenReturn(false);
        when(passwordEncoder.encode("Changed1!")).thenReturn("encoded-changed-password");

        service.reset(new PasswordRecoveryResetRequest("token", "Changed1!", "Changed1!"));

        assertThat(user.getPassword()).isEqualTo("encoded-changed-password");
        assertThat(user.getCredentialsVersion()).isEqualTo(2);
        assertThat(token.getUsedAt()).isEqualTo(LocalDateTime.now(clock));
    }

    @Test
    void shouldInvalidatePreviousCodeAndTokenBeforeSendingNewCode() {
        User user = activeUser();
        PasswordRecoveryCode previousCode = activeCode(user);
        PasswordResetToken previousToken = new PasswordResetToken();
        MessagingAccount account = mock(MessagingAccount.class);
        when(account.getExternalChatId()).thenReturn("123");
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(codeRepository.countByUserIdAndRequestedAtAfter(any(), any())).thenReturn(0L);
        when(messagingAccountRepository
                        .findFirstByUserContactUserIdAndChannelAndStatusAndVerifiedAtIsNotNullAndExternalChatIdIsNotNull(
                                any(),
                                eq(MessagingChannel.TELEGRAM),
                                eq(MessagingAccountStatus.ACTIVE)))
                .thenReturn(Optional.of(account));
        when(codeRepository.findByUserIdAndStatus(any(), eq(PasswordRecoveryCodeStatus.ACTIVE)))
                .thenReturn(List.of(previousCode));
        when(tokenRepository.findByUserIdAndUsedAtIsNullAndInvalidatedAtIsNull(any()))
                .thenReturn(List.of(previousToken));
        when(passwordEncoder.encode(any())).thenReturn("hashed-code");

        when(outgoingMessagingService.send(any(), any())).thenReturn(true);

        service.request(new PasswordRecoveryRequest(user.getEmail()));

        assertThat(previousCode.getStatus()).isEqualTo(PasswordRecoveryCodeStatus.INVALIDATED);
        assertThat(previousToken.getInvalidatedAt()).isEqualTo(LocalDateTime.now(clock));
        verify(codeRepository).save(any(PasswordRecoveryCode.class));
        verify(outgoingMessagingService).send(any(), contains("Código para redefinir sua senha"));
    }

    private User activeUser() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setPassword("encoded-password");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private PasswordRecoveryCode activeCode(User user) {
        PasswordRecoveryCode code = new PasswordRecoveryCode();
        code.setUser(user);
        code.setCodeHash("hashed-code");
        code.setStatus(PasswordRecoveryCodeStatus.ACTIVE);
        code.setExpiresAt(LocalDateTime.now(clock).plusMinutes(10));
        code.setMaxAttempts(3);
        return code;
    }
}
