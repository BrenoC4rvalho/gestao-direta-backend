package br.com.gestaodireta.farm.controller;

import br.com.gestaodireta.farm.dto.FarmAccessResponse;
import br.com.gestaodireta.farm.dto.FarmRequest;
import br.com.gestaodireta.farm.dto.FarmResponse;
import br.com.gestaodireta.farm.dto.FarmStatusUpdateRequest;
import br.com.gestaodireta.farm.dto.FarmUpdateRequest;
import br.com.gestaodireta.farm.service.FarmAccessService;
import br.com.gestaodireta.farm.service.FarmService;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/farms")
public class FarmController {

    private final FarmService farmService;

    private final FarmAccessService farmAccessService;

    public FarmController(FarmService farmService, FarmAccessService farmAccessService) {
        this.farmService = farmService;
        this.farmAccessService = farmAccessService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@farmAccess.canCreateFarm()")
    public FarmResponse create(@Valid @RequestBody FarmRequest request) {
        return farmService.create(request);
    }

    @GetMapping
    public PageResponse<FarmResponse> findAll(
            @Valid @ModelAttribute PaginationParams paginationParams) {
        return farmService.findAll(paginationParams);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@farmAccess.canViewFarm(#id)")
    public FarmResponse findById(@PathVariable Long id) {
        return farmService.findById(id);
    }

    @GetMapping("/{farmId}/access")
    @PreAuthorize("isAuthenticated()")
    public FarmAccessResponse getAccess(@PathVariable Long farmId) {
        return farmAccessService.getAccess(farmId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@farmAccess.canManageFarm(#id)")
    public FarmResponse update(
            @PathVariable Long id, @Valid @RequestBody FarmUpdateRequest request) {
        return farmService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@farmAccess.canChangeFarmStatus()")
    public FarmResponse updateStatus(
            @PathVariable Long id, @Valid @RequestBody FarmStatusUpdateRequest request) {
        return farmService.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@farmAccess.canChangeFarmStatus()")
    public void inactivate(@PathVariable Long id) {
        farmService.inactivate(id);
    }
}
