package br.com.gestaodireta.user.controller;

import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import br.com.gestaodireta.user.dto.UserCreateRequest;
import br.com.gestaodireta.user.dto.UserResponse;
import br.com.gestaodireta.user.dto.UserStatusUpdateRequest;
import br.com.gestaodireta.user.dto.UserTypeUpdateRequest;
import br.com.gestaodireta.user.dto.UserUpdateRequest;
import br.com.gestaodireta.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse findMe() {
        return userService.findMe();
    }

    @PutMapping("/me")
    public UserResponse updateMe(@Valid @RequestBody UserUpdateRequest request) {
        return userService.updateMe(request);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserResponse> findAll(
            @Valid @ModelAttribute PaginationParams paginationParams) {
        return userService.findAll(paginationParams);
    }

    @GetMapping("/search-by-email")
    @PreAuthorize("@userAccess.canSearchUserByEmail()")
    public UserResponse findByEmail(@RequestParam(required = false) String email) {
        return userService.findByEmail(email);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@userAccess.canCreateUser(#request)")
    public UserResponse create(@Valid @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateStatus(
            @PathVariable Long id, @Valid @RequestBody UserStatusUpdateRequest request) {
        return userService.updateStatus(id, request);
    }

    @PatchMapping("/{id}/type")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateType(
            @PathVariable Long id, @Valid @RequestBody UserTypeUpdateRequest request) {
        return userService.updateType(id, request);
    }
}
