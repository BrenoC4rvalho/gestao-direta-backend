package br.com.gestaodireta.messaging.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.gestaodireta.messaging.domain.MessagingAccount;
import br.com.gestaodireta.messaging.domain.MessagingConversation;
import br.com.gestaodireta.messaging.domain.MessagingMessage;
import br.com.gestaodireta.messaging.enumeration.*;
import br.com.gestaodireta.messaging.repository.MessagingAccountRepository;
import br.com.gestaodireta.messaging.repository.MessagingConversationRepository;
import br.com.gestaodireta.messaging.repository.MessagingMessageRepository;
import br.com.gestaodireta.support.PostgresIntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MessagingControllerTest extends PostgresIntegrationTest {
    private static final String CONTEXT_PATH = "/api";
    @Autowired private MockMvc mockMvc;
    @Autowired private MessagingMessageRepository messages;
    @Autowired private MessagingConversationRepository conversations;
    @Autowired private MessagingAccountRepository accounts;

    @BeforeEach
    void setUp() {
        messages.deleteAll();
        conversations.deleteAll();
        accounts.deleteAll();
    }

    @Test
    void shouldFilterMessagesByConversationDirectionAndStatusForAdmin() throws Exception {
        MessagingConversation first = conversation();
        MessagingConversation second = conversation();
        message(first, MessagingDirection.INBOUND, MessagingMessageStatus.PROCESSED, "inbound");
        message(first, MessagingDirection.OUTBOUND, MessagingMessageStatus.SENT, "outbound");
        message(second, MessagingDirection.INBOUND, MessagingMessageStatus.PROCESSED, "other");

        mockMvc.perform(
                        get("/api/messaging/messages")
                                .contextPath(CONTEXT_PATH)
                                .param("messagingConversationId", String.valueOf(first.getId()))
                                .param("messageDirection", "INBOUND")
                                .param("status", "PROCESSED")
                                .with(user("1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].content").value("inbound"))
                .andExpect(jsonPath("$.content[0].messagingConversationId").value(first.getId()))
                .andExpect(jsonPath("$.content[0].rawPayload").doesNotExist());
    }

    @Test
    void shouldProtectAdministrativeMessagesAndReturnEmptyPageForUnknownConversation()
            throws Exception {
        mockMvc.perform(get("/api/messaging/messages").contextPath(CONTEXT_PATH))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        get("/api/messaging/messages")
                                .contextPath(CONTEXT_PATH)
                                .with(user("2").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        get("/api/messaging/messages")
                                .contextPath(CONTEXT_PATH)
                                .param("messagingConversationId", "999999")
                                .with(user("1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    private MessagingConversation conversation() {
        MessagingAccount account = new MessagingAccount();
        account.setChannel(MessagingChannel.TELEGRAM);
        account.setExternalUserId("user-" + System.nanoTime());
        account.setExternalChatId("chat-" + System.nanoTime());
        account.setStatus(MessagingAccountStatus.PENDING);
        account = accounts.save(account);
        MessagingConversation conversation = new MessagingConversation();
        conversation.setMessagingAccount(account);
        conversation.setStatus(MessagingConversationStatus.ACTIVE);
        conversation.setCurrentStep(MessagingConversationStep.NONE);
        conversation.setLastInteractionAt(LocalDateTime.now());
        conversation.setExpiresAt(LocalDateTime.now().plusHours(1));
        return conversations.save(conversation);
    }

    private void message(
            MessagingConversation conversation,
            MessagingDirection direction,
            MessagingMessageStatus status,
            String content) {
        MessagingMessage message = new MessagingMessage();
        message.setMessagingConversation(conversation);
        message.setChannel(MessagingChannel.TELEGRAM);
        message.setDirection(direction);
        message.setMessageType(MessagingMessageType.TEXT);
        message.setContent(content);
        message.setStatus(status);
        message.setRawPayload("secret");
        messages.save(message);
    }
}
