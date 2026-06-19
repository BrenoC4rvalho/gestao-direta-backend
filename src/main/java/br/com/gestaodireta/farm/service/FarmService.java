package br.com.gestaodireta.farm.service;

import br.com.gestaodireta.farm.dto.FarmRequest;
import br.com.gestaodireta.farm.dto.FarmResponse;
import br.com.gestaodireta.farm.dto.FarmStatusUpdateRequest;
import br.com.gestaodireta.farm.dto.FarmUpdateRequest;
import br.com.gestaodireta.farm.entity.Farm;
import br.com.gestaodireta.farm.enumeration.FarmStatus;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.mapper.FarmMapper;
import br.com.gestaodireta.farm.repository.FarmRepository;
import br.com.gestaodireta.farm.repository.FarmUserRepository;
import br.com.gestaodireta.shared.exception.ForbiddenException;
import br.com.gestaodireta.shared.exception.ResourceNotFoundException;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import br.com.gestaodireta.shared.security.SecurityUtils;
import br.com.gestaodireta.user.enumeration.UserStatus;
import br.com.gestaodireta.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FarmService {

    private final FarmRepository farmRepository;

    private final FarmUserRepository farmUserRepository;

    private final UserRepository userRepository;

    private final FarmMapper farmMapper;

    public FarmService(
            FarmRepository farmRepository,
            FarmUserRepository farmUserRepository,
            UserRepository userRepository,
            FarmMapper farmMapper) {
        this.farmRepository = farmRepository;
        this.farmUserRepository = farmUserRepository;
        this.userRepository = userRepository;
        this.farmMapper = farmMapper;
    }

    @Transactional
    public FarmResponse create(FarmRequest request) {
        Farm farm = new Farm();
        farm.setName(request.name());
        farm.setDocument(request.document());
        farm.setCity(request.city());
        farm.setState(request.state());
        farm.setTotalArea(request.totalArea());
        farm.setProductionType(request.productionType());
        farm.setStatus(FarmStatus.ACTIVE);

        return farmMapper.toResponse(farmRepository.save(farm));
    }

    @Transactional(readOnly = true)
    public PageResponse<FarmResponse> findAll(PaginationParams paginationParams) {
        Page<Farm> farms;

        if (SecurityUtils.isAdmin()) {
            farms = farmRepository.findAll(paginationParams.toPageable());
        } else {
            ensureCurrentUserIsActive();
            farms =
                    farmUserRepository.findActiveFarmsByUserId(
                            SecurityUtils.getAuthenticatedUserId(),
                            FarmStatus.ACTIVE,
                            FarmUserRole.INACTIVE,
                            paginationParams.toPageable());
        }

        return PageResponse.from(farms.map(farmMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public FarmResponse findById(Long id) {
        return farmMapper.toResponse(findEntityById(id));
    }

    @Transactional
    public FarmResponse update(Long id, FarmUpdateRequest request) {
        Farm farm = findEntityById(id);
        farm.setName(request.name());
        farm.setDocument(request.document());
        farm.setCity(request.city());
        farm.setState(request.state());
        farm.setTotalArea(request.totalArea());
        farm.setProductionType(request.productionType());

        return farmMapper.toResponse(farmRepository.save(farm));
    }

    @Transactional
    public FarmResponse updateStatus(Long id, FarmStatusUpdateRequest request) {
        Farm farm = findEntityById(id);
        farm.setStatus(request.status());

        return farmMapper.toResponse(farmRepository.save(farm));
    }

    @Transactional
    public void inactivate(Long id) {
        Farm farm = findEntityById(id);
        farm.setStatus(FarmStatus.INACTIVE);
        farmRepository.save(farm);
    }

    public Farm findEntityById(Long id) {
        return farmRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Farm not found"));
    }

    private void ensureCurrentUserIsActive() {
        boolean active =
                userRepository
                        .findById(SecurityUtils.getAuthenticatedUserId())
                        .map(user -> UserStatus.ACTIVE.equals(user.getStatus()))
                        .orElse(false);

        if (!active) {
            throw new ForbiddenException("Access denied");
        }
    }
}
