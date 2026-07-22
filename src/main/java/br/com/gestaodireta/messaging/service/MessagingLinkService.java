package br.com.gestaodireta.messaging.service;

import br.com.gestaodireta.messaging.domain.*;
import br.com.gestaodireta.messaging.dto.*;
import br.com.gestaodireta.messaging.enumeration.*;
import br.com.gestaodireta.messaging.repository.*;
import br.com.gestaodireta.shared.exception.*;
import br.com.gestaodireta.user.entity.UserContact;
import br.com.gestaodireta.user.enumeration.*;
import br.com.gestaodireta.user.service.UserContactService;
import java.security.SecureRandom;
import java.time.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessagingLinkService {
    private final UserContactService contacts;
    private final ContactVerificationCodeRepository codes;
    private final MessagingAccountRepository accounts;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();

    public MessagingLinkService(
            UserContactService contacts,
            ContactVerificationCodeRepository codes,
            MessagingAccountRepository accounts,
            PasswordEncoder encoder) {
        this.contacts = contacts;
        this.codes = codes;
        this.accounts = accounts;
        this.encoder = encoder;
    }

    @Transactional
    public MessagingLinkCodeResponse create(CreateMessagingLinkCodeRequest request) {
        if (request.channel() != MessagingChannel.TELEGRAM)
            throw new ValidationException("Messaging channel is not supported");
        UserContact contact = contacts.currentContact();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (codes.existsByUserContactIdAndVerificationTypeAndChannelAndCreatedAtAfter(
                contact.getId(),
                ContactVerificationType.MESSAGING_ACCOUNT_LINK,
                request.channel(),
                now.minusMinutes(1)))
            throw new BusinessException("Please wait before generating another code");
        codes.findByUserContactIdAndVerificationTypeAndChannelAndStatus(
                        contact.getId(),
                        ContactVerificationType.MESSAGING_ACCOUNT_LINK,
                        request.channel(),
                        ContactVerificationStatus.ACTIVE)
                .forEach(
                        c -> {
                            c.setStatus(ContactVerificationStatus.CANCELED);
                            codes.save(c);
                        });
        String value = "%06d".formatted(random.nextInt(1_000_000));
        ContactVerificationCode code = new ContactVerificationCode();
        code.setUserContact(contact);
        code.setVerificationType(ContactVerificationType.MESSAGING_ACCOUNT_LINK);
        code.setChannel(request.channel());
        code.setCodeHash(encoder.encode(value));
        code.setStatus(ContactVerificationStatus.ACTIVE);
        code.setExpiresAt(now.plusMinutes(10));
        codes.save(code);
        return new MessagingLinkCodeResponse(value, code.getExpiresAt(), "/vincular " + value);
    }

    @Transactional
    public boolean link(MessagingAccount account, String value) {
        if (account.getUserContact() != null
                && account.getStatus() == MessagingAccountStatus.ACTIVE) return false;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (ContactVerificationCode code :
                codes.findByVerificationTypeAndChannelAndStatusAndExpiresAtAfter(
                        ContactVerificationType.MESSAGING_ACCOUNT_LINK,
                        MessagingChannel.TELEGRAM,
                        ContactVerificationStatus.ACTIVE,
                        now)) {
            if (!encoder.matches(value, code.getCodeHash())) continue;
            UserContact contact = code.getUserContact();
            if (accounts.existsByUserContactIdAndChannelAndStatus(
                    contact.getId(), MessagingChannel.TELEGRAM, MessagingAccountStatus.ACTIVE))
                return false;
            account.setUserContact(contact);
            account.setStatus(MessagingAccountStatus.ACTIVE);
            account.setVerifiedAt(now);
            accounts.save(account);
            code.setStatus(ContactVerificationStatus.USED);
            code.setUsedAt(now);
            codes.save(code);
            contact.setStatus(UserContactStatus.ACTIVE);
            if (contact.getPreferredChannel() == PreferredMessagingChannel.NONE)
                contact.setPreferredChannel(PreferredMessagingChannel.TELEGRAM);
            return true;
        }
        codes.findByVerificationTypeAndChannelAndStatusAndExpiresAtAfter(
                        ContactVerificationType.MESSAGING_ACCOUNT_LINK,
                        MessagingChannel.TELEGRAM,
                        ContactVerificationStatus.ACTIVE,
                        now)
                .forEach(
                        c -> {
                            c.setAttemptCount(c.getAttemptCount() + 1);
                            if (c.getAttemptCount() >= 5)
                                c.setStatus(ContactVerificationStatus.BLOCKED);
                            codes.save(c);
                        });
        return false;
    }

    @Transactional
    public void unlink(Long accountId) {
        UserContact contact = contacts.currentContact();
        MessagingAccount account =
                accounts.findById(accountId)
                        .filter(
                                a ->
                                        a.getUserContact() != null
                                                && a.getUserContact()
                                                        .getId()
                                                        .equals(contact.getId()))
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Messaging account not found"));
        account.setStatus(MessagingAccountStatus.INACTIVE);
        accounts.save(account);
    }
}
