package br.com.gestaodireta.messaging.service;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.*;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.messaging.domain.MessagingConversation;
import br.com.gestaodireta.messaging.enumeration.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class MessagingFarmContextService {
    private final FarmUserRepository farmUsers;

    public MessagingFarmContextService(FarmUserRepository farmUsers) {
        this.farmUsers = farmUsers;
    }

    public List<Farm> accessibleFarms(Long userId) {
        return farmUsers
                .findActiveFarmsByUserId(
                        userId, FarmStatus.ACTIVE, FarmUserRole.INACTIVE, PageRequest.of(0, 100))
                .stream()
                .sorted(
                        Comparator.comparing(Farm::getName, String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(Farm::getId))
                .toList();
    }

    public void resolve(MessagingConversation conversation) {
        List<Farm> farms =
                accessibleFarms(
                        conversation.getMessagingAccount().getUserContact().getUser().getId());
        if (farms.isEmpty()) {
            conversation.setFarm(null);
            conversation.setStatus(MessagingConversationStatus.COMPLETED);
            conversation.setCurrentStep(MessagingConversationStep.NONE);
        } else if (farms.size() == 1) {
            conversation.setFarm(farms.getFirst());
            conversation.setStatus(MessagingConversationStatus.ACTIVE);
            conversation.setCurrentStep(MessagingConversationStep.NONE);
        } else {
            conversation.setFarm(null);
            conversation.setStatus(MessagingConversationStatus.WAITING_FARM_SELECTION);
            conversation.setCurrentStep(MessagingConversationStep.WAITING_FARM_SELECTION);
        }
    }

    public boolean select(MessagingConversation conversation, String option) {
        try {
            int index = Integer.parseInt(option.trim()) - 1;
            List<Farm> farms =
                    accessibleFarms(
                            conversation.getMessagingAccount().getUserContact().getUser().getId());
            if (index < 0 || index >= farms.size()) return false;
            conversation.setFarm(farms.get(index));
            conversation.setStatus(MessagingConversationStatus.ACTIVE);
            conversation.setCurrentStep(MessagingConversationStep.NONE);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
