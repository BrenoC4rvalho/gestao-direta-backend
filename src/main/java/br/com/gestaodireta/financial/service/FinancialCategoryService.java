package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.financial.dto.FinancialCategoryCreateRequest;
import br.com.gestaodireta.financial.dto.FinancialCategoryResponse;
import br.com.gestaodireta.financial.dto.FinancialCategoryUpdateRequest;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.enumeration.TransactionType;
import br.com.gestaodireta.financial.mapper.FinancialCategoryMapper;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialCategoryService {

    private static final String DUPLICATE_CATEGORY_MESSAGE =
            "A category with this name and type already exists for this farm.";

    private final FinancialCategoryRepository financialCategoryRepository;

    private final FarmService farmService;

    private final FinancialCategoryMapper financialCategoryMapper;

    public FinancialCategoryService(
            FinancialCategoryRepository financialCategoryRepository,
            FarmService farmService,
            FinancialCategoryMapper financialCategoryMapper) {
        this.financialCategoryRepository = financialCategoryRepository;
        this.farmService = farmService;
        this.financialCategoryMapper = financialCategoryMapper;
    }

    @Transactional
    public FinancialCategoryResponse create(FinancialCategoryCreateRequest request) {
        Farm farm = farmService.findEntityById(request.farmId());
        ensureFarmIsActive(farm);
        String name = sanitizeName(request.name());
        validateUniqueName(name, farm.getId(), request.type(), null);

        FinancialCategory category = new FinancialCategory();
        category.setFarm(farm);
        category.setName(name);
        category.setType(request.type());
        category.setColor(request.color());
        category.setIcon(request.icon());
        category.setStatus(FinancialCategoryStatus.ACTIVE);

        return financialCategoryMapper.toResponse(financialCategoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public PageResponse<FinancialCategoryResponse> findAll(
            Long farmId,
            boolean includeInactive,
            String search,
            FinancialCategoryStatus status,
            PaginationParams paginationParams) {
        farmService.findEntityById(farmId);
        Page<FinancialCategoryResponse> categories =
                financialCategoryRepository
                        .findByFarmIdAndFilters(
                                farmId,
                                includeInactive,
                                normalizeSearch(search),
                                status,
                                paginationParams.toPageable())
                        .map(financialCategoryMapper::toResponse);

        return PageResponse.from(categories);
    }

    @Transactional(readOnly = true)
    public PageResponse<FinancialCategoryResponse> findAll(
            Long farmId, boolean includeInactive, PaginationParams paginationParams) {
        return findAll(farmId, includeInactive, null, null, paginationParams);
    }

    @Transactional(readOnly = true)
    public List<FinancialCategoryResponse> findUsedInTransactions(Long farmId) {
        farmService.findEntityById(farmId);

        return financialCategoryRepository
                .findUsedInTransactionsByFarmId(farmId, FinancialRecordStatus.ACTIVE)
                .stream()
                .map(financialCategoryMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FinancialCategoryResponse findById(Long id) {
        return financialCategoryMapper.toResponse(findEntityById(id));
    }

    @Transactional
    public FinancialCategoryResponse update(Long id, FinancialCategoryUpdateRequest request) {
        FinancialCategory category = findEntityById(id);
        ensureFarmIsActive(category.getFarm());
        String name = sanitizeName(request.name());
        validateUniqueName(name, category.getFarm().getId(), request.type(), id);

        category.setName(name);
        category.setType(request.type());
        category.setColor(request.color());
        category.setIcon(request.icon());

        return financialCategoryMapper.toResponse(financialCategoryRepository.save(category));
    }

    @Transactional
    public FinancialCategoryResponse activate(Long id) {
        FinancialCategory category = findEntityById(id);
        ensureFarmIsActive(category.getFarm());
        category.setStatus(FinancialCategoryStatus.ACTIVE);

        return financialCategoryMapper.toResponse(financialCategoryRepository.save(category));
    }

    @Transactional
    public void inactivate(Long id) {
        FinancialCategory category = findEntityById(id);
        ensureFarmIsActive(category.getFarm());
        category.setStatus(FinancialCategoryStatus.INACTIVE);
        financialCategoryRepository.save(category);
    }

    public FinancialCategory findEntityById(Long id) {
        return financialCategoryRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Financial category not found"));
    }

    public void ensureCategoriesBelongToFarm(Collection<Long> categoryIds, Long farmId) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }

        long matchingCategories =
                financialCategoryRepository.countByFarmIdAndIdIn(farmId, categoryIds);

        if (matchingCategories != categoryIds.size()) {
            throw new BusinessException("Financial category does not belong to farm");
        }
    }

    private String sanitizeName(String name) {
        String sanitizedName = name == null ? "" : name.trim();

        if (sanitizedName.isEmpty()) {
            throw new BusinessException("Category name cannot be blank.");
        }

        return sanitizedName;
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        return "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private void validateUniqueName(
            String name, Long farmId, TransactionType type, Long ignoredCategoryId) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        boolean duplicateExists;

        if (ignoredCategoryId == null) {
            duplicateExists =
                    financialCategoryRepository.existsFarmByNormalizedNameAndType(
                            farmId, normalizedName, type);
        } else {
            duplicateExists =
                    financialCategoryRepository.existsFarmByNormalizedNameAndTypeAndIdNot(
                            farmId, normalizedName, type, ignoredCategoryId);
        }

        if (duplicateExists) {
            throw new BusinessException(DUPLICATE_CATEGORY_MESSAGE);
        }
    }

    private void ensureFarmIsActive(Farm farm) {
        if (!FarmStatus.ACTIVE.equals(farm.getStatus())) {
            throw new BusinessException("Inactive farm cannot receive financial categories");
        }
    }
}
