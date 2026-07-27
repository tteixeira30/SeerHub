package pt.seerhub.community.api;

import java.time.Instant;
import java.util.Set;

import pt.seerhub.community.domain.Community;
import pt.seerhub.community.domain.CommunityPermission;
import pt.seerhub.community.domain.MembershipRole;
import pt.seerhub.community.domain.MembershipStatus;
import pt.seerhub.community.service.CommunityAccess;
import pt.seerhub.community.service.CommunityAuthorization;

/**
 * D-11 do plano de F03: o endpoint de estado que o cliente usa para decidir
 * o que desenhar (Subscrever / Cancelar / Re-subscrever). Nunca dá
 * {@code 403} — é o {@code member-area} que é a porta dura.
 *
 * <p>F04 acrescenta {@code permissions} (o conjunto de {@link CommunityPermission}
 * do viewer) sem remover {@code premium}/{@code manager} — F07/F10/F11/F12
 * podem já depender deles.
 */
public record CommunityAccessResponse(
        Long communityId,
        String slug,
        boolean premium,
        boolean manager,
        MembershipRole role,
        MembershipStatus status,
        Instant joinedAt,
        Instant expiresAt,
        int priceMonthlyCents,
        String currency,
        Set<CommunityPermission> permissions) {

    public static CommunityAccessResponse de(CommunityAccess access, Community community, CommunityAuthorization authorization) {
        return new CommunityAccessResponse(
                community.getId(),
                community.getSlug(),
                access.premium(),
                access.gestor(),
                access.role(),
                access.status(),
                access.joinedAt(),
                access.expiresAt(),
                community.getPriceMonthlyCents(),
                Community.MOEDA,
                authorization.permissions());
    }
}
