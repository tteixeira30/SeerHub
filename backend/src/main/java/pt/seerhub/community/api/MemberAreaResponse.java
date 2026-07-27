package pt.seerhub.community.api;

import java.time.Instant;

import pt.seerhub.community.domain.Community;
import pt.seerhub.community.domain.MembershipRole;
import pt.seerhub.community.domain.MembershipStatus;
import pt.seerhub.community.service.CommunityAccess;

/**
 * O conteúdo premium de F03 (D-10 do plano): o primeiro endpoint do SeerHub
 * que exige acesso ativo, e o consumidor de referência de
 * {@code CommunityAccessService}. F07/F10/F11/F12 <b>não estendem este
 * endpoint</b> — chamam a porta a partir dos seus próprios.
 */
public record MemberAreaResponse(
        Long communityId,
        String slug,
        String name,
        MembershipRole role,
        MembershipStatus status,
        Instant joinedAt,
        Instant expiresAt) {

    public static MemberAreaResponse de(Community community, CommunityAccess access) {
        return new MemberAreaResponse(
                community.getId(),
                community.getSlug(),
                community.getName(),
                access.role(),
                access.status(),
                access.joinedAt(),
                access.expiresAt());
    }
}
