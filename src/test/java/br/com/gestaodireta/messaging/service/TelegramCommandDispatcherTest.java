package br.com.gestaodireta.messaging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.messaging.domain.MessagingAccount;
import br.com.gestaodireta.messaging.domain.MessagingConversation;
import br.com.gestaodireta.messaging.enumeration.MessagingAccountStatus;
import br.com.gestaodireta.messaging.enumeration.MessagingConversationStatus;
import br.com.gestaodireta.messaging.enumeration.MessagingConversationStep;
import br.com.gestaodireta.messaging.repository.MessagingConversationRepository;
import br.com.gestaodireta.user.entity.User;
import br.com.gestaodireta.user.entity.UserContact;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelegramCommandDispatcherTest {
    @Mock private MessagingConversationService conversations;
    @Mock private MessagingConversationRepository conversationRepository;
    @Mock private MessagingFarmContextService farms;
    @Mock private MessagingLinkService links;
    @Mock private OutgoingMessagingService outgoing;

    @InjectMocks private TelegramCommandDispatcher dispatcher;

    @Test
    void shouldProcessLinkBeforeRejectingInactiveAccount() {
        MessagingAccount account = new MessagingAccount();
        account.setStatus(MessagingAccountStatus.INACTIVE);
        MessagingConversation conversation = new MessagingConversation();
        when(links.link(account, "363773")).thenReturn(MessagingLinkResult.TEMPORARILY_BLOCKED);

        dispatcher.dispatch(account, conversation, "/vincular 363773");

        verify(links).link(account, "363773");
        verify(outgoing, never())
                .send(
                        eq(conversation),
                        eq(
                                "Sua conta de mensagens está indisponível. Acesse o Gestão Direta para verificar seu acesso."));
        verify(outgoing)
                .send(
                        eq(conversation),
                        eq(
                                "Não foi possível concluir a vinculação agora. Aguarde alguns minutos e tente novamente."));
    }

    @Test
    void shouldConsumeValidFarmSelectionBeforeFinancialExtraction() {
        MessagingAccount account = activeAccount();
        MessagingConversation conversation = waitingForFarmSelection();
        conversation.setMessagingAccount(account);
        Farm selectedFarm = new Farm();
        selectedFarm.setName("Fazenda Boa Sorte");
        when(farms.select(conversation, "1"))
                .thenAnswer(
                        invocation -> {
                            conversation.setFarm(selectedFarm);
                            conversation.setStatus(MessagingConversationStatus.ACTIVE);
                            conversation.setCurrentStep(MessagingConversationStep.NONE);
                            return true;
                        });
        when(farms.accessibleFarms(any())).thenReturn(List.of(selectedFarm));

        boolean handled = dispatcher.dispatch(account, conversation, "1");

        assertThat(handled).isTrue();
        assertThat(conversation.getFarm()).isSameAs(selectedFarm);
        assertThat(conversation.getStatus()).isEqualTo(MessagingConversationStatus.ACTIVE);
        assertThat(conversation.getCurrentStep()).isEqualTo(MessagingConversationStep.NONE);
        verify(conversationRepository).save(conversation);
        verify(outgoing).send(conversation, "Fazenda selecionada: Fazenda Boa Sorte.");
    }

    @Test
    void shouldKeepFarmUnsetForInvalidFarmSelection() {
        MessagingAccount account = activeAccount();
        MessagingConversation conversation = waitingForFarmSelection();
        conversation.setMessagingAccount(account);
        Farm availableFarm = new Farm();
        availableFarm.setName("Fazenda Boa Sorte");
        when(farms.select(conversation, "A")).thenReturn(false);
        when(farms.accessibleFarms(any())).thenReturn(List.of(availableFarm));

        boolean handled = dispatcher.dispatch(account, conversation, "A");

        assertThat(handled).isTrue();
        assertThat(conversation.getFarm()).isNull();
        verify(conversationRepository, never()).save(conversation);
        verify(outgoing)
                .send(
                        eq(conversation),
                        eq(
                                "Opção inválida. Envie o número de uma das fazendas disponíveis.\n\n"
                                        + "Selecione a fazenda que deseja utilizar:\n\n"
                                        + "1. Fazenda Boa Sorte\n\n"
                                        + "Responda com o número da opção."));
    }

    private MessagingAccount activeAccount() {
        User user = new User();
        UserContact contact = new UserContact();
        contact.setUser(user);
        MessagingAccount account = new MessagingAccount();
        account.setStatus(MessagingAccountStatus.ACTIVE);
        account.setUserContact(contact);
        return account;
    }

    private MessagingConversation waitingForFarmSelection() {
        MessagingConversation conversation = new MessagingConversation();
        conversation.setStatus(MessagingConversationStatus.WAITING_FARM_SELECTION);
        conversation.setCurrentStep(MessagingConversationStep.WAITING_FARM_SELECTION);
        return conversation;
    }
}
