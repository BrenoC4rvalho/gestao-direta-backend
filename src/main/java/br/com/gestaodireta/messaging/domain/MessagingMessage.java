package br.com.gestaodireta.messaging.domain;

import br.com.gestaodireta.messaging.enumeration.*;
import br.com.gestaodireta.shared.audit.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messaging_messages")
public class MessagingMessage extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "messaging_conversation_id", nullable = false)
    private MessagingConversation messagingConversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MessagingChannel channel;

    @Column(name = "provider_update_id", length = 100)
    private String providerUpdateId;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "external_user_id", length = 100)
    private String externalUserId;

    @Column(name = "external_chat_id", length = 100)
    private String externalChatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessagingDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private MessagingMessageType messageType;

    @Column(nullable = false, length = 4096)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MessagingMessageStatus status;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    public Long getId() {
        return id;
    }

    public MessagingConversation getMessagingConversation() {
        return messagingConversation;
    }

    public void setMessagingConversation(MessagingConversation v) {
        messagingConversation = v;
    }

    public MessagingChannel getChannel() {
        return channel;
    }

    public void setChannel(MessagingChannel v) {
        channel = v;
    }

    public String getProviderUpdateId() {
        return providerUpdateId;
    }

    public void setProviderUpdateId(String v) {
        providerUpdateId = v;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String v) {
        providerMessageId = v;
    }

    public String getExternalUserId() {
        return externalUserId;
    }

    public void setExternalUserId(String v) {
        externalUserId = v;
    }

    public String getExternalChatId() {
        return externalChatId;
    }

    public void setExternalChatId(String v) {
        externalChatId = v;
    }

    public MessagingDirection getDirection() {
        return direction;
    }

    public void setDirection(MessagingDirection v) {
        direction = v;
    }

    public MessagingMessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessagingMessageType v) {
        messageType = v;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String v) {
        content = v;
    }

    public MessagingMessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessagingMessageStatus v) {
        status = v;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime v) {
        receivedAt = v;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime v) {
        sentAt = v;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime v) {
        processedAt = v;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String v) {
        errorMessage = v;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String v) {
        rawPayload = v;
    }
}
