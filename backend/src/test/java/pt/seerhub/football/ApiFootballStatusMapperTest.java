package pt.seerhub.football;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import pt.seerhub.football.domain.FixtureStatus;
import pt.seerhub.football.provider.ApiFootballStatusMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** R5, casos de fronteira 8g/8h/8i. */
class ApiFootballStatusMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void estadoDesconhecidoNaoDegradaOEstadoJaGravado() {
        assertThat(ApiFootballStatusMapper.mapear("TBD")).isEmpty();
        assertThat(ApiFootballStatusMapper.mapear("CODIGO-INEXISTENTE")).isEmpty();
        assertThat(ApiFootballStatusMapper.mapear(null)).isEmpty();
        // A preservação do estado já gravado é responsabilidade do upsert em
        // FootballSyncService, que nunca sobrescreve com um Optional vazio —
        // este teste garante só que o mapeador não inventa um estado.
    }

    @Test
    void osCodigosDoFornecedorMapeiamParaOsCincoEstadosDoBaseline() {
        assertThat(ApiFootballStatusMapper.mapear("NS")).contains(FixtureStatus.SCHEDULED);

        assertThat(ApiFootballStatusMapper.mapear("1H")).contains(FixtureStatus.LIVE);
        assertThat(ApiFootballStatusMapper.mapear("HT")).contains(FixtureStatus.LIVE);
        assertThat(ApiFootballStatusMapper.mapear("2H")).contains(FixtureStatus.LIVE);
        assertThat(ApiFootballStatusMapper.mapear("ET")).contains(FixtureStatus.LIVE);

        assertThat(ApiFootballStatusMapper.mapear("FT")).contains(FixtureStatus.FINISHED);
        assertThat(ApiFootballStatusMapper.mapear("AET")).contains(FixtureStatus.FINISHED);
        assertThat(ApiFootballStatusMapper.mapear("PEN")).contains(FixtureStatus.FINISHED);

        assertThat(ApiFootballStatusMapper.mapear("PST")).contains(FixtureStatus.POSTPONED);

        assertThat(ApiFootballStatusMapper.mapear("CANC")).contains(FixtureStatus.CANCELLED);
        assertThat(ApiFootballStatusMapper.mapear("ABD")).contains(FixtureStatus.CANCELLED);

        // Sensível a minúsculas/maiúsculas: o fornecedor usa sempre maiúsculas, mas o mapeador é tolerante.
        assertThat(ApiFootballStatusMapper.mapear("ns")).contains(FixtureStatus.SCHEDULED);
    }

    @Test
    void osGolosGravadosSaoOsDoTempoRegulamentarENaoOsDoProlongamento() {
        ObjectNode fixtureComProlongamento = objectMapper.createObjectNode();
        ObjectNode score = fixtureComProlongamento.putObject("score");
        score.putObject("fulltime").put("home", 1).put("away", 1);
        score.putObject("extratime").put("home", 2).put("away", 1);
        // "goals" inclui o prolongamento (2-1); "fulltime" tem o tempo regulamentar (1-1).
        fixtureComProlongamento.putObject("goals").put("home", 2).put("away", 1);

        Integer[] golos = ApiFootballStatusMapper.golosRegulamentares(fixtureComProlongamento);
        assertThat(golos[0]).isEqualTo(1);
        assertThat(golos[1]).isEqualTo(1);

        // Sem "fulltime" preenchido, recorre a "goals" (jogo ainda a decorrer, por exemplo).
        ObjectNode fixtureSemFulltime = objectMapper.createObjectNode();
        fixtureSemFulltime.putObject("score").putObject("fulltime");
        fixtureSemFulltime.putObject("goals").put("home", 3).put("away", 0);

        Integer[] golosDeRecurso = ApiFootballStatusMapper.golosRegulamentares(fixtureSemFulltime);
        assertThat(golosDeRecurso[0]).isEqualTo(3);
        assertThat(golosDeRecurso[1]).isEqualTo(0);
    }
}
