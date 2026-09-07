package br.com.gestaodireta.harvest.service;

import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.service.FinancialCategoryService;
import br.com.gestaodireta.harvest.dto.HarvestSeasonBudgetCategoryResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonBudgetItemRequest;
import br.com.gestaodireta.harvest.dto.HarvestSeasonBudgetItemResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonBudgetResponse;
import br.com.gestaodireta.harvest.entity.HarvestSeason;
import br.com.gestaodireta.harvest.entity.HarvestSeasonBudgetItem;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
import br.com.gestaodireta.harvest.repository.HarvestSeasonBudgetItemRepository;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HarvestSeasonBudgetItemService {

    private static final String LEGACY_CATEGORY_NAME = "Sem categoria";

    private final HarvestSeasonBudgetItemRepository budgetItemRepository;
    private final HarvestSeasonService harvestSeasonService;
    private final FinancialCategoryService financialCategoryService;

    public HarvestSeasonBudgetItemService(
            HarvestSeasonBudgetItemRepository budgetItemRepository,
            HarvestSeasonService harvestSeasonService,
            FinancialCategoryService financialCategoryService) {
        this.budgetItemRepository = budgetItemRepository;
        this.harvestSeasonService = harvestSeasonService;
        this.financialCategoryService = financialCategoryService;
    }

    @Transactional(readOnly = true)
    public HarvestSeasonBudgetResponse findAll(Long harvestSeasonId) {
        HarvestSeason harvestSeason = harvestSeasonService.findEntityById(harvestSeasonId);
        return toBudgetResponse(
                harvestSeason.getId(),
                budgetItemRepository.findAllByHarvestSeasonId(harvestSeason.getId()));
    }

    @Transactional
    public HarvestSeasonBudgetItemResponse create(
            Long harvestSeasonId, HarvestSeasonBudgetItemRequest request) {
        HarvestSeason harvestSeason = harvestSeasonService.findEntityById(harvestSeasonId);
        ensurePlanningCanBeChanged(harvestSeason);

        HarvestSeasonBudgetItem item = new HarvestSeasonBudgetItem();
        item.setHarvestSeason(harvestSeason);
        applyRequest(item, harvestSeason, request);
        return toResponse(budgetItemRepository.save(item));
    }

    @Transactional
    public HarvestSeasonBudgetItemResponse update(
            Long harvestSeasonId, Long itemId, HarvestSeasonBudgetItemRequest request) {
        HarvestSeasonBudgetItem item = findItem(itemId, harvestSeasonId);
        ensurePlanningCanBeChanged(item.getHarvestSeason());
        applyRequest(item, item.getHarvestSeason(), request);
        return toResponse(budgetItemRepository.save(item));
    }

    @Transactional
    public void delete(Long harvestSeasonId, Long itemId) {
        HarvestSeasonBudgetItem item = findItem(itemId, harvestSeasonId);
        ensurePlanningCanBeChanged(item.getHarvestSeason());
        budgetItemRepository.delete(item);
    }

    private void applyRequest(
            HarvestSeasonBudgetItem item,
            HarvestSeason harvestSeason,
            HarvestSeasonBudgetItemRequest request) {
        FinancialCategory category = financialCategoryService.findEntityById(request.categoryId());
        validateCategory(category, harvestSeason, request.type());

        item.setCategory(category);
        item.setType(request.type());
        item.setDescription(request.description().trim());
        item.setPlannedAmount(request.plannedAmount());
    }

    private void validateCategory(
            FinancialCategory category, HarvestSeason harvestSeason, TransactionType type) {
        if (!FinancialCategoryStatus.ACTIVE.equals(category.getStatus())) {
            throw new BusinessException("Inactive financial category cannot be used in planning.");
        }

        if (!category.getFarm().getId().equals(harvestSeason.getFarm().getId())) {
            throw new BusinessException(
                    "Financial category must belong to the harvest season farm.");
        }

        if (!category.getType().equals(type)) {
            throw new BusinessException(
                    "Financial category type must match the planning item type.");
        }
    }

    private void ensurePlanningCanBeChanged(HarvestSeason harvestSeason) {
        if (HarvestSeasonStatus.FINISHED.equals(harvestSeason.getStatus())) {
            throw new BusinessException("Finished harvest season planning is read-only.");
        }

        if (HarvestSeasonStatus.INACTIVE.equals(harvestSeason.getStatus())) {
            throw new BusinessException("Inactive harvest season planning is read-only.");
        }
    }

    private HarvestSeasonBudgetItem findItem(Long itemId, Long harvestSeasonId) {
        return budgetItemRepository
                .findByIdAndHarvestSeasonId(itemId, harvestSeasonId)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Harvest season budget item not found"));
    }

    private HarvestSeasonBudgetResponse toBudgetResponse(
            Long harvestSeasonId, List<HarvestSeasonBudgetItem> items) {
        BigDecimal plannedRevenue = totalForType(items, TransactionType.INCOME);
        BigDecimal plannedExpense = totalForType(items, TransactionType.EXPENSE);
        BigDecimal plannedResult = plannedRevenue.subtract(plannedExpense);
        BigDecimal plannedMargin = calculateMargin(plannedRevenue, plannedResult);

        return new HarvestSeasonBudgetResponse(
                harvestSeasonId,
                plannedRevenue,
                plannedExpense,
                plannedResult,
                plannedMargin,
                groupsForType(items, TransactionType.EXPENSE),
                groupsForType(items, TransactionType.INCOME));
    }

    private BigDecimal calculateMargin(BigDecimal plannedRevenue, BigDecimal plannedResult) {
        if (plannedRevenue.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        return plannedResult
                .multiply(BigDecimal.valueOf(100))
                .divide(plannedRevenue, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal totalForType(List<HarvestSeasonBudgetItem> items, TransactionType type) {
        return items.stream()
                .filter(item -> type.equals(item.getType()))
                .map(HarvestSeasonBudgetItem::getPlannedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<HarvestSeasonBudgetCategoryResponse> groupsForType(
            List<HarvestSeasonBudgetItem> items, TransactionType type) {
        Map<Long, List<HarvestSeasonBudgetItem>> groups =
                items.stream()
                        .filter(item -> type.equals(item.getType()))
                        .collect(Collectors.groupingBy(this::categoryKey));

        return groups.values().stream()
                .map(this::toCategoryResponse)
                .sorted(Comparator.comparing(HarvestSeasonBudgetCategoryResponse::categoryName))
                .toList();
    }

    private Long categoryKey(HarvestSeasonBudgetItem item) {
        return item.getCategory() == null ? -item.getId() : item.getCategory().getId();
    }

    private HarvestSeasonBudgetCategoryResponse toCategoryResponse(
            List<HarvestSeasonBudgetItem> items) {
        HarvestSeasonBudgetItem first = items.getFirst();
        FinancialCategory category = first.getCategory();
        BigDecimal plannedAmount =
                items.stream()
                        .map(HarvestSeasonBudgetItem::getPlannedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new HarvestSeasonBudgetCategoryResponse(
                category == null ? null : category.getId(),
                category == null ? LEGACY_CATEGORY_NAME : category.getName(),
                first.getType(),
                items.size(),
                plannedAmount,
                items.stream().map(this::toResponse).toList());
    }

    private HarvestSeasonBudgetItemResponse toResponse(HarvestSeasonBudgetItem item) {
        FinancialCategory category = item.getCategory();
        return new HarvestSeasonBudgetItemResponse(
                item.getId(),
                item.getHarvestSeason().getId(),
                category == null ? null : category.getId(),
                category == null ? LEGACY_CATEGORY_NAME : category.getName(),
                item.getType(),
                item.getDescription(),
                item.getPlannedAmount());
    }
}
