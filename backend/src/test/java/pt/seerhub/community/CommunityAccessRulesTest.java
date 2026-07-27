package pt.seerhub.community;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;

import pt.seerhub.common.error.ApiException;
import pt.seerhub.community.domain.Community;
import pt.seerhub.community.domain.CommunityStatus;
import pt.seerhub.community.service.CommunityAccessRules;
import pt.seerhub.user.domain.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 6c, 6d — ponto de contacto que F03 consome (R2, critério 6). Nenhuma Spring, nenhum mock. */
class CommunityAccessRulesTest {

    private static final Clock RELOGIO = Clock.systemUTC();

    @Test
    void comunidadeSuspensaRecusaNovasSubscricoes() {
        User dono = new User("dono@exemplo.pt", "hash", "dono", "Dono", RELOGIO);
        Community comunidade = new Community(dono, "Comunidade Suspensa", "comunidade-suspensa", RELOGIO);
        comunidade.setStatus(CommunityStatus.SUSPENDED);

        assertThat(CommunityAccessRules.aceitaNovasSubscricoes(comunidade)).isFalse();
        assertThatThrownBy(() -> CommunityAccessRules.exigirQueAceitaNovasSubscricoes(comunidade))
                .isInstanceOf(ApiException.class)
                .satisfies(excecao -> {
                    ApiException apiException = (ApiException) excecao;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiException.getDetail()).isEqualTo(CommunityAccessRules.MENSAGEM_COMUNIDADE_SUSPENSA);
                });
    }

    @Test
    void comunidadeAtivaAceitaNovasSubscricoes() {
        User dono = new User("dono2@exemplo.pt", "hash", "dono2", "Dono2", RELOGIO);
        Community comunidade = new Community(dono, "Comunidade Ativa", "comunidade-ativa", RELOGIO);

        assertThat(CommunityAccessRules.aceitaNovasSubscricoes(comunidade)).isTrue();
        // Controlo: sobre uma comunidade ativa, o ponto de verificação deixa passar sem lançar nada.
        assertThatCode(() -> CommunityAccessRules.exigirQueAceitaNovasSubscricoes(comunidade))
                .doesNotThrowAnyException();
    }
}
