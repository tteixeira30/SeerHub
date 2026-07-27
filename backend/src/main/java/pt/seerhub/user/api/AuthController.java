package pt.seerhub.user.api;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pt.seerhub.user.security.AuthenticatedUser;
import pt.seerhub.user.service.AuthService;

/** Registo, login, refresh e logout (R1). */
@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/auth/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/api/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/api/auth/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/api/auth/logout")
    public void logout(@Valid @RequestBody LogoutRequest request, @AuthenticationPrincipal AuthenticatedUser autenticado) {
        authService.logout(request, autenticado);
    }
}
