package pt.seerhub.community.domain;

/**
 * Papel de uma linha de {@code community_memberships} (os três do
 * {@code CHECK} do baseline {@code V2}). F02 só cria linhas {@code OWNER}
 * — {@code MODERATOR} e {@code MEMBER} são território de F03/F04 (ver §2.1
 * do plano de F02).
 */
public enum MembershipRole {
    OWNER,
    MODERATOR,
    MEMBER
}
