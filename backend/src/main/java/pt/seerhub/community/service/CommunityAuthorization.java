package pt.seerhub.community.service;

import java.util.Set;

import pt.seerhub.community.domain.CommunityPermission;
import pt.seerhub.community.domain.MembershipRole;

/**
 * Fotografia de valor da autorização de um utilizador numa comunidade — o
 * que {@link CommunityPermissionService} devolve. Nunca é uma entidade JPA e
 * nunca segura uma {@link pt.seerhub.community.domain.Community}: é sempre
 * a resposta da porta, com {@link #permissions} já calculado por
 * {@link CommunityPermissionRules}.
 */
public record CommunityAuthorization(
        Long communityId,
        Long userId,
        MembershipRole role,
        boolean premium,
        Set<CommunityPermission> permissions) {

    public boolean pode(CommunityPermission permission) {
        return permissions.contains(permission);
    }

    /** Papel de gestão (D-5 de F03) — atravessa a porta independentemente do acesso premium. */
    public boolean gestor() {
        return MembershipAccessRules.eGestor(role);
    }

    /** Sem papel reconhecido nesta comunidade: fechado por omissão, sem nenhuma permissão. */
    public static CommunityAuthorization semPapel(Long communityId, Long userIdOuNull) {
        return new CommunityAuthorization(communityId, userIdOuNull, null, false, Set.of());
    }
}
