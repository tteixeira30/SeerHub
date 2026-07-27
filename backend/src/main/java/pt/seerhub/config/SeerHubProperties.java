package pt.seerhub.config;

import java.nio.file.Path;
import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propriedades tipadas da aplicação, sob o prefixo {@code seerhub}.
 *
 * <p>{@code security.jwtSecret} não tem valor por omissão de propósito: se
 * faltar, o arranque tem de falhar imediatamente (ver X2 no plano de F00),
 * em vez de a aplicação arrancar com um segredo vazio e falhar mais tarde,
 * silenciosamente, na autenticação.
 */
@ConfigurationProperties("seerhub")
@Validated
public record SeerHubProperties(
        @Valid Security security,
        @Valid Football football,
        @Valid Uploads uploads) {

    /** Segredos e tempos de vida dos tokens JWT (usados a partir de F01). */
    public record Security(
            @NotBlank String jwtSecret,
            Duration accessTtl,
            Duration refreshTtl) {
    }

    /** Credenciais e endpoint da API-Football (usados a partir de F05). */
    public record Football(
            String apiKey,
            String baseUrl) {
    }

    /** Diretório de uploads persistentes (usado a partir de F06/F07). */
    public record Uploads(
            Path dir) {
    }
}
