package pt.seerhub.community.api;

import java.util.Set;

import pt.seerhub.community.domain.CommunityPermission;
import pt.seerhub.community.domain.MembershipRole;

/**
 * Uma linha de {@code GET /api/me/community-roles} (R4, secção 5 da spec:
 * «um utilizador acumula papéis» — dono de A, moderador de B, membro de C).
 * Uma linha por {@code CommunityMembership} do utilizador.
 */
public record CommunityRoleResponse(
        Long communityId,
        String slug,
        String name,
        MembershipRole role,
        boolean premium,
        Set<CommunityPermission> permissions) {
}
