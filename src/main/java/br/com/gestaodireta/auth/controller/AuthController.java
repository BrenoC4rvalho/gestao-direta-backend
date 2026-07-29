package br.com.gestaodireta.auth.controller;

import br.com.gestaodireta.auth.cookie.AuthCookieService;
import br.com.gestaodireta.auth.dto.AuthResponse;
import br.com.gestaodireta.auth.dto.ChangePasswordRequest;
import br.com.gestaodireta.auth.dto.LoginRequest;
import br.com.gestaodireta.auth.dto.PasswordRecoveryRequest;
import br.com.gestaodireta.auth.dto.PasswordRecoveryResetRequest;
import br.com.gestaodireta.auth.dto.PasswordRecoveryVerifyRequest;
import br.com.gestaodireta.auth.dto.PasswordRecoveryVerifyResponse;
import br.com.gestaodireta.auth.service.AuthService;
import br.com.gestaodireta.auth.service.AuthService.LoginResult;
import br.com.gestaodireta.auth.service.PasswordRecoveryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    private final AuthCookieService authCookieService;

    private final PasswordRecoveryService passwordRecoveryService;

    public AuthController(
            AuthService authService,
            AuthCookieService authCookieService,
            PasswordRecoveryService passwordRecoveryService) {
        this.authService = authService;
        this.authCookieService = authCookieService;
        this.passwordRecoveryService = passwordRecoveryService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult loginResult = authService.login(request);
        HttpHeaders headers = new HttpHeaders();
        authCookieService.addCookie(headers, loginResult.token(), loginResult.expirationSeconds());

        return ResponseEntity.ok().headers(headers).body(loginResult.response());
    }

    @PostMapping("/password-recovery/requests")
    public ResponseEntity<String> requestPasswordRecovery(
            @Valid @RequestBody PasswordRecoveryRequest request) {
        passwordRecoveryService.request(request);
        return ResponseEntity.accepted().body(PasswordRecoveryService.GENERIC_MESSAGE);
    }

    @PostMapping("/password-recovery/verify")
    public PasswordRecoveryVerifyResponse verifyPasswordRecovery(
            @Valid @RequestBody PasswordRecoveryVerifyRequest request) {
        return passwordRecoveryService.verify(request);
    }

    @PostMapping("/password-recovery/reset")
    public ResponseEntity<String> resetPasswordRecovery(
            @Valid @RequestBody PasswordRecoveryResetRequest request) {
        passwordRecoveryService.reset(request);
        return ResponseEntity.ok("Senha redefinida com sucesso.");
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        HttpHeaders headers = new HttpHeaders();
        authCookieService.expireCookie(headers);

        return ResponseEntity.noContent().headers(headers).build();
    }

    @GetMapping("/session")
    public AuthResponse session() {
        return authService.getSession();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        HttpHeaders headers = new HttpHeaders();
        authCookieService.expireCookie(headers);

        return ResponseEntity.noContent().headers(headers).build();
    }
}
