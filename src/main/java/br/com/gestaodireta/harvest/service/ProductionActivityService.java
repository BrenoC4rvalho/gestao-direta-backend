package br.com.gestaodireta.harvest.service;

import br.com.gestaodireta.harvest.dto.ProductionActivityRequest;
import br.com.gestaodireta.harvest.dto.ProductionActivityResponse;
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

    private final ProductionActivityMapper productionActivityMapper;

    public ProductionActivityService(
            ProductionActivityRepository productionActivityRepository,
            ProductionActivityMapper productionActivityMapper) {
        this.productionActivityRepository = productionActivityRepository;
        this.productionActivityMapper = productionActivityMapper;
    }

    @Transactional
    public ProductionActivityResponse create(ProductionActivityRequest request) {
        ProductionActivity productionActivity = new ProductionActivity();
        applyRequest(productionActivity, request, null);
        productionActivity.setStatus(ProductionActivityStatus.ACTIVE);

        return productionActivityMapper.toResponse(
                productionActivityRepository.save(productionActivity));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductionActivityResponse> findAll(
            ProductionActivityStatus status, PaginationParams paginationParams) {
        Page<ProductionActivity> activities =
                status == null
                        ? productionActivityRepository.findAll(paginationParams.toPageable())
                        : productionActivityRepository.findByStatus(
                                status, paginationParams.toPageable());

        return PageResponse.from(activities.map(productionActivityMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public List<ProductionActivityResponse> findActive() {
        return productionActivityRepository
                .findByStatus(ProductionActivityStatus.ACTIVE, PaginationParamsForOptions.PAGEABLE)
                .stream()
                .map(productionActivityMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductionActivityResponse findById(Long id) {
        return productionActivityMapper.toResponse(findEntityById(id));
    }

    @Transactional
    public ProductionActivityResponse update(Long id, ProductionActivityRequest request) {
        ProductionActivity productionActivity = findEntityById(id);
        applyRequest(productionActivity, request, id);

        return productionActivityMapper.toResponse(
                productionActivityRepository.save(productionActivity));
    }

    @Transactional
    public ProductionActivityResponse activate(Long id) {
        ProductionActivity productionActivity = findEntityById(id);
        productionActivity.setStatus(ProductionActivityStatus.ACTIVE);

        return productionActivityMapper.toResponse(
                productionActivityRepository.save(productionActivity));
    }

    @Transactional
    public void inactivate(Long id) {
        ProductionActivity productionActivity = findEntityById(id);
        productionActivity.setStatus(ProductionActivityStatus.INACTIVE);
        productionActivityRepository.save(productionActivity);
    }

    public ProductionActivity findEntityById(Long id) {
        return productionActivityRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Production activity not found"));
    }

    private void applyRequest(
            ProductionActivity productionActivity,
            ProductionActivityRequest request,
            Long ignoredActivityId) {
        String name = sanitizeName(request.name());
        validateUniqueName(name, ignoredActivityId);

        productionActivity.setName(name);
        productionActivity.setDescription(request.description());
    }

    private String sanitizeName(String name) {
        String sanitizedName = name == null ? "" : name.trim();

        if (sanitizedName.isEmpty()) {
            throw new BusinessException("Production activity name cannot be blank.");
        }

        return sanitizedName;
    }

    private void validateUniqueName(String name, Long ignoredActivityId) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        boolean duplicateExists =
                ignoredActivityId == null
                        ? productionActivityRepository.existsByNormalizedName(normalizedName)
                        : productionActivityRepository.existsByNormalizedNameAndIdNot(
                                normalizedName, ignoredActivityId);

        if (duplicateExists) {
            throw new BusinessException("A production activity with this name already exists.");
        }
    }
}
