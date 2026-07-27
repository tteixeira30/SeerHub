package pt.seerhub.community.service;

import java.util.EnumSet;
import java.util.Set;

import pt.seerhub.community.domain.CommunityPermission;
import pt.seerhub.community.domain.MembershipRole;

/**
 * A matriz papel × permissão (R4), em forma pura e estática — mesma família
 * de {@link CommunityAccessRules}/{@link MembershipAccessRules}: sem Spring,
 * sem repositórios, testável como {@code *Test} sem contexto.
 *
 * <p><b>Regras fixadas pela tabela (§3.1 do plano de F04):</b>
 * <ul>
 *   <li>{@code OWNER} tem sempre todas as permissões do catálogo.</li>
 *   <li>{@code MODERATOR} ignora {@code acessoPremium} (D-5 de F03: um
 *       moderador com a sua própria linha {@code EXPIRED} continua a
 *       moderar) — tem sempre {@code READ_TIPS}, {@code READ_CHAT},
 *       {@code WRITE_CHAT}, {@code PUBLISH_TIPS}, {@code SETTLE_TIPS} e
 *       {@code DELETE_ANY_CHAT_MESSAGE}; nunca {@code MANAGE_MODERATORS},
 *       {@code EDIT_COMMUNITY} nem {@code DELETE_COMMUNITY} (essas são só
 *       do dono).</li>
 *   <li>{@code MEMBER} com acesso premium só tem as três permissões de
 *       leitura/participação ({@code READ_TIPS}/{@code READ_CHAT}/
 *       {@code WRITE_CHAT}); sem acesso premium, nenhuma.</li>
 *   <li>Fechado por omissão: {@code role == null} devolve o conjunto
 *       vazio, e qualquer valor de {@link MembershipRole} sem linha nesta
 *       matriz também devolve o conjunto vazio — nunca acesso por
 *       omissão.</li>
 * </ul>
 */
public final class CommunityPermissionRules {

    private static final Set<CommunityPermission> PERMISSOES_DE_GESTAO = Set.copyOf(EnumSet.of(
            CommunityPermission.READ_TIPS,
            CommunityPermission.READ_CHAT,
            CommunityPermission.WRITE_CHAT,
            CommunityPermission.PUBLISH_TIPS,
            CommunityPermission.SETTLE_TIPS,
            CommunityPermission.DELETE_ANY_CHAT_MESSAGE));

    private static final Set<CommunityPermission> PERMISSOES_DE_MEMBRO_PREMIUM = Set.copyOf(EnumSet.of(
            CommunityPermission.READ_TIPS,
            CommunityPermission.READ_CHAT,
            CommunityPermission.WRITE_CHAT));

    private CommunityPermissionRules() {
    }

    /** A matriz completa: o conjunto de permissões de {@code role} nesta comunidade, dado o acesso premium atual. */
    public static Set<CommunityPermission> permissoesDe(MembershipRole role, boolean acessoPremium) {
        if (role == null) {
            return Set.of();
        }
        return switch (role) {
            case OWNER -> Set.copyOf(EnumSet.allOf(CommunityPermission.class));
            case MODERATOR -> PERMISSOES_DE_GESTAO;
            case MEMBER -> acessoPremium ? PERMISSOES_DE_MEMBRO_PREMIUM : Set.of();
        };
    }

    /** Atalho booleano sobre {@link #permissoesDe(MembershipRole, boolean)}. */
    public static boolean permite(MembershipRole role, boolean acessoPremium, CommunityPermission permission) {
        return permissoesDe(role, acessoPremium).contains(permission);
    }
}
