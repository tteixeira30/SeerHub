package pt.seerhub.community.api;

import java.time.Instant;
import java.util.Set;

import pt.seerhub.community.domain.Community;
import pt.seerhub.community.domain.CommunityPermission;
import pt.seerhub.community.domain.CommunityStatus;
import pt.seerhub.community.domain.MembershipRole;
import pt.seerhub.community.service.CommunityAuthorization;

/**
 * Representação pública de uma comunidade. Nomes de campo em inglês,
 * espelhando o modelo de dados da §8 da spec (D-13 do plano de F02).
 *
 * <p>{@code currency} vem sempre de {@link Community#MOEDA}, não de um
 * campo mapeado (D-2). {@code ownedByViewer} é o mínimo de que a UI
 * precisa para mostrar o botão "Definições" — não é o "papel efetivo" de
 * R4 (D-14); {@code viewerRole}/{@code viewerPermissions} (F04) é que
 * expõem o papel efetivo e o conjunto de permissões do utilizador nesta
 * comunidade, sem substituir {@code ownedByViewer}.
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
        boolean ownedByViewer,
        MembershipRole viewerRole,
        Set<CommunityPermission> viewerPermissions) {

    /** F04: constrói a partir da autorização já calculada do viewer (papel efetivo + permissões). */
    public static CommunityResponse de(Community community, CommunityAuthorization authorization) {
        boolean ownedByViewer = authorization.userId() != null
                && community.getOwner().getId().equals(authorization.userId());

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
                ownedByViewer,
                authorization.role(),
                authorization.permissions());
    }

    /**
     * Mantida com o comportamento anterior a F04 (papel {@code null},
     * permissões vazias) apenas para o caminho anónimo — {@code viewerRole}
     * fica ausente do JSON ({@code default-property-inclusion: non_null}).
     */
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
                ownedByViewer,
                null,
                Set.of());
    }
}
