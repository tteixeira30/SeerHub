package pt.seerhub.football;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import pt.seerhub.football.provider.FixedFootballDataProvider;
import pt.seerhub.football.service.FixtureResultView;
import pt.seerhub.football.service.FootballCatalogService;
import pt.seerhub.football.service.FootballSyncService;
import pt.seerhub.football.service.TeamNameNormalizer;
import pt.seerhub.football.service.TeamView;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.FootballTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R5, critérios 5i, 6g e a superfície de resultados para F08 (§2.5 do
 * plano de F05).
 */
class FootballCatalogSurfaceIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private FootballSyncService footballSyncService;
    @Autowired
    private FixedFootballDataProvider fake;
    @Autowired
    private FootballCatalogService footballCatalogService;
    @Autowired
    private Clock clock;

    private FootballTestSupport footballTestSupport;
    private final List<Long> jogosCriados = new ArrayList<>();
    private final List<Long> equipasCriadas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        footballTestSupport = new FootballTestSupport(jdbcTemplate);
        footballTestSupport.limparOrcamento();
        fake.limparRegisto();
    }

    @AfterEach
    void tearDown() {
        for (Long fixtureId : jogosCriados) {
            footballTestSupport.apagarJogo(fixtureId);
        }
        for (Long teamId : equipasCriadas) {
            footballTestSupport.apagarEquipa(teamId);
        }
    }

    @Test
    void osEmblemasExpostosPelaSuperficieSaoSempreLocais() {
        footballSyncService.sincronizarCatalogo();

        Map<String, Object> equipa = footballTestSupport.lerEquipaPorProviderId(940101L);
        long teamId = ((Number) equipa.get("id")).longValue();

        Optional<TeamView> view = footballCatalogService.equipa(teamId);
        assertThat(view).isPresent();
        assertThat(view.get().crestUrl()).startsWith("/api/football/crests/teams/");
        assertThat(view.get().crestUrl()).doesNotContain("fake-provider.test");

        long fixtureId = ((Number) footballTestSupport.lerJogoPorProviderId(9401L).get("id")).longValue();
        Optional<pt.seerhub.football.service.FixtureView> jogoView = footballCatalogService.jogo(fixtureId);
        assertThat(jogoView).isPresent();
        assertThat(jogoView.get().leagueCrestUrl()).startsWith("/api/football/crests/leagues/");
        assertThat(jogoView.get().home().crestUrl()).startsWith("/api/football/crests/teams/");
        assertThat(jogoView.get().away().crestUrl()).startsWith("/api/football/crests/teams/");
    }

    @Test
    void aPesquisaPorTrigramasSobreNomesNormalizadosDevolveOCandidatoCerto() {
        footballSyncService.sincronizarCatalogo();
        String termoDePesquisa = TeamNameNormalizer.normalizar("Sporting Lisbon");

        Map<String, Object> candidato = jdbcTemplate.queryForMap(
                "SELECT id, name, similarity(normalized_name, ?) AS sim FROM teams "
                        + "WHERE provider_id IN (940101, 940102, 940103, 940104) "
                        + "ORDER BY sim DESC LIMIT 1",
                termoDePesquisa);

        assertThat(candidato.get("name")).isEqualTo("Sporting CP");
    }

    @Test
    void aSuperficieDeResultadosDevolveOEstadoEOsGolosParaF08() {
        long liga = footballTestSupport.inserirLiga(55001L, "Liga de Teste", 2026);
        long home = criarEquipa(55011L);
        long away = criarEquipa(55012L);
        Instant kickoffAt = clock.instant().minus(Duration.ofHours(3));
        long fixtureId = footballTestSupport.inserirJogo(550001L, liga, home, away, kickoffAt, "SCHEDULED");
        jogosCriados.add(fixtureId);

        jdbcTemplate.update(
                "UPDATE fixtures SET status = 'FINISHED', home_score = 2, away_score = 1, last_synced_at = now() "
                        + "WHERE id = ?",
                fixtureId);

        Optional<FixtureResultView> resultado = footballCatalogService.resultadoDe(fixtureId);
        assertThat(resultado).isPresent();
        assertThat(resultado.get().terminado()).isTrue();
        assertThat(resultado.get().naoSeRealizou()).isFalse();
        assertThat(resultado.get().homeScore()).isEqualTo(2);
        assertThat(resultado.get().awayScore()).isEqualTo(1);
        assertThat(resultado.get().lastSyncedAt()).isNotNull();

        List<FixtureResultView> lote = footballCatalogService.resultadosDe(List.of(fixtureId));
        assertThat(lote).hasSize(1);
        assertThat(lote.get(0).fixtureId()).isEqualTo(fixtureId);
    }

    private long criarEquipa(long providerId) {
        long id = footballTestSupport.inserirEquipa(providerId, "Equipa " + providerId, "equipa " + providerId);
        equipasCriadas.add(id);
        return id;
    }
}
