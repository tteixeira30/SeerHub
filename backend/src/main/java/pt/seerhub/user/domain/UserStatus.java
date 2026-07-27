package pt.seerhub.user.domain;

/**
 * Estado da conta. {@code SUSPENDED} prepara o R14 (painel de administração):
 * um utilizador suspenso não autentica, nem no login nem no refresh, e a
 * resposta é indistinguível da de credenciais erradas (ver X5 de F01).
 */
public enum UserStatus {
    ACTIVE,
    SUSPENDED
}
