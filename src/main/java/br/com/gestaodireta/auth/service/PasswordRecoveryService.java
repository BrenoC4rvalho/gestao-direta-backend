package br.com.gestaodireta.auth.service;

import br.com.gestaodireta.auth.dto.PasswordRecoveryOptionsResponse;
import br.com.gestaodireta.auth.dto.PasswordRecoveryRequest;
import br.com.gestaodireta.auth.dto.PasswordRecoveryResetRequest;
import br.com.gestaodireta.auth.dto.PasswordRecoveryTelegramResponse;
import br.com.gestaodireta.auth.dto.PasswordRecoveryVerifyRequest;
import br.com.gestaodireta.auth.dto.PasswordRecoveryVerifyResponse;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordRecoveryService {

    public static final String GENERIC_MESSAGE =
            "Se existir uma conta associada a este e-mail, serão apresentadas as opções disponíveis.";

    private static final String INVALID_CODE_MESSAGE = "Código inválido.";
    private static final String EXPIRED_CODE_MESSAGE = "Código expirado.";
    private static final String BLOCKED_CODE_MESSAGE = "Código bloqueado.";
    private static final String INVALID_RESET_TOKEN_MESSAGE =
            "Token de redefinição inválido ou expirado.";

    private final UserRepository userRepository;
    private final PasswordRecoveryCodeRepository codeRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final MessagingAccountRepository messagingAccountRepository;
    private final MessagingConversationService messagingConversationService;
    private final OutgoingMessagingService outgoingMessagingService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final int codeMinutes;
    private final int maxAttempts;
    private final int windowMinutes;
    private final int maxRequests;
    private final SecureRandom random = new SecureRandom();

    public PasswordRecoveryService(
            UserRepository userRepository,
            PasswordRecoveryCodeRepository codeRepository,
            PasswordResetTokenRepository tokenRepository,
            MessagingAccountRepository messagingAccountRepository,
            MessagingConversationService messagingConversationService,
            OutgoingMessagingService outgoingMessagingService,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${app.auth.password-recovery.code-expiration-minutes}") int codeMinutes,
            @Value("${app.auth.password-recovery.max-attempts}") int maxAttempts,
            @Value("${app.auth.password-recovery.request-window-minutes}") int windowMinutes,
            @Value("${app.auth.password-recovery.max-requests-per-window}") int maxRequests) {
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.tokenRepository = tokenRepository;
        this.messagingAccountRepository = messagingAccountRepository;
        this.messagingConversationService = messagingConversationService;
        this.outgoingMessagingService = outgoingMessagingService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.codeMinutes = codeMinutes;
        this.maxAttempts = maxAttempts;
        this.windowMinutes = windowMinutes;
        this.maxRequests = maxRequests;
    }

    @Transactional(readOnly = true)
    public PasswordRecoveryOptionsResponse options(PasswordRecoveryRequest request) {
        return new PasswordRecoveryOptionsResponse(
                findAvailableTelegramAccount(request.email()) != null);
    }

    @Transactional
    public PasswordRecoveryTelegramResponse requestTelegramCode(PasswordRecoveryRequest request) {
        User user = findActiveUser(request.email());
        if (user == null) {
            return new PasswordRecoveryTelegramResponse(null);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (codeRepository.countByUserIdAndRequestedAtAfter(
                        user.getId(), now.minusMinutes(windowMinutes))
                >= maxRequests) {
            return new PasswordRecoveryTelegramResponse(null);
        }

        MessagingAccount account = findAvailableTelegramAccount(user.getId());
        if (account == null) {
            return new PasswordRecoveryTelegramResponse(null);
        }

        invalidateActiveCredentials(user.getId(), now);

        String rawCode = "%06d".formatted(random.nextInt(1_000_000));
        PasswordRecoveryCode code = new PasswordRecoveryCode();
        code.setUser(user);
        code.setCodeHash(passwordEncoder.encode(rawCode));
        code.setStatus(PasswordRecoveryCodeStatus.ACTIVE);
        code.setExpiresAt(now.plusMinutes(codeMinutes));
        code.setAttemptCount(0);
        code.setMaxAttempts(maxAttempts);
        code.setRequestedAt(now);
        codeRepository.save(code);

        boolean sent =
                outgoingMessagingService.send(
                        messagingConversationService.active(account, now),
                        "Código para redefinir sua senha no Gestão Direta:\n\n"
                                + rawCode
                                + "\n\nO código expira em "
                                + codeMinutes
                                + " minutos.\n\nSe você não solicitou a redefinição, ignore esta mensagem.",
                        "Código para redefinir sua senha no Gestão Direta: [REDACTED]");
        if (!sent) {
            code.setStatus(PasswordRecoveryCodeStatus.INVALIDATED);
            code.setInvalidatedAt(now);
            return new PasswordRecoveryTelegramResponse(null);
        }

        return new PasswordRecoveryTelegramResponse(phoneLastFour(account));
    }

    @Transactional
    public PasswordRecoveryVerifyResponse verify(PasswordRecoveryVerifyRequest request) {
        User user =
                userRepository
                        .findByEmailIgnoreCase(request.email().trim())
                        .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
                        .orElseThrow(this::invalidCode);
        LocalDateTime now = LocalDateTime.now(clock);
        PasswordRecoveryCode code =
                codeRepository
                        .findFirstByUserIdOrderByRequestedAtDesc(user.getId())
                        .orElseThrow(this::invalidCode);

        validateCode(code, request.code(), now);
        code.setStatus(PasswordRecoveryCodeStatus.USED);
        code.setUsedAt(now);
        invalidateActiveTokens(user.getId(), now);

        String rawToken = newToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setRecoveryCode(code);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(now.plusMinutes(5));
        tokenRepository.save(token);

        return new PasswordRecoveryVerifyResponse(
                rawToken, token.getExpiresAt().atZone(clock.getZone()).toInstant());
    }

    @Transactional
    public void reset(PasswordRecoveryResetRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        PasswordResetToken token =
                tokenRepository
                        .findByTokenHash(hash(request.recoveryToken()))
                        .orElseThrow(this::invalidResetToken);
        if (token.getUsedAt() != null
                || token.getInvalidatedAt() != null
                || !token.getExpiresAt().isAfter(now)) {
            if (!token.getExpiresAt().isAfter(now) && token.getInvalidatedAt() == null) {
                token.setInvalidatedAt(now);
            }
            throw invalidResetToken();
        }

        User user = token.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw invalidResetToken();
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException("New password must be different from current password");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.incrementCredentialsVersion();
        token.setUsedAt(now);
        invalidateActiveTokens(user.getId(), now);
        codeRepository
                .findByUserIdAndStatus(user.getId(), PasswordRecoveryCodeStatus.ACTIVE)
                .forEach(
                        code -> {
                            code.setStatus(PasswordRecoveryCodeStatus.INVALIDATED);
                            code.setInvalidatedAt(now);
                        });
    }

    private String phoneLastFour(MessagingAccount account) {
        String phoneNumber = account.getUserContact().getPhoneNumber();
        String digits = phoneNumber.replaceAll("\\D", "");
        return digits.substring(Math.max(0, digits.length() - 4));
    }

    private User findActiveUser(String email) {
        return userRepository
                .findByEmailIgnoreCase(email.trim())
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElse(null);
    }

    private MessagingAccount findAvailableTelegramAccount(String email) {
        User user = findActiveUser(email);
        return user == null ? null : findAvailableTelegramAccount(user.getId());
    }

    private MessagingAccount findAvailableTelegramAccount(Long userId) {
        return messagingAccountRepository
                .findFirstByUserContactUserIdAndChannelAndStatusAndVerifiedAtIsNotNullAndExternalChatIdIsNotNull(
                        userId, MessagingChannel.TELEGRAM, MessagingAccountStatus.ACTIVE)
                .filter(account -> !account.getExternalChatId().isBlank())
                .orElse(null);
    }

    private void validateCode(PasswordRecoveryCode code, String rawCode, LocalDateTime now) {
        if (code.getStatus() == PasswordRecoveryCodeStatus.BLOCKED) {
            throw blockedCode();
        }
        if (code.getStatus() == PasswordRecoveryCodeStatus.EXPIRED) {
            throw expiredCode();
        }
        if (code.getStatus() != PasswordRecoveryCodeStatus.ACTIVE) {
            throw invalidCode();
        }
        if (!code.getExpiresAt().isAfter(now)) {
            code.setStatus(PasswordRecoveryCodeStatus.EXPIRED);
            throw expiredCode();
        }
        if (!passwordEncoder.matches(rawCode, code.getCodeHash())) {
            code.setAttemptCount(code.getAttemptCount() + 1);
            if (code.getAttemptCount() >= code.getMaxAttempts()) {
                code.setStatus(PasswordRecoveryCodeStatus.BLOCKED);
                throw blockedCode();
            }
            throw invalidCode();
        }
    }

    private void invalidateActiveCredentials(Long userId, LocalDateTime now) {
        codeRepository
                .findByUserIdAndStatus(userId, PasswordRecoveryCodeStatus.ACTIVE)
                .forEach(
                        code -> {
                            code.setStatus(PasswordRecoveryCodeStatus.INVALIDATED);
                            code.setInvalidatedAt(now);
                        });
        invalidateActiveTokens(userId, now);
    }

    private void invalidateActiveTokens(Long userId, LocalDateTime now) {
        tokenRepository
                .findByUserIdAndUsedAtIsNullAndInvalidatedAtIsNull(userId)
                .forEach(token -> token.setInvalidatedAt(now));
    }

    private BusinessException invalidCode() {
        return new BusinessException(INVALID_CODE_MESSAGE);
    }

    private BusinessException expiredCode() {
        return new BusinessException(EXPIRED_CODE_MESSAGE);
    }

    private BusinessException blockedCode() {
        return new BusinessException(BLOCKED_CODE_MESSAGE);
    }

    private BusinessException invalidResetToken() {
        return new BusinessException(INVALID_RESET_TOKEN_MESSAGE);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return Base64.getEncoder()
                    .encodeToString(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
