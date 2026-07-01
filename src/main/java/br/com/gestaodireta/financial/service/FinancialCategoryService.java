package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.financial.dto.FinancialCategoryRequest;
import br.com.gestaodireta.financial.dto.FinancialCategoryResponse;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.enumeration.FinancialRecordStatus;
import br.com.gestaodireta.financial.mapper.FinancialCategoryMapper;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialCategoryService {

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
    public FinancialCategoryResponse create(FinancialCategoryRequest request) {
        FinancialCategory category = new FinancialCategory();
        applyRequest(category, request, null);
        category.setStatus(FinancialCategoryStatus.ACTIVE);

        return financialCategoryMapper.toResponse(financialCategoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public PageResponse<FinancialCategoryResponse> findAll(
            Long farmId, boolean includeInactive, PaginationParams paginationParams) {
        farmService.findEntityById(farmId);
        Page<FinancialCategoryResponse> categories =
                financialCategoryRepository
                        .findVisibleByFarmId(farmId, includeInactive, paginationParams.toPageable())
                        .map(financialCategoryMapper::toResponse);

        return PageResponse.from(categories);
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
    public PageResponse<FinancialCategoryResponse> findGlobal(PaginationParams paginationParams) {
        Page<FinancialCategoryResponse> categories =
                financialCategoryRepository
                        .findGlobal(paginationParams.toPageable())
                        .map(financialCategoryMapper::toResponse);

        return PageResponse.from(categories);
    }

    @Transactional(readOnly = true)
    public FinancialCategoryResponse findById(Long id) {
        return financialCategoryMapper.toResponse(findEntityById(id));
    }

    @Transactional
    public FinancialCategoryResponse update(Long id, FinancialCategoryRequest request) {
        FinancialCategory category = findEntityById(id);
        applyRequest(category, request, id);

        return financialCategoryMapper.toResponse(financialCategoryRepository.save(category));
    }

    @Transactional
    public FinancialCategoryResponse activate(Long id) {
        FinancialCategory category = findEntityById(id);
        category.setStatus(FinancialCategoryStatus.ACTIVE);

        return financialCategoryMapper.toResponse(financialCategoryRepository.save(category));
    }

    @Transactional
    public void inactivate(Long id) {
        FinancialCategory category = findEntityById(id);
        category.setStatus(FinancialCategoryStatus.INACTIVE);
        financialCategoryRepository.save(category);
    }

    public FinancialCategory findEntityById(Long id) {
        return financialCategoryRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Financial category not found"));
    }

    private void applyRequest(
            FinancialCategory category, FinancialCategoryRequest request, Long ignoredCategoryId) {
        String name = sanitizeName(request.name());
        Farm farm = resolveFarm(request);
        validateUniqueName(name, farm, request.isDefault(), ignoredCategoryId);

        category.setName(name);
        category.setType(request.type());
        category.setColor(request.color());
        category.setIcon(request.icon());
        category.setDefaultCategory(request.isDefault());
        category.setFarm(farm);
    }

    private String sanitizeName(String name) {
        String sanitizedName = name == null ? "" : name.trim();

        if (sanitizedName.isEmpty()) {
            throw new BusinessException("Category name cannot be blank.");
        }

        return sanitizedName;
    }

    private void validateUniqueName(
            String name, Farm farm, boolean defaultCategory, Long ignoredCategoryId) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        boolean duplicateExists =
                defaultCategory
                        ? existsGlobalDuplicate(normalizedName, ignoredCategoryId)
                        : existsFarmDuplicate(farm.getId(), normalizedName, ignoredCategoryId);

        if (duplicateExists) {
            throw new BusinessException("A category with this name already exists.");
        }
    }

    private boolean existsGlobalDuplicate(String normalizedName, Long ignoredCategoryId) {
        if (ignoredCategoryId == null) {
            return financialCategoryRepository.existsGlobalByNormalizedName(normalizedName);
        }

        return financialCategoryRepository.existsGlobalByNormalizedNameAndIdNot(
                normalizedName, ignoredCategoryId);
    }

    private boolean existsFarmDuplicate(
            Long farmId, String normalizedName, Long ignoredCategoryId) {
        if (ignoredCategoryId == null) {
            return financialCategoryRepository.existsFarmByNormalizedName(farmId, normalizedName);
        }

        return financialCategoryRepository.existsFarmByNormalizedNameAndIdNot(
                farmId, normalizedName, ignoredCategoryId);
    }

    private Farm resolveFarm(FinancialCategoryRequest request) {
        if (request.isDefault()) {
            if (request.farmId() != null) {
                throw new BusinessException("Default category cannot be linked to a farm");
            }

            return null;
        }

        if (request.farmId() == null) {
            throw new BusinessException("Farm category must be linked to a farm");
        }

        return farmService.findEntityById(request.farmId());
    }
}
