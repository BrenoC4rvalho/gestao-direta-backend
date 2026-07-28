package br.com.gestaodireta.messaging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.gestaodireta.ai.config.FinancialExtractionProperties;
import br.com.gestaodireta.ai.service.FinancialTransactionExtractionService;
import br.com.gestaodireta.ai.service.dto.FinancialTransactionExtractionResult;
import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.financial.entity.PendingFinancialTransaction;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.financial.repository.PendingFinancialTransactionRepository;
import br.com.gestaodireta.messaging.domain.MessagingAccount;
import br.com.gestaodireta.messaging.domain.MessagingConversation;
import br.com.gestaodireta.messaging.domain.MessagingMessage;
import br.com.gestaodireta.messaging.enumeration.MessagingAccountStatus;
import br.com.gestaodireta.messaging.enumeration.MessagingConversationStatus;
import br.com.gestaodireta.messaging.enumeration.MessagingMessageType;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.entity.UserContact;
import br.com.gestaodireta.user.enumeration.UserContactStatus;
import br.com.gestaodireta.user.enumeration.UserStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class TelegramPendingFinancialTransactionTest {
    @ParameterizedTest
    @ValueSource(strings = {"90", "1000", "R$ 250", "a", "x", ".", "oi", "teste"})
    void shouldNotCallAiOrCreatePendingForIneligibleMessage(String text) {
        Fixture fixture = fixture();

        fixture.processor.process(fixture.account, fixture.conversation, message(text));

        verify(fixture.extractionService, never()).extract(any(), any(), any());
        verify(fixture.pendingRepository, never()).save(any());
        verify(fixture.outgoing, times(1)).send(any(), any());
    }

    @Test
    void shouldCreateOnlyPendingForConsistentFinancialMessage() {
        Fixture fixture = fixture();
        when(fixture.extractionService.isEnabled()).thenReturn(true);
        when(fixture.extractionService.model()).thenReturn("fake");
        when(fixture.extractionService.extract(any(), any(), any()))
                .thenReturn(
                        new FinancialTransactionExtractionResult(
                                true,
                                TransactionType.EXPENSE,
                                new BigDecimal("90.00"),
                                LocalDate.of(2026, 7, 27),
                                "Combustível",
                                null,
                                new BigDecimal("0.90"),
                                List.of()));

        fixture.processor.process(
                fixture.account, fixture.conversation, message("Gastei R$ 90 com combustível"));

        ArgumentCaptor<PendingFinancialTransaction> pending =
                ArgumentCaptor.forClass(PendingFinancialTransaction.class);
        verify(fixture.pendingRepository).save(pending.capture());
        assertThat(pending.getValue().getAmount()).isEqualByComparingTo("90.00");
        assertThat(pending.getValue().getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(pending.getValue().getDescription()).isNotBlank();
        verify(fixture.outgoing, times(1)).send(any(), any());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "vendi 20kg de milho por 100 reais hoje",
                "foi vendido 20kg de milho e recebi 100 reais hoje"
            })
    void shouldCreatePendingForSaleWithPhysicalQuantityAndMonetaryAmount(String text) {
        Fixture fixture = fixture();
        when(fixture.extractionService.isEnabled()).thenReturn(true);
        when(fixture.extractionService.model()).thenReturn("fake");
        when(fixture.extractionService.extract(any(), any(), any()))
                .thenReturn(
                        new FinancialTransactionExtractionResult(
                                true,
                                TransactionType.INCOME,
                                new BigDecimal("100.00"),
                                LocalDate.of(2026, 7, 27),
                                "Venda de 20 kg de milho",
                                "Venda de produção",
                                new BigDecimal("0.95"),
                                List.of()));

        fixture.processor.process(fixture.account, fixture.conversation, message(text));

        ArgumentCaptor<PendingFinancialTransaction> pending =
                ArgumentCaptor.forClass(PendingFinancialTransaction.class);
        verify(fixture.extractionService, times(1)).extract(any(), any(), any());
        verify(fixture.pendingRepository, times(1)).save(pending.capture());
        assertThat(pending.getValue().getAmount()).isEqualByComparingTo("100.00");
        assertThat(pending.getValue().getAmount()).isNotEqualByComparingTo("20.00");
        assertThat(pending.getValue().getType()).isEqualTo(TransactionType.INCOME);
        assertThat(pending.getValue().getDescription()).isNotBlank();
        verify(fixture.outgoing, times(1)).send(any(), any());
    }

    private Fixture fixture() {
        FinancialTransactionExtractionService extractionService =
                mock(FinancialTransactionExtractionService.class);
        PendingFinancialTransactionRepository pendingRepository =
                mock(PendingFinancialTransactionRepository.class);
        FinancialCategoryRepository categoryRepository = mock(FinancialCategoryRepository.class);
        FarmUserRepository farmUsers = mock(FarmUserRepository.class);
        OutgoingMessagingService outgoing = mock(OutgoingMessagingService.class);
        when(pendingRepository.findBySourceMessageId(any())).thenReturn(Optional.empty());
        when(farmUsers.findRoleByFarmIdAndUserId(any(), any()))
                .thenReturn(Optional.of(FarmUserRole.PRODUCER));
        when(categoryRepository.findByFarmId(any(), anyBoolean(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        FinancialMessageEvidenceExtractor evidence = new FinancialMessageEvidenceExtractor();
        FinancialExtractionProperties properties = new FinancialExtractionProperties();
        properties.setMinimumConfidence(0.60);
        TelegramFinancialExtractionProcessor processor =
                new TelegramFinancialExtractionProcessor(
                        extractionService,
                        pendingRepository,
                        categoryRepository,
                        farmUsers,
                        outgoing,
                        new TelegramFinancialMessageEligibilityValidator(evidence),
                        new FinancialTransactionExtractionResultValidator(properties, evidence),
                        Clock.system(ZoneOffset.UTC));
        return new Fixture(
                processor,
                extractionService,
                pendingRepository,
                outgoing,
                account(),
                conversation());
    }

    private MessagingAccount account() {
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);
        UserContact contact = new UserContact();
        contact.setUser(user);
        contact.setStatus(UserContactStatus.ACTIVE);
        MessagingAccount account = new MessagingAccount();
        account.setStatus(MessagingAccountStatus.ACTIVE);
        account.setUserContact(contact);
        return account;
    }

    private MessagingConversation conversation() {
        Farm farm = new Farm();
        farm.setName("Boa Vista");
        MessagingConversation conversation = new MessagingConversation();
        conversation.setFarm(farm);
        conversation.setStatus(MessagingConversationStatus.ACTIVE);
        return conversation;
    }

    private MessagingMessage message(String content) {
        MessagingMessage message = new MessagingMessage();
        message.setMessageType(MessagingMessageType.TEXT);
        message.setContent(content);
        return message;
    }

    private record Fixture(
            TelegramFinancialExtractionProcessor processor,
            FinancialTransactionExtractionService extractionService,
            PendingFinancialTransactionRepository pendingRepository,
            OutgoingMessagingService outgoing,
            MessagingAccount account,
            MessagingConversation conversation) {}
}
