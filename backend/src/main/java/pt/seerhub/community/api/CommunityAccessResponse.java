package pt.seerhub.community.api;

import java.time.Instant;

import pt.seerhub.community.domain.Community;
import pt.seerhub.community.domain.MembershipRole;
import pt.seerhub.community.domain.MembershipStatus;
import pt.seerhub.community.service.CommunityAccess;

/**
 * D-11 do plano de F03: o endpoint de estado que o cliente usa para decidir
 * o que desenhar (Subscrever / Cancelar / Re-subscrever). Nunca dá
 * {@code 403} — é o {@code member-area} que é a porta dura.
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
        String currency) {

    public static CommunityAccessResponse de(CommunityAccess access, Community community) {
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
                Community.MOEDA);
    }
}
