package pt.seerhub.user.security;

import pt.seerhub.user.domain.GlobalRole;

/**
 * Principal do utilizador autenticado (D-5 do plano de F01) — a superfície
 * pública mais importante que esta feature deixa para F02–F14. Colocado
 * como {@code principal} de um {@code UsernamePasswordAuthenticationToken}
 * com autoridade {@code ROLE_<papel>}.
 *
 * <p>Injetar num controlador protegido com
 * {@code @AuthenticationPrincipal AuthenticatedUser autenticado} e usar
 * {@code autenticado.id()} para consultar memberships/permissões por
 * comunidade a partir da base de dados — nunca confiar em papéis por
 * comunidade vindos do token (não existem: ver D-4 do plano).
 */
public record AuthenticatedUser(Long id, String email, GlobalRole role) {
}
