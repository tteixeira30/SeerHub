package pt.seerhub.community;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import pt.seerhub.community.domain.MembershipRole;
import pt.seerhub.community.domain.MembershipStatus;
import pt.seerhub.community.service.MembershipAccessRules;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regra pura de acesso (D-3, D-4, D-5 do plano de F03) — sem contexto Spring.
 * Fixa a semântica antes de existir qualquer endpoint (passo 1 da ordem de
 * implementação do plano).
 */
class MembershipAccessRulesTest {

    private static final Instant AGORA = Instant.parse("2026-07-27T12:00:00Z");

    @Test
    void proximaExpiracaoEAgoraMaisTrintaDias() {
        Instant esperado = AGORA.plus(Duration.ofDays(30));

        assertThat(MembershipAccessRules.proximaExpiracao(AGORA)).isEqualTo(esperado);
    }

    @Test
    void membroCanceladoDentroDoPrazoMantemAcesso() {
        Instant expiraNoFuturo = AGORA.plus(Duration.ofDays(5));

        boolean acesso = MembershipAccessRules.concedeAcessoPremium(
                MembershipRole.MEMBER, MembershipStatus.CANCELLED, expiraNoFuturo, AGORA);

        assertThat(acesso).isTrue();
    }

    @Test
    void deveExpirarNaFronteiraExata() {
        boolean deveExpirar = MembershipAccessRules.deveExpirar(MembershipStatus.ACTIVE, AGORA, AGORA);

        assertThat(deveExpirar).isTrue();
    }

    @Test
    void deveExpirarIgnoraLinhasSemDataDeExpiracao() {
        boolean deveExpirar = MembershipAccessRules.deveExpirar(MembershipStatus.ACTIVE, null, AGORA);

        assertThat(deveExpirar).isFalse();
    }

    /** Caso feliz de {@code deveExpirar}: uma linha claramente vencida no passado. */
    @Test
    void deveExpirarQuandoMembershipAtivaTemDataNoPassado() {
        Instant noPassado = AGORA.minus(Duration.ofDays(1));

        boolean deveExpirar = MembershipAccessRules.deveExpirar(MembershipStatus.ACTIVE, noPassado, AGORA);

        assertThat(deveExpirar).isTrue();
    }

    @Test
    void gestorTemAcessoIndependentementeDoEstadoEDaData() {
        Instant[] datas = { null, AGORA.plus(Duration.ofDays(10)), AGORA.minus(Duration.ofDays(10)) };

        for (MembershipRole gestor : new MembershipRole[] { MembershipRole.OWNER, MembershipRole.MODERATOR }) {
            for (MembershipStatus status : MembershipStatus.values()) {
                for (Instant expiresAt : datas) {
                    boolean acesso = MembershipAccessRules.concedeAcessoPremium(gestor, status, expiresAt, AGORA);
                    assertThat(acesso)
                            .as("gestor=%s status=%s expiresAt=%s", gestor, status, expiresAt)
                            .isTrue();
                }
            }
        }
    }

    /** Contraste explícito com o teste anterior: um MEMBER expirado nunca tem acesso, seja qual for a data. */
    @Test
    void membroExpiradoNaoTemAcesso() {
        Instant[] datas = { null, AGORA.plus(Duration.ofDays(10)), AGORA.minus(Duration.ofDays(10)) };

        for (Instant expiresAt : datas) {
            boolean acesso = MembershipAccessRules.concedeAcessoPremium(
                    MembershipRole.MEMBER, MembershipStatus.EXPIRED, expiresAt, AGORA);
            assertThat(acesso).as("expiresAt=%s", expiresAt).isFalse();
        }
    }

    @Test
    void membroSemDataDeExpiracaoNaoTemAcessoRegraFechada() {
        boolean acesso = MembershipAccessRules.concedeAcessoPremium(
                MembershipRole.MEMBER, MembershipStatus.ACTIVE, null, AGORA);

        assertThat(acesso).isFalse();
    }

    @Test
    void semLinhaDeMembershipNaoHaAcessoPremium() {
        boolean acesso = MembershipAccessRules.concedeAcessoPremium(null, null, null, AGORA);

        assertThat(acesso).isFalse();
    }

    @Test
    void membroAtivoDentroDoPrazoTemAcesso() {
        Instant expiraNoFuturo = AGORA.plus(Duration.ofDays(15));

        boolean acesso = MembershipAccessRules.concedeAcessoPremium(
                MembershipRole.MEMBER, MembershipStatus.ACTIVE, expiraNoFuturo, AGORA);

        assertThat(acesso).isTrue();
    }
}
