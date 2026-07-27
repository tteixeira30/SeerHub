package pt.seerhub.community.api;

import java.time.Instant;

import pt.seerhub.community.domain.CommunityMembership;
import pt.seerhub.community.domain.MembershipRole;
import pt.seerhub.community.domain.MembershipStatus;
import pt.seerhub.user.domain.User;

/**
 * Uma linha de {@code GET /api/communities/{slug}/members} (R4, critério 1).
 * <b>Nunca inclui o email</b> (D-10 do plano de F04) — só
 * {@code username}/{@code displayName}, que já são públicos noutras
 * respostas.
 */
public record CommunityMemberResponse(
        Long userId,
        String username,
        String displayName,
        MembershipRole role,
        MembershipStatus status,
        Instant joinedAt,
        Instant expiresAt,
        boolean active) {

    public static CommunityMemberResponse de(CommunityMembership membership, boolean active) {
        User user = membership.getUser();
        return new CommunityMemberResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                membership.getRole(),
                membership.getStatus(),
                membership.getJoinedAt(),
                membership.getExpiresAt(),
                active);
    }

    /** O dono sem nenhuma linha de membership (fallback sintético de F03) — aparece na lista à mesma (1k). */
    public static CommunityMemberResponse paraDonoSemMembership(User owner) {
        return new CommunityMemberResponse(
                owner.getId(),
                owner.getUsername(),
                owner.getDisplayName(),
                MembershipRole.OWNER,
                MembershipStatus.ACTIVE,
                null,
                null,
                true);
    }
}
