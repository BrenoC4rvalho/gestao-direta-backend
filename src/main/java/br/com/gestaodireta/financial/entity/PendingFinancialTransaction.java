package br.com.gestaodireta.financial.entity;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.financial.enumeration.PendingFinancialTransactionStatus;
import br.com.gestaodireta.financial.enumeration.PendingTransactionSource;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.messaging.domain.MessagingAccount;
import br.com.gestaodireta.messaging.domain.MessagingConversation;
import br.com.gestaodireta.messaging.domain.MessagingMessage;
import br.com.gestaodireta.shared.audit.BaseEntity;
import br.com.gestaodireta.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pending_financial_transactions")
public class PendingFinancialTransaction extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false)
    private Farm farm;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedByUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "messaging_account_id", nullable = false)
    private MessagingAccount messagingAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "messaging_conversation_id", nullable = false)
    private MessagingConversation messagingConversation;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_message_id", nullable = false, unique = true)
    private MessagingMessage sourceMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_channel", nullable = false, length = 30)
    private PendingTransactionSource sourceChannel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @NotBlank
    @Size(max = 160)
    @Column(nullable = false, length = 160)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_category_id")
    private FinancialCategory suggestedCategory;

    @Column(name = "raw_category_name", length = 100)
    private String rawCategoryName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PendingFinancialTransactionStatus status;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "ai_model", length = 120)
    private String aiModel;

    @Column(name = "ai_processed_at")
    private LocalDateTime aiProcessedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedByUser;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_financial_transaction_id", unique = true)
    private FinancialTransaction approvedFinancialTransaction;

    public Long getId() {
        return id;
    }

    public Farm getFarm() {
        return farm;
    }

    public void setFarm(Farm value) {
        farm = value;
    }

    public User getRequestedByUser() {
        return requestedByUser;
    }

    public void setRequestedByUser(User value) {
        requestedByUser = value;
    }

    public MessagingAccount getMessagingAccount() {
        return messagingAccount;
    }

    public void setMessagingAccount(MessagingAccount value) {
        messagingAccount = value;
    }

    public MessagingConversation getMessagingConversation() {
        return messagingConversation;
    }

    public void setMessagingConversation(MessagingConversation value) {
        messagingConversation = value;
    }

    public MessagingMessage getSourceMessage() {
        return sourceMessage;
    }

    public void setSourceMessage(MessagingMessage value) {
        sourceMessage = value;
    }

    public PendingTransactionSource getSourceChannel() {
        return sourceChannel;
    }

    public void setSourceChannel(PendingTransactionSource value) {
        sourceChannel = value;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType value) {
        type = value;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal value) {
        amount = value;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate value) {
        transactionDate = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String value) {
        description = value;
    }

    public FinancialCategory getSuggestedCategory() {
        return suggestedCategory;
    }

    public void setSuggestedCategory(FinancialCategory value) {
        suggestedCategory = value;
    }

    public String getRawCategoryName() {
        return rawCategoryName;
    }

    public void setRawCategoryName(String value) {
        rawCategoryName = value;
    }

    public PendingFinancialTransactionStatus getStatus() {
        return status;
    }

    public void setStatus(PendingFinancialTransactionStatus value) {
        status = value;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal value) {
        confidence = value;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String value) {
        aiModel = value;
    }

    public LocalDateTime getAiProcessedAt() {
        return aiProcessedAt;
    }

    public void setAiProcessedAt(LocalDateTime value) {
        aiProcessedAt = value;
    }

    public User getReviewedByUser() {
        return reviewedByUser;
    }

    public void setReviewedByUser(User value) {
        reviewedByUser = value;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime value) {
        reviewedAt = value;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String value) {
        rejectionReason = value;
    }

    public FinancialTransaction getApprovedFinancialTransaction() {
        return approvedFinancialTransaction;
    }

    public void setApprovedFinancialTransaction(FinancialTransaction value) {
        approvedFinancialTransaction = value;
    }
}
