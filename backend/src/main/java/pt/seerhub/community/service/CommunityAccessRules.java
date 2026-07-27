package pt.seerhub.community.service;

import org.springframework.http.HttpStatus;

import pt.seerhub.common.error.ApiException;
import pt.seerhub.community.domain.Community;
import pt.seerhub.community.domain.CommunityStatus;

/**
 * Ponto de contacto público que F03 consome (D-8 do plano de F02): funções
 * puras sobre a entidade {@link Community}, sem repositórios nem Spring —
 * por isso testável como {@code *Test}, sem contexto de integração.
 *
 * <p>F03 chama {@link #exigirQueAceitaNovasSubscricoes(Community)} no seu
 * caminho de subscrição e propaga a {@link ApiException} — a mensagem é
 * exatamente a do §6.2 da spec. F02 não implementa mais nada de
 * subscrição: nenhuma linha {@code MEMBER} é criada por esta classe.
 */
public final class CommunityAccessRules {

    public static final String MENSAGEM_COMUNIDADE_SUSPENSA = "Esta comunidade está suspensa.";

    private CommunityAccessRules() {
    }

    /** Verdadeiro só quando a comunidade está {@code ACTIVE}. */
    public static boolean aceitaNovasSubscricoes(Community comunidade) {
        return comunidade.getStatus() == CommunityStatus.ACTIVE;
    }

    /** Lança {@code ApiException(409, MENSAGEM_COMUNIDADE_SUSPENSA)} se a comunidade estiver suspensa. */
    public static void exigirQueAceitaNovasSubscricoes(Community comunidade) {
        if (!aceitaNovasSubscricoes(comunidade)) {
            throw new ApiException(HttpStatus.CONFLICT, MENSAGEM_COMUNIDADE_SUSPENSA);
        }
    }

    /**
     * Verdadeiro se a comunidade está {@code ACTIVE} (qualquer pessoa lê),
     * ou se está {@code SUSPENDED} mas quem pede tem membership (qualquer
     * {@code role}, qualquer {@code status} — D-7 do plano de F02).
     */
    public static boolean podeSerLidaPor(Community comunidade, boolean temMembership) {
        return comunidade.getStatus() == CommunityStatus.ACTIVE || temMembership;
    }
}
