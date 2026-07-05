package br.com.gestaodireta.harvest.service;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.harvest.dto.ProductionActivityCreateRequest;
import br.com.gestaodireta.harvest.dto.ProductionActivityResponse;
import br.com.gestaodireta.harvest.dto.ProductionActivitySummaryResponse;
import br.com.gestaodireta.harvest.dto.ProductionActivityUpdateRequest;
import br.com.gestaodireta.harvest.entity.ProductionActivity;
import br.com.gestaodireta.harvest.enumeration.ProductionActivityStatus;
import br.com.gestaodireta.harvest.mapper.ProductionActivityMapper;
import br.com.gestaodireta.harvest.repository.ProductionActivityRepository;
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
public class ProductionActivityService {

    private final ProductionActivityRepository productionActivityRepository;

    private final FarmService farmService;

    private final ProductionActivityMapper productionActivityMapper;

    public ProductionActivityService(
            ProductionActivityRepository productionActivityRepository,
            FarmService farmService,
            ProductionActivityMapper productionActivityMapper) {
        this.productionActivityRepository = productionActivityRepository;
        this.farmService = farmService;
        this.productionActivityMapper = productionActivityMapper;
    }

    @Transactional
    public ProductionActivityResponse create(ProductionActivityCreateRequest request) {
        Farm farm = farmService.findEntityById(request.farmId());
        ensureFarmIsActive(farm);

        ProductionActivity productionActivity = new ProductionActivity();
        productionActivity.setFarm(farm);
        applyRequest(productionActivity, request.name(), request.description(), null);
        productionActivity.setStatus(ProductionActivityStatus.ACTIVE);

        return productionActivityMapper.toResponse(
                productionActivityRepository.save(productionActivity));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductionActivityResponse> findAll(
            Long farmId, ProductionActivityStatus status, PaginationParams paginationParams) {
        farmService.findEntityById(farmId);
        Page<ProductionActivity> activities =
                productionActivityRepository.findByFarmIdAndStatus(
                        farmId, status, paginationParams.toPageable());

        return PageResponse.from(activities.map(productionActivityMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public List<ProductionActivityResponse> findActive(Long farmId) {
        farmService.findEntityById(farmId);

        return productionActivityRepository
                .findByFarmIdAndStatus(
                        farmId,
                        ProductionActivityStatus.ACTIVE,
                        PaginationParamsForOptions.PAGEABLE)
                .stream()
                .map(productionActivityMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductionActivitySummaryResponse getSummary(Long farmId) {
        farmService.findEntityById(farmId);

        return new ProductionActivitySummaryResponse(
                farmId,
                productionActivityRepository.countByFarmId(farmId),
                productionActivityRepository.countByFarmIdAndStatus(
                        farmId, ProductionActivityStatus.ACTIVE),
                productionActivityRepository.countByFarmIdAndStatus(
                        farmId, ProductionActivityStatus.INACTIVE),
                productionActivityRepository.countDistinctInProgressByFarmId(farmId));
    }

    @Transactional(readOnly = true)
    public ProductionActivityResponse findById(Long id) {
        return productionActivityMapper.toResponse(findEntityById(id));
    }

    @Transactional
    public ProductionActivityResponse update(Long id, ProductionActivityUpdateRequest request) {
        ProductionActivity productionActivity = findEntityById(id);
        ensureFarmIsActive(productionActivity.getFarm());
        applyRequest(productionActivity, request.name(), request.description(), id);

        return productionActivityMapper.toResponse(
                productionActivityRepository.save(productionActivity));
    }

    @Transactional
    public ProductionActivityResponse activate(Long id) {
        ProductionActivity productionActivity = findEntityById(id);
        ensureFarmIsActive(productionActivity.getFarm());
        productionActivity.setStatus(ProductionActivityStatus.ACTIVE);

        return productionActivityMapper.toResponse(
                productionActivityRepository.save(productionActivity));
    }

    @Transactional
    public void inactivate(Long id) {
        ProductionActivity productionActivity = findEntityById(id);
        ensureFarmIsActive(productionActivity.getFarm());
        productionActivity.setStatus(ProductionActivityStatus.INACTIVE);
        productionActivityRepository.save(productionActivity);
    }

    public ProductionActivity findEntityById(Long id) {
        return productionActivityRepository
                .findByIdWithFarm(id)
                .orElseThrow(() -> new ResourceNotFoundException("Production activity not found"));
    }

    private void applyRequest(
            ProductionActivity productionActivity,
            String name,
            String description,
            Long ignoredActivityId) {
        String sanitizedName = sanitizeName(name);
        validateUniqueName(productionActivity.getFarm().getId(), sanitizedName, ignoredActivityId);

        productionActivity.setName(sanitizedName);
        productionActivity.setDescription(description);
    }

    private String sanitizeName(String name) {
        String sanitizedName = name == null ? "" : name.trim();

        if (sanitizedName.isEmpty()) {
            throw new BusinessException("Production activity name cannot be blank.");
        }

        return sanitizedName;
    }

    private void validateUniqueName(Long farmId, String name, Long ignoredActivityId) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        boolean duplicateExists =
                ignoredActivityId == null
                        ? productionActivityRepository.existsByFarmIdAndNormalizedName(
                                farmId, normalizedName)
                        : productionActivityRepository.existsByFarmIdAndNormalizedNameAndIdNot(
                                farmId, ignoredActivityId, normalizedName);

        if (duplicateExists) {
            throw new BusinessException(
                    "A production activity with this name already exists for this farm.");
        }
    }

    private void ensureFarmIsActive(Farm farm) {
        if (!FarmStatus.ACTIVE.equals(farm.getStatus())) {
            throw new BusinessException("Inactive farm cannot receive production activities.");
        }
    }
}
