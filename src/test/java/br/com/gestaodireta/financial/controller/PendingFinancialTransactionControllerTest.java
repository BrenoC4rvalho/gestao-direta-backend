package br.com.gestaodireta.financial.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.PendingFinancialTransaction;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.PaymentMethod;
import br.com.gestaodireta.financial.enumeration.PaymentStatus;
import br.com.gestaodireta.financial.enumeration.PendingFinancialTransactionStatus;
import br.com.gestaodireta.financial.enumeration.PendingTransactionSource;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.FinancialTransactionRepository;
import br.com.gestaodireta.financial.repository.PendingFinancialTransactionRepository;
import br.com.gestaodireta.messaging.domain.MessagingAccount;
import br.com.gestaodireta.messaging.domain.MessagingConversation;
import br.com.gestaodireta.messaging.domain.MessagingMessage;
import br.com.gestaodireta.messaging.enumeration.MessagingAccountStatus;
import br.com.gestaodireta.messaging.enumeration.MessagingChannel;
import br.com.gestaodireta.messaging.enumeration.MessagingConversationStatus;
import br.com.gestaodireta.messaging.enumeration.MessagingConversationStep;
import br.com.gestaodireta.messaging.enumeration.MessagingDirection;
import br.com.gestaodireta.messaging.enumeration.MessagingMessageStatus;
import br.com.gestaodireta.messaging.enumeration.MessagingMessageType;
import br.com.gestaodireta.messaging.repository.MessagingAccountRepository;
import br.com.gestaodireta.messaging.repository.MessagingConversationRepository;
import br.com.gestaodireta.messaging.repository.MessagingMessageRepository;
import br.com.gestaodireta.messaging.telegram.client.TelegramBotClient;
import br.com.gestaodireta.messaging.telegram.client.TelegramSendMessageResult;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.enumeration.UserType;
import br.com.gestaodireta.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PendingFinancialTransactionControllerTest extends PostgresIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private PendingFinancialTransactionRepository pendingRepository;
    @Autowired private FinancialTransactionRepository transactionRepository;
    @Autowired private FinancialCategoryRepository categoryRepository;
    @Autowired private MessagingMessageRepository messageRepository;
    @Autowired private MessagingConversationRepository conversationRepository;
    @Autowired private MessagingAccountRepository accountRepository;
    @Autowired private FarmRepository farmRepository;
    @Autowired private UserRepository userRepository;
    @MockBean private TelegramBotClient telegramBotClient;

    @BeforeEach
    void setUp() {
        when(telegramBotClient.sendMessage(anyString(), anyString()))
                .thenReturn(new TelegramSendMessageResult("outbound"));
        pendingRepository.deleteAll();
        transactionRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        accountRepository.deleteAll();
        categoryRepository.deleteAll();
        farmRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void approvesPaidPendingAndPersistsAuditAndFinancialCopy() throws Exception {
        Fixture fixture = fixture();
        mockMvc.perform(
                        post(
                                        "/api/pending-financial-transactions/{id}/approve",
                                        fixture.pending.getId())
                                .contextPath("/api")
                                .with(user(String.valueOf(fixture.reviewer.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"type\":\"EXPENSE\",\"amount\":1000.00,\"description\":\"Fuel updated\",\"paymentMethod\":\"PIX\",\"transactionDate\":\"2026-07-21\",\"notes\":\"Paid by PIX\",\"status\":\"PAID\",\"paidAt\":\"2026-07-21\",\"dueDate\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("status").value("APPROVED"));

        assertThat(transactionRepository.count()).isEqualTo(1);
        var transaction = transactionRepository.findAll().getFirst();
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(transaction.getDescription()).isEqualTo("Fuel updated");
        assertThat(transaction.getPaymentMethod()).isEqualTo(PaymentMethod.PIX);
        assertThat(transaction.getNotes()).isEqualTo("Paid by PIX");
        assertThat(transaction.getPaidAt()).isEqualTo(LocalDate.of(2026, 7, 21));
        assertThat(transaction.getDueDate()).isNull();
        assertThat(transaction.getFarm().getId()).isEqualTo(fixture.farm.getId());
        assertThat(transaction.getCategory().getId()).isEqualTo(fixture.category.getId());
        assertThat(transaction.getCreatedByUser().getId()).isEqualTo(fixture.reviewer.getId());
        PendingFinancialTransaction saved =
                pendingRepository.findById(fixture.pending.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PendingFinancialTransactionStatus.APPROVED);
        assertThat(saved.getReviewedByUser().getId()).isEqualTo(fixture.reviewer.getId());
        assertThat(saved.getReviewedAt()).isNotNull();
        assertThat(saved.getApprovedFinancialTransaction().getId()).isEqualTo(transaction.getId());
    }

    @Test
    void validatesApprovalShapeAndPreventsDuplicateApproval() throws Exception {
        Fixture fixture = fixture();
        String path = "/api/pending-financial-transactions/" + fixture.pending.getId() + "/approve";
        mockMvc.perform(
                        post(path)
                                .contextPath("/api")
                                .with(user(String.valueOf(fixture.reviewer.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"status\":\"PENDING\",\"paidAt\":null,\"dueDate\":null}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        post(path)
                                .contextPath("/api")
                                .with(user(String.valueOf(fixture.reviewer.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"status\":\"PAID\",\"paidAt\":null,\"dueDate\":\"2026-07-30\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        post(path)
                                .contextPath("/api")
                                .with(user(String.valueOf(fixture.reviewer.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"PAID\",\"paidAt\":null,\"dueDate\":null}"))
                .andExpect(status().isOk());
        mockMvc.perform(
                        post(path)
                                .contextPath("/api")
                                .with(user(String.valueOf(fixture.reviewer.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"PAID\",\"paidAt\":null,\"dueDate\":null}"))
                .andExpect(status().isConflict());
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsWithoutCreatingTransactionAndNotifiesReasonWhenProvided() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(
                        post(
                                        "/api/pending-financial-transactions/{id}/reject",
                                        fixture.pending.getId())
                                .contextPath("/api")
                                .with(user(String.valueOf(fixture.reviewer.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Duplicated receipt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("status").value("REJECTED"))
                .andExpect(jsonPath("rejectionReason").value("Duplicated receipt"));

        assertThat(transactionRepository.count()).isZero();
        assertThat(messageRepository.findAll())
                .anySatisfy(
                        message ->
                                assertThat(message.getContent())
                                        .contains("Motivo: Duplicated receipt"));
    }

    @Test
    void rejectsWithoutReasonUsingTheBaseNotification() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(
                        post(
                                        "/api/pending-financial-transactions/{id}/reject",
                                        fixture.pending.getId())
                                .contextPath("/api")
                                .with(user(String.valueOf(fixture.reviewer.getId())).roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk());

        assertThat(messageRepository.findAll())
                .anySatisfy(
                        message ->
                                assertThat(message.getContent())
                                        .isEqualTo(
                                                "A movimentação enviada foi rejeitada no Gestão Direta."));
    }

    private Fixture fixture() {
        User reviewer = new User();
        reviewer.setName("Reviewer");
        reviewer.setEmail("reviewer-" + System.nanoTime() + "@test.com");
        reviewer.setPassword("secret");
        reviewer.setUserType(UserType.ADMIN);
        reviewer.setStatus(UserStatus.ACTIVE);
        reviewer = userRepository.save(reviewer);
        Farm farm = new Farm();
        farm.setName("Farm");
        farm.setStatus(FarmStatus.ACTIVE);
        farm = farmRepository.save(farm);
        FinancialCategory category = new FinancialCategory();
        category.setFarm(farm);
        category.setName("Fuel");
        category.setType(TransactionType.EXPENSE);
        category.setStatus(FinancialCategoryStatus.ACTIVE);
        category = categoryRepository.save(category);
        MessagingAccount account = new MessagingAccount();
        account.setChannel(MessagingChannel.TELEGRAM);
        account.setExternalUserId("user-" + System.nanoTime());
        account.setExternalChatId("chat-" + System.nanoTime());
        account.setStatus(MessagingAccountStatus.ACTIVE);
        account = accountRepository.save(account);
        MessagingConversation conversation = new MessagingConversation();
        conversation.setMessagingAccount(account);
        conversation.setFarm(farm);
        conversation.setStatus(MessagingConversationStatus.ACTIVE);
        conversation.setCurrentStep(MessagingConversationStep.NONE);
        conversation.setLastInteractionAt(LocalDateTime.now());
        conversation.setExpiresAt(LocalDateTime.now().plusDays(1));
        conversation = conversationRepository.save(conversation);
        MessagingMessage message = new MessagingMessage();
        message.setMessagingConversation(conversation);
        message.setChannel(MessagingChannel.TELEGRAM);
        message.setDirection(MessagingDirection.INBOUND);
        message.setMessageType(MessagingMessageType.TEXT);
        message.setContent("Fuel");
        message.setStatus(MessagingMessageStatus.PROCESSED);
        message = messageRepository.save(message);
        PendingFinancialTransaction pending = new PendingFinancialTransaction();
        pending.setFarm(farm);
        pending.setRequestedByUser(reviewer);
        pending.setMessagingAccount(account);
        pending.setMessagingConversation(conversation);
        pending.setSourceMessage(message);
        pending.setSourceChannel(PendingTransactionSource.TELEGRAM);
        pending.setType(TransactionType.EXPENSE);
        pending.setAmount(new BigDecimal("1000.00"));
        pending.setTransactionDate(LocalDate.of(2026, 7, 20));
        pending.setDescription("Fuel");
        pending.setSuggestedCategory(category);
        pending.setStatus(PendingFinancialTransactionStatus.PENDING_REVIEW);
        pending.setConfidence(new BigDecimal("0.900"));
        pending = pendingRepository.save(pending);
        return new Fixture(reviewer, farm, category, pending);
    }

    private record Fixture(
            User reviewer,
            Farm farm,
            FinancialCategory category,
            PendingFinancialTransaction pending) {}
}
