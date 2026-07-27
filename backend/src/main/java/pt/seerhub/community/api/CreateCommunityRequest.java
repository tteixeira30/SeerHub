package pt.seerhub.community.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/communities} (R2, critério 1). {@code name} é
 * obrigatório (3–60 caracteres); os restantes são opcionais.
 */
public record CreateCommunityRequest(
        @NotBlank @Size(min = 3, max = 60) String name,
        @Size(max = 2000) String description,
        @Size(max = 500) @Pattern(regexp = "^https?://.+") String avatarUrl,
        @Size(max = 500) @Pattern(regexp = "^https?://.+") String bannerUrl,
        @NotNull @Min(0) Integer priceMonthlyCents) {

    /**
     * Remove espaços à volta do nome e converte campos opcionais em branco
     * para {@code null}, antes do Bean Validation correr (o construtor
     * compacto executa antes de {@code @Valid}) — mesmo padrão de
     * {@code RegisterRequest} (desvio 4 do plano de F01).
     */
    public CreateCommunityRequest {
        name = name == null ? null : name.trim();
        description = emBrancoParaNulo(description);
        avatarUrl = emBrancoParaNulo(avatarUrl);
        bannerUrl = emBrancoParaNulo(bannerUrl);
    }

    private static String emBrancoParaNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor;
    }
}
