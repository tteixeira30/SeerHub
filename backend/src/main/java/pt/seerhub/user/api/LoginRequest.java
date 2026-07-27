package pt.seerhub.user.api;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank String password) {

    /** Ver {@link RegisterRequest#RegisterRequest} — mesma razão. */
    public LoginRequest {
        email = email == null ? null : email.trim();
    }
}
