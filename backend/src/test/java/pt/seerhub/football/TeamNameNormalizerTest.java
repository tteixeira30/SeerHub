package pt.seerhub.football;

import org.junit.jupiter.api.Test;

import pt.seerhub.football.service.TeamNameNormalizer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R5, critério 6 — regras do normalizador, contrato para F06b (§4.2 do
 * plano de F05).
 */
class TeamNameNormalizerTest {

    @Test
    void normalizaParaMinusculasSemAcentosESemPrefixosOuSufixosDeClube() {
        assertThat(TeamNameNormalizer.normalizar("SL Benfica")).isEqualTo("benfica");
        assertThat(TeamNameNormalizer.normalizar("FC Porto")).isEqualTo("porto");
        assertThat(TeamNameNormalizer.normalizar("Sporting CP")).isEqualTo("sporting");
        assertThat(TeamNameNormalizer.normalizar("Atlético Madrid")).isEqualTo("atletico madrid");
        assertThat(TeamNameNormalizer.normalizar("Bayern München")).isEqualTo("bayern munchen");
        assertThat(TeamNameNormalizer.normalizar("RB Leipzig")).isEqualTo("leipzig");
    }

    @Test
    void nomeFormadoSoPorTokensDeClubeNaoFicaVazio() {
        assertThat(TeamNameNormalizer.normalizar("FC")).isEqualTo("fc");
        assertThat(TeamNameNormalizer.normalizar("SC")).isEqualTo("sc");
    }

    @Test
    void normalizarEIdempotenteENuncaDevolveNull() {
        assertThat(TeamNameNormalizer.normalizar(null)).isEqualTo("");
        assertThat(TeamNameNormalizer.normalizar("")).isEqualTo("");
        assertThat(TeamNameNormalizer.normalizar("   ")).isEqualTo("");

        String nome = "Sporting CP";
        String normalizadoUmaVez = TeamNameNormalizer.normalizar(nome);
        String normalizadoDuasVezes = TeamNameNormalizer.normalizar(normalizadoUmaVez);
        assertThat(normalizadoDuasVezes).isEqualTo(normalizadoUmaVez);
    }

    @Test
    void asVariantesDeEscritaDoRequisitoSeisNormalizamParaAFormaEsperada() {
        assertThat(TeamNameNormalizer.normalizar("Sporting")).isEqualTo("sporting");
        assertThat(TeamNameNormalizer.normalizar("Sporting Lisbon")).isEqualTo("sporting lisbon");
        assertThat(TeamNameNormalizer.normalizar("Man Utd")).isEqualTo("man utd");
        assertThat(TeamNameNormalizer.normalizar("Manchester United")).isEqualTo("manchester united");
        assertThat(TeamNameNormalizer.normalizar("Inter")).isEqualTo("inter");
        assertThat(TeamNameNormalizer.normalizar("Internazionale")).isEqualTo("internazionale");
        assertThat(TeamNameNormalizer.normalizar("Paris Saint-Germain")).isEqualTo("paris saint germain");
        assertThat(TeamNameNormalizer.normalizar("1899 Hoffenheim")).isEqualTo("1899 hoffenheim");
    }
}
