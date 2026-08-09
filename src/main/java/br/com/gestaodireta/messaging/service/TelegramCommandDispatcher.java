package br.com.gestaodireta.messaging.service;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.messaging.domain.*;
import br.com.gestaodireta.messaging.enumeration.*;
import br.com.gestaodireta.messaging.repository.MessagingConversationRepository;
import java.time.*;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelegramCommandDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramCommandDispatcher.class);
    private static final String LINK_GUIDANCE =
            "Bem-vindo ao Gestão Direta.\n\nSua conta do Telegram ainda não está vinculada.\n\nAcesse o sistema, gere um código de vinculação e envie:\n\n/vincular SEU_CODIGO";
    private final MessagingConversationService conversations;
    private final MessagingConversationRepository conversationRepository;
    private final MessagingFarmContextService farms;
    private final MessagingLinkService links;
    private final OutgoingMessagingService outgoing;

    public TelegramCommandDispatcher(
            MessagingConversationService conversations,
            MessagingConversationRepository conversationRepository,
            MessagingFarmContextService farms,
            MessagingLinkService links,
            OutgoingMessagingService outgoing) {
        this.conversations = conversations;
        this.conversationRepository = conversationRepository;
        this.farms = farms;
        this.links = links;
        this.outgoing = outgoing;
    }

    @Transactional
    public void dispatch(
            MessagingAccount account, MessagingConversation conversation, String content) {
        String text = content.trim();
        String command =
                text.startsWith("/")
                        ? text.split("\\s+", 2)[0].replaceFirst("@[^\\s]+$", "").toLowerCase()
                        : null;
        if ("/vincular".equals(command)) {
            pending(account, conversation, command, text);
            return;
        }
        if (account.getStatus() == MessagingAccountStatus.BLOCKED
                || account.getStatus() == MessagingAccountStatus.INACTIVE) {
            rejection(account, "ACCOUNT_" + account.getStatus());
            outgoing.send(
                    conversation,
                    "Sua conta de mensagens está indisponível. Acesse o Gestão Direta para verificar seu acesso.");
            return;
        }
        if (account.getStatus() != MessagingAccountStatus.ACTIVE
                || account.getUserContact() == null) {
            pending(account, conversation, command, text);
            return;
        }
        if ("/start".equals(command)) {
            outgoing.send(
                    conversation,
                    "Bem-vindo ao Gestão Direta.\n\nUse /ajuda para consultar os comandos disponíveis.");
            resolve(conversation, true);
            return;
        }
        if ("/ajuda".equals(command)) {
            outgoing.send(
                    conversation,
                    "Comandos disponíveis:\n\n/start — iniciar ou continuar\n/fazendas — listar suas fazendas\n/fazenda — mostrar a fazenda atual\n/trocar_fazenda — selecionar outra fazenda\n/sair — finalizar a conversa\n/ajuda — mostrar esta ajuda");
            return;
        }
        if ("/fazendas".equals(command)) {
            listFarms(conversation);
            return;
        }
        if ("/fazenda".equals(command)) {
            currentFarm(conversation);
            return;
        }
        if ("/trocar_fazenda".equals(command)) {
            conversation.setFarm(null);
            resolve(conversation, true);
            return;
        }
        if ("/sair".equals(command)) {
            conversation.setFarm(null);
            conversation.setStatus(MessagingConversationStatus.COMPLETED);
            conversation.setCurrentStep(MessagingConversationStep.NONE);
            conversationRepository.save(conversation);
            outgoing.send(
                    conversation, "Conversa finalizada. Envie /start para começar novamente.");
            return;
        }
        if (conversation.getCurrentStep() == MessagingConversationStep.WAITING_FARM_SELECTION) {
            if (farms.select(conversation, text)) {
                conversationRepository.save(conversation);
                outgoing.send(
                        conversation,
                        "Fazenda selecionada: " + conversation.getFarm().getName() + ".");
            } else {
                outgoing.send(
                        conversation,
                        "Opção inválida. Envie o número de uma das fazendas disponíveis.\n\n"
                                + options(conversation));
            }
            return;
        }
        if (conversation.getFarm() == null) {
            resolve(conversation, true);
            return;
        }
        if (!farms.accessibleFarms(account.getUserContact().getUser().getId()).stream()
                .anyMatch(f -> f.getId().equals(conversation.getFarm().getId()))) {
            conversation.setFarm(null);
            resolve(conversation, true);
            return;
        }
        // Non-command text is handled by TelegramFinancialExtractionProcessor.
    }

    private void pending(
            MessagingAccount account,
            MessagingConversation conversation,
            String command,
            String text) {
        if ("/ajuda".equals(command)) {
            outgoing.send(
                    conversation, "Comandos disponíveis:\n\n/start\n/vincular CODIGO\n/ajuda");
            return;
        }
        if ("/vincular".equals(command)) {
            String[] values = text.split("\\s+");
            if (values.length != 2 || !values[1].matches("\\d{6}")) {
                outgoing.send(
                        conversation,
                        "Informe o código de vinculação.\n\nExemplo:\n/vincular 482913");
                return;
            }
            MessagingLinkResult result = links.link(account, values[1]);
            if (result == MessagingLinkResult.LINKED) {
                outgoing.send(
                        conversation,
                        "Telegram vinculado com sucesso à sua conta do Gestão Direta.");
                resolve(conversation, true);
            } else if (result == MessagingLinkResult.TEMPORARILY_BLOCKED) {
                rejection(account, "ACCOUNT_BLOCKED");
                outgoing.send(
                        conversation,
                        "Não foi possível concluir a vinculação agora. Aguarde alguns minutos e tente novamente.");
            } else {
                rejection(account, linkRejectionReason(result));
                outgoing.send(
                        conversation,
                        "Código inválido ou expirado. Gere um novo código no Gestão Direta.");
            }
            return;
        }
        if ("/start".equals(command) || command == null) {
            outgoing.send(conversation, LINK_GUIDANCE);
        } else {
            outgoing.send(conversation, "Sua conta ainda não está vinculada ao Gestão Direta.");
        }
    }

    private String linkRejectionReason(MessagingLinkResult result) {
        return switch (result) {
            case ALREADY_LINKED -> "ACCOUNT_ALREADY_LINKED";
            case LINK_CODE_NOT_FOUND -> "LINK_CODE_NOT_FOUND";
            case LINK_CODE_EXPIRED -> "LINK_CODE_EXPIRED";
            case TEMPORARILY_BLOCKED -> "ACCOUNT_BLOCKED";
            case LINKED -> throw new IllegalArgumentException("Linked result is not a rejection");
        };
    }

    private void rejection(MessagingAccount account, String reason) {
        LOGGER.info(
                "telegram command rejected: reason={} messagingAccountId={}",
                reason,
                account.getId());
    }

    private void resolve(MessagingConversation conversation, boolean notify) {
        farms.resolve(conversation);
        conversationRepository.save(conversation);
        if (!notify) return;
        if (conversation.getStatus() == MessagingConversationStatus.COMPLETED)
            outgoing.send(conversation, "Sua conta não possui acesso ativo a nenhuma fazenda.");
        else if (conversation.getFarm() != null)
            outgoing.send(
                    conversation, "Fazenda selecionada: " + conversation.getFarm().getName() + ".");
        else outgoing.send(conversation, options(conversation));
    }

    private void listFarms(MessagingConversation conversation) {
        List<Farm> accessible =
                farms.accessibleFarms(
                        conversation.getMessagingAccount().getUserContact().getUser().getId());
        if (accessible.isEmpty())
            outgoing.send(conversation, "Sua conta não possui acesso ativo a nenhuma fazenda.");
        else outgoing.send(conversation, "Suas fazendas:\n\n" + numbered(accessible));
    }

    private void currentFarm(MessagingConversation conversation) {
        if (conversation.getFarm() == null)
            outgoing.send(
                    conversation,
                    "Nenhuma fazenda está selecionada.\n\nUse /trocar_fazenda para selecionar uma fazenda.");
        else
            outgoing.send(conversation, "Fazenda atual: " + conversation.getFarm().getName() + ".");
    }

    private String options(MessagingConversation conversation) {
        return "Selecione a fazenda que deseja utilizar:\n\n"
                + numbered(
                        farms.accessibleFarms(
                                conversation
                                        .getMessagingAccount()
                                        .getUserContact()
                                        .getUser()
                                        .getId()))
                + "\n\nResponda com o número da opção.";
    }

    private String numbered(List<Farm> farms) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < farms.size(); index++)
            result.append(index + 1).append(". ").append(farms.get(index).getName()).append('\n');
        return result.toString().trim();
    }
}
