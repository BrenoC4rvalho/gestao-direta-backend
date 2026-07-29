package br.com.gestaodireta.auth.service;

import br.com.gestaodireta.auth.dto.*;
import br.com.gestaodireta.auth.entity.*;
import br.com.gestaodireta.auth.enumeration.PasswordRecoveryCodeStatus;
import br.com.gestaodireta.auth.repository.*;
import br.com.gestaodireta.messaging.domain.MessagingAccount;
import br.com.gestaodireta.messaging.enumeration.*;
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
import java.time.*;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordRecoveryService {
    public static final String GENERIC_MESSAGE =
            "Se houver uma conta válida com Telegram vinculado, um código será enviado.";
    private final UserRepository users;
    private final PasswordRecoveryCodeRepository codes;
    private final PasswordResetTokenRepository tokens;
    private final MessagingAccountRepository accounts;
    private final MessagingConversationService conversations;
    private final OutgoingMessagingService outgoing;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final int codeMinutes;
    private final int maxAttempts;
    private final int windowMinutes;
    private final int maxRequests;
    private final SecureRandom random = new SecureRandom();

    public PasswordRecoveryService(
            UserRepository users,
            PasswordRecoveryCodeRepository codes,
            PasswordResetTokenRepository tokens,
            MessagingAccountRepository accounts,
            MessagingConversationService conversations,
            OutgoingMessagingService outgoing,
            PasswordEncoder encoder,
            Clock clock,
            @Value("${app.auth.password-recovery.code-expiration-minutes}") int codeMinutes,
            @Value("${app.auth.password-recovery.max-attempts}") int maxAttempts,
            @Value("${app.auth.password-recovery.request-window-minutes}") int windowMinutes,
            @Value("${app.auth.password-recovery.max-requests-per-window}") int maxRequests) {
        this.users = users;
        this.codes = codes;
        this.tokens = tokens;
        this.accounts = accounts;
        this.conversations = conversations;
        this.outgoing = outgoing;
        this.encoder = encoder;
        this.clock = clock;
        this.codeMinutes = codeMinutes;
        this.maxAttempts = maxAttempts;
        this.windowMinutes = windowMinutes;
        this.maxRequests = maxRequests;
    }

    @Transactional
    public void request(PasswordRecoveryRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) return;
        LocalDateTime now = LocalDateTime.now(clock);
        if (codes.countByUserIdAndRequestedAtAfter(user.getId(), now.minusMinutes(windowMinutes))
                >= maxRequests) return;
        MessagingAccount account =
                accounts.findFirstByUserContactUserIdAndChannelAndStatusAndVerifiedAtIsNotNullAndExternalChatIdIsNotNull(
                                user.getId(),
                                MessagingChannel.TELEGRAM,
                                MessagingAccountStatus.ACTIVE)
                        .orElse(null);
        if (account == null || account.getExternalChatId().isBlank()) return;
        codes.findByUserIdAndStatus(user.getId(), PasswordRecoveryCodeStatus.ACTIVE)
                .forEach(
                        c -> {
                            c.setStatus(PasswordRecoveryCodeStatus.INVALIDATED);
                            c.setInvalidatedAt(now);
                        });
        String raw = "%06d".formatted(random.nextInt(1_000_000));
        PasswordRecoveryCode code = new PasswordRecoveryCode();
        code.setUser(user);
        code.setCodeHash(encoder.encode(raw));
        code.setStatus(PasswordRecoveryCodeStatus.ACTIVE);
        code.setExpiresAt(now.plusMinutes(codeMinutes));
        code.setAttemptCount(0);
        code.setMaxAttempts(maxAttempts);
        code.setRequestedAt(now);
        codes.save(code);
        outgoing.send(
                conversations.active(account, now),
                "Código para redefinir sua senha no Gestão Direta:\n\n"
                        + raw
                        + "\n\nO código expira em "
                        + codeMinutes
                        + " minutos.\n\nSe você não solicitou a redefinição, ignore esta mensagem.");
    }

    @Transactional
    public PasswordRecoveryVerifyResponse verify(PasswordRecoveryVerifyRequest request) {
        User user =
                users.findByEmailIgnoreCase(request.email().trim()).orElseThrow(() -> invalid());
        LocalDateTime now = LocalDateTime.now(clock);
        PasswordRecoveryCode code =
                codes.findFirstByUserIdAndStatusOrderByRequestedAtDesc(
                                user.getId(), PasswordRecoveryCodeStatus.ACTIVE)
                        .orElseThrow(() -> invalid());
        if (!code.getExpiresAt().isAfter(now)) {
            code.setStatus(PasswordRecoveryCodeStatus.EXPIRED);
            throw invalid();
        }
        if (!encoder.matches(request.code(), code.getCodeHash())) {
            code.setAttemptCount(code.getAttemptCount() + 1);
            if (code.getAttemptCount() >= code.getMaxAttempts())
                code.setStatus(PasswordRecoveryCodeStatus.BLOCKED);
            throw invalid();
        }
        code.setStatus(PasswordRecoveryCodeStatus.USED);
        code.setUsedAt(now);
        String raw = newToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setRecoveryCode(code);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(now.plusMinutes(5));
        tokens.save(token);
        return new PasswordRecoveryVerifyResponse(
                raw, token.getExpiresAt().atZone(clock.getZone()).toInstant());
    }

    @Transactional
    public void reset(PasswordRecoveryResetRequest request) {
        if (!request.newPassword().equals(request.confirmPassword()))
            throw new BusinessException("Passwords do not match");
        LocalDateTime now = LocalDateTime.now(clock);
        PasswordResetToken token =
                tokens.findByTokenHash(hash(request.resetToken()))
                        .orElseThrow(
                                () -> new BusinessException("Reset token is invalid or expired."));
        if (token.getUsedAt() != null
                || token.getInvalidatedAt() != null
                || !token.getExpiresAt().isAfter(now))
            throw new BusinessException("Reset token is invalid or expired.");
        User user = token.getUser();
        if (encoder.matches(request.newPassword(), user.getPassword()))
            throw new BusinessException("New password must be different from current password");
        user.setPassword(encoder.encode(request.newPassword()));
        user.incrementCredentialsVersion();
        token.setUsedAt(now);
        tokens.findByUserIdAndUsedAtIsNullAndInvalidatedAtIsNull(user.getId())
                .forEach(
                        remaining -> {
                            if (!remaining.getId().equals(token.getId())) {
                                remaining.setInvalidatedAt(now);
                            }
                        });
        codes.findByUserIdAndStatus(user.getId(), PasswordRecoveryCodeStatus.ACTIVE)
                .forEach(
                        remaining -> {
                            remaining.setStatus(PasswordRecoveryCodeStatus.INVALIDATED);
                            remaining.setInvalidatedAt(now);
                        });
    }

    private BusinessException invalid() {
        return new BusinessException("Código inválido ou expirado.");
    }

    private String newToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private String hash(String value) {
        try {
            return Base64.getEncoder()
                    .encodeToString(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
