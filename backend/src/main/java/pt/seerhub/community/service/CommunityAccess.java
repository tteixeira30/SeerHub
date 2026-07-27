package pt.seerhub.community.service;

import java.time.Instant;

import pt.seerhub.community.domain.MembershipRole;
import pt.seerhub.community.domain.MembershipStatus;

/**
 * Fotografia de valor do acesso de um utilizador a uma comunidade — o que
 * {@link CommunityAccessService} devolve. Nunca é uma entidade JPA: é a
 * resposta da porta, já com {@link #premium} calculado (D-3, D-4, D-5 do
 * plano de F03, via {@link MembershipAccessRules}).
 */
public record CommunityAccess(
        Long communityId,
        Long userId,
        MembershipRole role,
        MembershipStatus status,
        Instant joinedAt,
        Instant expiresAt,
        boolean premium) {

    /** Papel de gestão (D-5) — atravessa a porta independentemente de {@code status}/{@code expiresAt}. */
    public boolean gestor() {
        return MembershipAccessRules.eGestor(role);
    }

    /** Verdadeiro para uma linha {@code MEMBER} (subscrição), gestor ou não. */
    public boolean subscritor() {
        return role == MembershipRole.MEMBER;
    }

    /** Sem linha de membership e sem fallback de dono: regra fechada por omissão (D-4), sem acesso. */
    public static CommunityAccess semMembership(Long communityId, Long userId) {
        return new CommunityAccess(communityId, userId, null, null, null, null, false);
    }
}
