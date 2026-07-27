package pt.seerhub.community.service;

import java.time.Duration;
import java.time.Instant;

import pt.seerhub.community.domain.MembershipRole;
import pt.seerhub.community.domain.MembershipStatus;

/**
 * Regras puras e estáticas de acesso a conteúdo premium de uma comunidade
 * (D-2 do plano de F03) — sem Spring, sem repositórios, testável como
 * {@code *Test}. É a mesma família de {@link CommunityAccessRules} (F02):
 * lógica pura primeiro, porta com Spring por cima.
 *
 * <p><b>D-3 — o acesso é calculado por {@code expiresAt}, não por
 * {@code status}.</b> Uma linha {@code ACTIVE} cuja {@code expiresAt} já
 * passou não dá acesso, mesmo antes de a tarefa diária correr.
 *
 * <p><b>D-4 — regra fechada por omissão.</b> Sem papel reconhecido, sem
 * acesso; um {@code MEMBER} sem {@code expiresAt} não tem acesso.
 *
 * <p><b>D-5 — gestores atravessam sempre.</b> {@code OWNER}/{@code MODERATOR}
 * têm acesso independentemente de {@code status} e de {@code expiresAt}.
 */
public final class MembershipAccessRules {

    /** R3: toda subscrição nova (ou renovada) dura 30 dias. */
    public static final Duration DURACAO_SUBSCRICAO = Duration.ofDays(30);

    private MembershipAccessRules() {
    }

    /** Verdadeiro para {@code OWNER} e {@code MODERATOR} — os papéis de gestão. */
    public static boolean eGestor(MembershipRole role) {
        return role == MembershipRole.OWNER || role == MembershipRole.MODERATOR;
    }

    /**
     * A porta de acesso, em forma pura. Gestores atravessam sempre (D-5).
     * Um {@code MEMBER} só tem acesso com {@code status} {@code ACTIVE} ou
     * {@code CANCELLED} (D-3: cancelar não retira acesso imediato) e
     * {@code expiresAt} estritamente depois de {@code agora} (fronteira
     * exata {@code expiresAt == agora} já não dá acesso, coerente com
     * {@link #deveExpirar}). Qualquer outra combinação — sem linha,
     * {@code EXPIRED}, ou {@code expiresAt} nulo — nega (D-4).
     */
    public static boolean concedeAcessoPremium(MembershipRole role, MembershipStatus status, Instant expiresAt,
            Instant agora) {
        if (role == null) {
            return false;
        }
        if (eGestor(role)) {
            return true;
        }
        if (status != MembershipStatus.ACTIVE && status != MembershipStatus.CANCELLED) {
            return false;
        }
        return expiresAt != null && expiresAt.isAfter(agora);
    }

    /**
     * Verdadeiro sse a linha deve ser marcada {@code EXPIRED} pela tarefa
     * diária (D-13): {@code status} {@code ACTIVE} ou {@code CANCELLED},
     * {@code expiresAt} não nulo e já chegado ({@code expiresAt <= agora},
     * fronteira inclusive). Linhas sem {@code expiresAt} (o dono) nunca
     * expiram.
     */
    public static boolean deveExpirar(MembershipStatus status, Instant expiresAt, Instant agora) {
        if (expiresAt == null) {
            return false;
        }
        if (status != MembershipStatus.ACTIVE && status != MembershipStatus.CANCELLED) {
            return false;
        }
        return !expiresAt.isAfter(agora);
    }

    /** {@code agora + 30 dias} — usado tanto na subscrição nova como na renovação. */
    public static Instant proximaExpiracao(Instant agora) {
        return agora.plus(DURACAO_SUBSCRICAO);
    }
}
