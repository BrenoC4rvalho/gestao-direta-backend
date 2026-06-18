package br.com.gestaodireta.auth.controller;

import br.com.gestaodireta.auth.cookie.AuthCookieService;
import br.com.gestaodireta.auth.dto.AuthResponse;
import br.com.gestaodireta.auth.dto.ChangePasswordRequest;
import br.com.gestaodireta.auth.dto.LoginRequest;
import br.com.gestaodireta.auth.service.AuthService;
import br.com.gestaodireta.auth.service.AuthService.LoginResult;
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

    public AuthController(AuthService authService, AuthCookieService authCookieService) {
        this.authService = authService;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult loginResult = authService.login(request);
        HttpHeaders headers = new HttpHeaders();
        authCookieService.addCookie(
                headers, loginResult.token(), authService.getTokenExpirationSeconds());

        return ResponseEntity.ok().headers(headers).body(loginResult.response());
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
