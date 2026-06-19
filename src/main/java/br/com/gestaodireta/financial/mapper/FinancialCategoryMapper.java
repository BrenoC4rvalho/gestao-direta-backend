package br.com.gestaodireta.financial.mapper;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.financial.dto.FinancialCategoryResponse;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import org.springframework.stereotype.Component;

@Component
public class FinancialCategoryMapper {

    public FinancialCategoryResponse toResponse(FinancialCategory category) {
        Farm farm = category.getFarm();

        return new FinancialCategoryResponse(
                category.getId(),
                category.getName(),
                category.getType(),
                category.getColor(),
                category.getIcon(),
                farm == null ? null : farm.getId(),
                farm == null ? null : farm.getName(),
                category.isDefaultCategory(),
                category.getStatus(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
