package br.com.gestaodireta.user.service;

import br.com.gestaodireta.messaging.repository.MessagingAccountRepository;
import br.com.gestaodireta.shared.exception.*;
import br.com.gestaodireta.shared.security.SecurityUtils;
import br.com.gestaodireta.user.dto.*;
import br.com.gestaodireta.user.entity.*;
import br.com.gestaodireta.user.enumeration.*;
import br.com.gestaodireta.user.repository.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserContactService {
    private static final Pattern PHONE = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private final UserRepository users;
    private final UserContactRepository contacts;
    private final MessagingAccountRepository accounts;

    public UserContactService(
            UserRepository users,
            UserContactRepository contacts,
            MessagingAccountRepository accounts) {
        this.users = users;
        this.contacts = contacts;
        this.accounts = accounts;
    }

    @Transactional
    public UserContact currentContact() {
        return contacts.findByUserId(SecurityUtils.getAuthenticatedUserId())
                .orElseGet(() -> create(activeUser()));
    }

    @Transactional
    public UserContactResponse me() {
        return response(currentContact());
    }

    @Transactional
    public UserContactResponse updatePhone(UpdatePhoneRequest request) {
        UserContact contact = currentContact();
        String phone = normalize(request.phoneNumber());
        if (!phone.equals(contact.getPhoneNumber())) {
            if (contacts.existsByPhoneNumberAndIdNot(phone, contact.getId()))
                throw new BusinessException("Phone number is already in use");
            contact.setPhoneNumber(phone);
            contact.setPhoneVerificationStatus(PhoneVerificationStatus.PENDING);
            contact.setPhoneVerifiedAt(null);
        }
        return response(contacts.save(contact));
    }

    public String normalize(String value) {
        String normalized = value == null ? "" : value.replaceAll("[\\s()\\-]", "");
        if (!PHONE.matcher(normalized).matches())
            throw new ValidationException("Phone number must be in international format");
        return normalized;
    }

    private User activeUser() {
        return users.findById(SecurityUtils.getAuthenticatedUserId())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ForbiddenException("Access denied"));
    }

    private UserContact create(User user) {
        UserContact c = new UserContact();
        c.setUser(user);
        c.setPhoneVerificationStatus(PhoneVerificationStatus.NOT_INFORMED);
        c.setPreferredChannel(PreferredMessagingChannel.NONE);
        c.setStatus(UserContactStatus.PENDING);
        return contacts.save(c);
    }

    private UserContactResponse response(UserContact c) {
        return new UserContactResponse(
                c.getId(),
                c.getPhoneNumber(),
                c.getPhoneVerificationStatus(),
                c.getPhoneVerifiedAt(),
                c.getPreferredChannel(),
                c.getStatus(),
                accounts.findAll().stream()
                        .filter(
                                a ->
                                        a.getUserContact() != null
                                                && a.getUserContact().getId().equals(c.getId()))
                        .map(
                                a ->
                                        new UserContactMessagingAccountResponse(
                                                a.getId(),
                                                a.getChannel(),
                                                a.getUsername(),
                                                a.getDisplayName(),
                                                a.getStatus(),
                                                a.getVerifiedAt(),
                                                a.getCreatedAt()))
                        .toList());
    }
}
