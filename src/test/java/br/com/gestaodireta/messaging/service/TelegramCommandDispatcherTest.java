package br.com.gestaodireta.messaging.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.gestaodireta.messaging.domain.MessagingAccount;
import br.com.gestaodireta.messaging.domain.MessagingConversation;
import br.com.gestaodireta.messaging.enumeration.MessagingAccountStatus;
import br.com.gestaodireta.messaging.repository.MessagingConversationRepository;
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
}
