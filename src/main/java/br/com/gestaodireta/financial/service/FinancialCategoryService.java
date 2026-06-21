package br.com.gestaodireta.financial.service;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.financial.dto.FinancialCategoryRequest;
import br.com.gestaodireta.financial.dto.FinancialCategoryResponse;
import br.com.gestaodireta.financial.entity.FinancialCategory;
import br.com.gestaodireta.financial.enumeration.FinancialCategoryStatus;
import br.com.gestaodireta.financial.mapper.FinancialCategoryMapper;
import br.com.gestaodireta.financial.repository.FinancialCategoryRepository;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
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
        applyRequest(category, request);
        category.setStatus(FinancialCategoryStatus.ACTIVE);

        return financialCategoryMapper.toResponse(financialCategoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public PageResponse<FinancialCategoryResponse> findAll(
            Long farmId, PaginationParams paginationParams) {
        farmService.findEntityById(farmId);
        Page<FinancialCategoryResponse> categories =
                financialCategoryRepository
                        .findVisibleByFarmId(
                                farmId,
                                FinancialCategoryStatus.ACTIVE,
                                paginationParams.toPageable())
                        .map(financialCategoryMapper::toResponse);

        return PageResponse.from(categories);
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
        applyRequest(category, request);

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

    private void applyRequest(FinancialCategory category, FinancialCategoryRequest request) {
        category.setName(request.name());
        category.setType(request.type());
        category.setColor(request.color());
        category.setIcon(request.icon());
        category.setDefaultCategory(request.isDefault());
        category.setFarm(resolveFarm(request));
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
