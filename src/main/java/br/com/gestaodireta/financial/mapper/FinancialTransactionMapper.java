package br.com.gestaodireta.financial.mapper;

import br.com.gestaodireta.financial.dto.FinancialTransactionResponse;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.entity.FinancialTransaction;
import br.com.gestaodireta.harvest.entity.HarvestSeason;
import br.com.gestaodireta.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class FinancialTransactionMapper {

    public FinancialTransactionResponse toResponse(FinancialTransaction transaction) {
        FinancialCategory category = transaction.getCategory();
        HarvestSeason harvestSeason = transaction.getHarvestSeason();
        User updatedByUser = transaction.getUpdatedByUser();

        return new FinancialTransactionResponse(
                transaction.getId(),
                transaction.getDescription(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getPaymentMethod(),
                transaction.getTransactionDate(),
                transaction.getDueDate(),
                transaction.getPaidAt(),
                transaction.getNotes(),
                transaction.getFarm().getId(),
                transaction.getFarm().getName(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                harvestSeason == null ? null : harvestSeason.getId(),
                harvestSeason == null ? null : harvestSeason.getName(),
                transaction.getCreatedByUser().getId(),
                transaction.getCreatedByUser().getName(),
                updatedByUser == null ? null : updatedByUser.getId(),
                updatedByUser == null ? null : updatedByUser.getName(),
                transaction.getRecordStatus(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt());
    }
}
