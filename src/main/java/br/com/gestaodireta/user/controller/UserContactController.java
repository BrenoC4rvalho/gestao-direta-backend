package br.com.gestaodireta.user.controller;

import br.com.gestaodireta.messaging.dto.*;
import br.com.gestaodireta.messaging.service.MessagingLinkService;
import br.com.gestaodireta.user.dto.*;
import br.com.gestaodireta.user.service.UserContactService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user-contact/me")
public class UserContactController {
    private final UserContactService service;
    private final MessagingLinkService links;

    public UserContactController(UserContactService service, MessagingLinkService links) {
        this.service = service;
        this.links = links;
    }

    @GetMapping
    public UserContactResponse me() {
        return service.me();
    }

    @PutMapping("/phone")
    public UserContactResponse phone(@Valid @RequestBody UpdatePhoneRequest request) {
        return service.updatePhone(request);
    }

    @PostMapping("/messaging-link-codes")
    public MessagingLinkCodeResponse code(
            @Valid @RequestBody CreateMessagingLinkCodeRequest request) {
        return links.create(request);
    }

    @DeleteMapping("/messaging-accounts/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable Long accountId) {
        links.unlink(accountId);
    }
}
