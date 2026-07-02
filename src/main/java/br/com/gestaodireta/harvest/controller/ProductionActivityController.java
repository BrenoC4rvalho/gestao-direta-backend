package br.com.gestaodireta.harvest.controller;

import br.com.gestaodireta.harvest.dto.ProductionActivityRequest;
import br.com.gestaodireta.harvest.dto.ProductionActivityResponse;
import br.com.gestaodireta.harvest.enumeration.ProductionActivityStatus;
import br.com.gestaodireta.harvest.service.ProductionActivityService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/harvest/production-activities")
public class ProductionActivityController {

    private final ProductionActivityService productionActivityService;

    public ProductionActivityController(ProductionActivityService productionActivityService) {
        this.productionActivityService = productionActivityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@harvestAccess.canManageProductionActivities()")
    public ProductionActivityResponse create(
            @Valid @RequestBody ProductionActivityRequest request) {
        return productionActivityService.create(request);
    }

    @GetMapping
    @PreAuthorize("@harvestAccess.canManageProductionActivities()")
    public PageResponse<ProductionActivityResponse> findAll(
            @RequestParam(required = false) ProductionActivityStatus status,
            @Valid @ModelAttribute PaginationParams paginationParams) {
        return productionActivityService.findAll(status, paginationParams);
    }

    @GetMapping("/active")
    @PreAuthorize("@harvestAccess.canViewActiveProductionActivities()")
    public List<ProductionActivityResponse> findActive() {
        return productionActivityService.findActive();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@harvestAccess.canManageProductionActivities()")
    public ProductionActivityResponse findById(@PathVariable Long id) {
        return productionActivityService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@harvestAccess.canManageProductionActivities()")
    public ProductionActivityResponse update(
            @PathVariable Long id, @Valid @RequestBody ProductionActivityRequest request) {
        return productionActivityService.update(id, request);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("@harvestAccess.canManageProductionActivities()")
    public ProductionActivityResponse activate(@PathVariable Long id) {
        return productionActivityService.activate(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@harvestAccess.canManageProductionActivities()")
    public void inactivate(@PathVariable Long id) {
        productionActivityService.inactivate(id);
    }
}
