package br.com.gestaodireta.financial.dto;

import br.com.gestaodireta.financial.enumeration.FinancialAgendaStatusFilter;
import br.com.gestaodireta.financial.enumeration.FinancialAgendaTypeFilter;
import java.util.List;
import org.springframework.data.domain.Pageable;

public record FinancialAgendaFilter(
        Long farmId,
        FinancialAgendaStatusFilter status,
        FinancialAgendaTypeFilter type,
        Integer periodDays,
        List<Long> harvestSeasonIds,
        Pageable pageable) {}
