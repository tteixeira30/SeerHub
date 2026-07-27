package pt.seerhub.community.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code PUT /api/communities/{slug}} (R2, critério 3). Mesma
 * forma de {@link CreateCommunityRequest}, representação completa (D-4 do
 * plano de F02): {@code name} e {@code priceMonthlyCents} são
 * obrigatórios; {@code description}/{@code avatarUrl}/{@code bannerUrl}
 * ausentes ou em branco significam limpar o campo, sem ambiguidade.
 */
public record UpdateCommunityRequest(
        @NotBlank @Size(min = 3, max = 60) String name,
        @Size(max = 2000) String description,
        @Size(max = 500) @Pattern(regexp = "^https?://.+") String avatarUrl,
        @Size(max = 500) @Pattern(regexp = "^https?://.+") String bannerUrl,
        @NotNull @Min(0) Integer priceMonthlyCents) {

    public UpdateCommunityRequest {
        name = name == null ? null : name.trim();
        description = emBrancoParaNulo(description);
        avatarUrl = emBrancoParaNulo(avatarUrl);
        bannerUrl = emBrancoParaNulo(bannerUrl);
    }

    private static String emBrancoParaNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor;
    }
}
