package br.com.gestaodireta.user.entity;

import br.com.gestaodireta.shared.audit.BaseEntity;
import br.com.gestaodireta.user.enumeration.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_contacts")
public class UserContact extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "phone_verification_status", nullable = false, length = 30)
    private PhoneVerificationStatus phoneVerificationStatus;

    @Column(name = "phone_verified_at")
    private LocalDateTime phoneVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_channel", nullable = false, length = 30)
    private PreferredMessagingChannel preferredChannel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserContactStatus status;

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User v) {
        user = v;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String v) {
        phoneNumber = v;
    }

    public PhoneVerificationStatus getPhoneVerificationStatus() {
        return phoneVerificationStatus;
    }

    public void setPhoneVerificationStatus(PhoneVerificationStatus v) {
        phoneVerificationStatus = v;
    }

    public LocalDateTime getPhoneVerifiedAt() {
        return phoneVerifiedAt;
    }

    public void setPhoneVerifiedAt(LocalDateTime v) {
        phoneVerifiedAt = v;
    }

    public PreferredMessagingChannel getPreferredChannel() {
        return preferredChannel;
    }

    public void setPreferredChannel(PreferredMessagingChannel v) {
        preferredChannel = v;
    }

    public UserContactStatus getStatus() {
        return status;
    }

    public void setStatus(UserContactStatus v) {
        status = v;
    }
}
