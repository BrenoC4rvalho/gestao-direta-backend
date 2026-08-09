package br.com.gestaodireta.financial.mapper;

import br.com.gestaodireta.financial.dto.PendingFinancialTransactionResponse;
import br.com.gestaodireta.financial.entity.PendingFinancialTransaction;
import org.springframework.stereotype.Component;

@Component
public class PendingFinancialTransactionMapper {
    public PendingFinancialTransactionResponse toResponse(PendingFinancialTransaction pending) {
        return new PendingFinancialTransactionResponse(
                pending.getId(),
                pending.getFarm().getId(),
                pending.getFarm().getName(),
                pending.getType(),
                pending.getAmount(),
                pending.getTransactionDate(),
                pending.getDescription(),
                pending.getSuggestedCategory() == null
                        ? null
                        : pending.getSuggestedCategory().getId(),
                pending.getSuggestedCategory() == null
                        ? null
                        : pending.getSuggestedCategory().getName(),
                pending.getRawCategoryName(),
                pending.getHarvestSeason() == null ? null : pending.getHarvestSeason().getId(),
                pending.getHarvestSeason() == null ? null : pending.getHarvestSeason().getName(),
                pending.getPaymentMethod(),
                pending.getNotes(),
                pending.getStatus(),
                pending.getConfidence(),
                pending.getSourceChannel(),
                pending.getSourceMessage().getId(),
                pending.getSourceMessage().getContent(),
                pending.getSourceMessage().getReceivedAt(),
                pending.getRequestedByUser().getId(),
                pending.getRequestedByUser().getName(),
                pending.getReviewedByUser() == null ? null : pending.getReviewedByUser().getId(),
                pending.getReviewedByUser() == null ? null : pending.getReviewedByUser().getName(),
                pending.getReviewedAt(),
                pending.getRejectionReason(),
                pending.getApprovedFinancialTransaction() == null
                        ? null
                        : pending.getApprovedFinancialTransaction().getId(),
                pending.getCreatedAt(),
                pending.getUpdatedAt());
    }
}
