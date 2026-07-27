package pt.seerhub.community.domain;

/**
 * Estado de uma linha de {@code community_memberships} (os três do
 * {@code CHECK} do baseline {@code V2}). F02 só cria linhas {@code ACTIVE}
 * (a do dono); qualquer transição de estado é território de F03 (ver §2.1
 * do plano de F02).
 */
public enum MembershipStatus {
    ACTIVE,
    CANCELLED,
    EXPIRED
}
