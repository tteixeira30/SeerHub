package pt.seerhub.community.api;

import java.time.Instant;

import pt.seerhub.community.domain.Community;
import pt.seerhub.community.domain.CommunityStatus;

/**
 * Representação pública de uma comunidade. Nomes de campo em inglês,
 * espelhando o modelo de dados da §8 da spec (D-13 do plano de F02).
 *
 * <p>{@code currency} vem sempre de {@link Community#MOEDA}, não de um
 * campo mapeado (D-2). {@code ownedByViewer} é o mínimo de que a UI
 * precisa para mostrar o botão "Definições" — não é o "papel efetivo" de
 * R4 (D-14), que F04 acrescenta a este mesmo registo sem remover este
 * campo.
 */
public record CommunityResponse(
        Long id,
        String slug,
        String name,
        String description,
        String avatarUrl,
        String bannerUrl,
        int priceMonthlyCents,
        String currency,
        CommunityStatus status,
        Long ownerId,
        String ownerDisplayName,
        Instant createdAt,
        boolean ownedByViewer) {

    public static CommunityResponse de(Community community, Long viewerIdOuNull) {
        boolean ownedByViewer = viewerIdOuNull != null
                && community.getOwner().getId().equals(viewerIdOuNull);

        return new CommunityResponse(
                community.getId(),
                community.getSlug(),
                community.getName(),
                community.getDescription(),
                community.getAvatarUrl(),
                community.getBannerUrl(),
                community.getPriceMonthlyCents(),
                Community.MOEDA,
                community.getStatus(),
                community.getOwner().getId(),
                community.getOwner().getDisplayName(),
                community.getCreatedAt(),
                ownedByViewer);
    }
}
