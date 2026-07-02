package br.com.gestaodireta.harvest.service;

import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.harvest.dto.HarvestSeasonRequest;
import br.com.gestaodireta.harvest.dto.HarvestSeasonResponse;
import br.com.gestaodireta.harvest.dto.HarvestSeasonStatusUpdateRequest;
import br.com.gestaodireta.harvest.dto.HarvestSeasonUpdateRequest;
import br.com.gestaodireta.harvest.entity.HarvestSeason;
import br.com.gestaodireta.harvest.entity.ProductionActivity;
import br.com.gestaodireta.harvest.enumeration.HarvestSeasonStatus;
import br.com.gestaodireta.harvest.enumeration.ProductionActivityStatus;
import br.com.gestaodireta.harvest.mapper.HarvestSeasonMapper;
import br.com.gestaodireta.harvest.repository.HarvestSeasonRepository;
import br.com.gestaodireta.shared.exception.BusinessException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HarvestSeasonService {

    private final HarvestSeasonRepository harvestSeasonRepository;

    private final FarmService farmService;

    private final ProductionActivityService productionActivityService;

    private final HarvestSeasonMapper harvestSeasonMapper;

    public HarvestSeasonService(
            HarvestSeasonRepository harvestSeasonRepository,
            FarmService farmService,
            ProductionActivityService productionActivityService,
            HarvestSeasonMapper harvestSeasonMapper) {
        this.harvestSeasonRepository = harvestSeasonRepository;
        this.farmService = farmService;
        this.productionActivityService = productionActivityService;
        this.harvestSeasonMapper = harvestSeasonMapper;
    }

    @Transactional
    public HarvestSeasonResponse create(HarvestSeasonRequest request) {
        Farm farm = farmService.findEntityById(request.farmId());
        ensureFarmIsActive(farm);

        HarvestSeason harvestSeason = new HarvestSeason();
        harvestSeason.setFarm(farm);
        applyRequest(
                harvestSeason,
                request.productionActivityId(),
                request.name(),
                request.description(),
                request.startDate(),
                request.endDate(),
                request.expectedRevenue(),
                request.expectedCost(),
                request.areaHectares(),
                null);
        harvestSeason.setStatus(HarvestSeasonStatus.PLANNED);

        return harvestSeasonMapper.toResponse(harvestSeasonRepository.save(harvestSeason));
    }

    @Transactional(readOnly = true)
    public PageResponse<HarvestSeasonResponse> findAll(
            Long farmId, boolean includeInactive, PaginationParams paginationParams) {
        farmService.findEntityById(farmId);
        Page<HarvestSeason> seasons =
                harvestSeasonRepository.findByFarmId(
                        farmId, includeInactive, paginationParams.toPageable());

        return PageResponse.from(seasons.map(harvestSeasonMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public HarvestSeasonResponse findById(Long id) {
        return harvestSeasonMapper.toResponse(findEntityById(id));
    }

    @Transactional
    public HarvestSeasonResponse update(Long id, HarvestSeasonUpdateRequest request) {
        HarvestSeason harvestSeason = findEntityById(id);

        if (HarvestSeasonStatus.INACTIVE.equals(harvestSeason.getStatus())) {
            throw new BusinessException("Inactive harvest season cannot be edited.");
        }

        applyRequest(
                harvestSeason,
                request.productionActivityId(),
                request.name(),
                request.description(),
                request.startDate(),
                request.endDate(),
                request.expectedRevenue(),
                request.expectedCost(),
                request.areaHectares(),
                id);

        return harvestSeasonMapper.toResponse(harvestSeasonRepository.save(harvestSeason));
    }

    @Transactional
    public HarvestSeasonResponse updateStatus(Long id, HarvestSeasonStatusUpdateRequest request) {
        HarvestSeason harvestSeason = findEntityById(id);

        if (!HarvestSeasonStatus.INACTIVE.equals(request.status())) {
            ensureFarmIsActive(harvestSeason.getFarm());
        }

        harvestSeason.setStatus(request.status());

        return harvestSeasonMapper.toResponse(harvestSeasonRepository.save(harvestSeason));
    }

    @Transactional
    public HarvestSeasonResponse activate(Long id) {
        HarvestSeason harvestSeason = findEntityById(id);
        ensureFarmIsActive(harvestSeason.getFarm());
        harvestSeason.setStatus(HarvestSeasonStatus.PLANNED);

        return harvestSeasonMapper.toResponse(harvestSeasonRepository.save(harvestSeason));
    }

    @Transactional
    public void inactivate(Long id) {
        HarvestSeason harvestSeason = findEntityById(id);
        harvestSeason.setStatus(HarvestSeasonStatus.INACTIVE);
        harvestSeasonRepository.save(harvestSeason);
    }

    public HarvestSeason findEntityById(Long id) {
        return harvestSeasonRepository
                .findByIdWithRelations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Harvest season not found"));
    }

    private void applyRequest(
            HarvestSeason harvestSeason,
            Long productionActivityId,
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal expectedRevenue,
            BigDecimal expectedCost,
            BigDecimal areaHectares,
            Long ignoredSeasonId) {
        String sanitizedName = sanitizeName(name);
        validateDates(startDate, endDate);
        validateUniqueName(harvestSeason.getFarm().getId(), sanitizedName, ignoredSeasonId);

        ProductionActivity productionActivity =
                productionActivityService.findEntityById(productionActivityId);
        ensureProductionActivityIsActive(productionActivity);

        harvestSeason.setProductionActivity(productionActivity);
        harvestSeason.setName(sanitizedName);
        harvestSeason.setDescription(description);
        harvestSeason.setStartDate(startDate);
        harvestSeason.setEndDate(endDate);
        harvestSeason.setExpectedRevenue(expectedRevenue);
        harvestSeason.setExpectedCost(expectedCost);
        harvestSeason.setAreaHectares(areaHectares);
    }

    private String sanitizeName(String name) {
        String sanitizedName = name == null ? "" : name.trim();

        if (sanitizedName.isEmpty()) {
            throw new BusinessException("Harvest season name cannot be blank.");
        }

        return sanitizedName;
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException("End date cannot be before start date.");
        }
    }

    private void validateUniqueName(Long farmId, String name, Long ignoredSeasonId) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        boolean duplicateExists =
                ignoredSeasonId == null
                        ? harvestSeasonRepository.existsActiveByFarmIdAndNormalizedName(
                                farmId, normalizedName, HarvestSeasonStatus.INACTIVE)
                        : harvestSeasonRepository.existsActiveByFarmIdAndNormalizedNameAndIdNot(
                                farmId,
                                ignoredSeasonId,
                                normalizedName,
                                HarvestSeasonStatus.INACTIVE);

        if (duplicateExists) {
            throw new BusinessException(
                    "A harvest season with this name already exists for this farm.");
        }
    }

    private void ensureFarmIsActive(Farm farm) {
        if (!FarmStatus.ACTIVE.equals(farm.getStatus())) {
            throw new BusinessException("Inactive farm cannot receive harvest seasons.");
        }
    }

    private void ensureProductionActivityIsActive(ProductionActivity productionActivity) {
        if (!ProductionActivityStatus.ACTIVE.equals(productionActivity.getStatus())) {
            throw new BusinessException(
                    "Inactive production activity cannot be used in a harvest season.");
        }
    }
}
