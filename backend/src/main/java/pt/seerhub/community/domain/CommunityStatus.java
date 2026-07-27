package pt.seerhub.community.domain;

/**
 * Estado de uma comunidade. Persistido como {@code VARCHAR} em
 * {@code communities.status} (convenção do {@code CLAUDE.md}).
 *
 * <p>{@code SUSPENDED} tira a comunidade da listagem pública e recusa
 * novas subscrições (R2, critério 6), mas não impede a leitura por quem já
 * é membro nem a edição pelo dono (D-7 do plano de F02).
 */
public enum CommunityStatus {
    ACTIVE,
    SUSPENDED
}
