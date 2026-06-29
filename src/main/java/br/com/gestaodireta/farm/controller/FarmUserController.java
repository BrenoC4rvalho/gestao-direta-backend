package br.com.gestaodireta.farm.controller;

import br.com.gestaodireta.farm.dto.FarmUserFilterRequest;
import br.com.gestaodireta.farm.dto.FarmUserRequest;
import br.com.gestaodireta.farm.dto.FarmUserResponse;
import br.com.gestaodireta.farm.dto.FarmUserRoleUpdateRequest;
import br.com.gestaodireta.farm.enumeration.FarmUserRole;
import br.com.gestaodireta.farm.service.FarmUserService;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/farms/{farmId}/users")
public class FarmUserController {

    private final FarmUserService farmUserService;

    public FarmUserController(FarmUserService farmUserService) {
        this.farmUserService = farmUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@farmAccess.canCreateFarmUser(#farmId, #request)")
    public FarmUserResponse create(
            @PathVariable Long farmId, @Valid @RequestBody FarmUserRequest request) {
        return farmUserService.create(farmId, request);
    }

    @GetMapping
    @PreAuthorize("@farmAccess.canManageFarmUsers(#farmId)")
    public PageResponse<FarmUserResponse> findByFarmId(
            @PathVariable Long farmId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) FarmUserRole role,
            @RequestParam(required = false) List<FarmUserRole> roles,
            @Valid @ModelAttribute PaginationParams paginationParams) {
        return farmUserService.findByFarmId(
                farmId, new FarmUserFilterRequest(search, role, roles), paginationParams);
    }

    @PatchMapping("/{userId}/role")
    @PreAuthorize("@farmAccess.canChangeFarmUserRole(#farmId, #userId, #request)")
    public FarmUserResponse updateRole(
            @PathVariable Long farmId,
            @PathVariable Long userId,
            @Valid @RequestBody FarmUserRoleUpdateRequest request) {
        return farmUserService.updateRole(farmId, userId, request);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@farmAccess.canRemoveFarmUser(#farmId, #userId)")
    public void inactivate(@PathVariable Long farmId, @PathVariable Long userId) {
        farmUserService.inactivate(farmId, userId);
    }
}
