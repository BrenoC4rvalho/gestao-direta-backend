package br.com.gestaodireta.messaging.controller;

import br.com.gestaodireta.messaging.dto.MessagingAccountResponse;
import br.com.gestaodireta.messaging.dto.MessagingAccountStatusUpdateRequest;
import br.com.gestaodireta.messaging.dto.MessagingMessageResponse;
import br.com.gestaodireta.messaging.dto.SendTelegramMessageRequest;
import br.com.gestaodireta.messaging.service.MessagingAdministrationService;
import br.com.gestaodireta.shared.pagination.PaginationParams;
import br.com.gestaodireta.shared.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/messaging")
@PreAuthorize("hasRole('ADMIN')")
public class MessagingController {
    private final MessagingAdministrationService service;

    public MessagingController(MessagingAdministrationService service) {
        this.service = service;
    }

    @PostMapping("/telegram/messages")
    public MessagingMessageResponse send(@Valid @RequestBody SendTelegramMessageRequest request) {
        return service.send(request);
    }

    @PatchMapping("/accounts/{id}/status")
    public MessagingAccountResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody MessagingAccountStatusUpdateRequest request) {
        return service.updateStatus(id, request);
    }

    @GetMapping("/accounts")
    public PageResponse<MessagingAccountResponse> accounts(
            @Valid @ModelAttribute PaginationParams paginationParams) {
        return service.accounts(paginationParams);
    }

    @GetMapping("/messages")
    public PageResponse<MessagingMessageResponse> messages(
            @Valid @ModelAttribute PaginationParams paginationParams) {
        return service.messages(paginationParams);
    }

    @GetMapping("/telegram/status")
    public MessagingAdministrationService.TelegramStatus status() {
        return service.status();
    }
}
