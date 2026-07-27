package pt.seerhub.user.domain;

/**
 * Papel global do utilizador (distinto do papel por comunidade, que é do
 * âmbito de F02–F04). Persistido como {@code VARCHAR} em {@code users.global_role}.
 */
public enum GlobalRole {
    USER,
    ADMIN
}
