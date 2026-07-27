package pt.seerhub.user.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code max=72} na password não é decorativo: o BCrypt trunca em 72 bytes
 * e aceitar mais criaria passwords distintas que autenticam uma à outra.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 10, max = 72) String password,
        @Size(max = 100) String displayName) {

    /**
     * Remove espaços à volta do email antes de o Bean Validation correr
     * (o construtor executa antes de {@code @Valid}): sem isto, um email
     * com espaços à volta falha {@code @Email} antes de chegar à
     * normalização de {@code AuthService} (D-7 do plano de F01).
     */
    public RegisterRequest {
        email = email == null ? null : email.trim();
    }
}
