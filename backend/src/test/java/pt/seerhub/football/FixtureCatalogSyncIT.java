package pt.seerhub.football;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.football.domain.FixtureStatus;
import pt.seerhub.football.provider.FixedFootballDataProvider;
import pt.seerhub.football.provider.ProviderCall;
import pt.seerhub.football.repo.FixtureRepository;
import pt.seerhub.football.repo.LeagueRepository;
import pt.seerhub.football.repo.PendingResultRepository;
import pt.seerhub.football.repo.TeamRepository;
import pt.seerhub.football.service.ApiCallBudgetService;
import pt.seerhub.football.service.CrestCacheService;
import pt.seerhub.football.service.FootballMetrics;
import pt.seerhub.football.service.FootballSyncService;
import pt.seerhub.football.service.SeasonResolver;
import pt.seerhub.football.service.TeamNameNormalizer;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.FootballTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R5, critérios 1c-1h, 5a-5c, 6e-6f e X1. As asserções são sempre por
 * {@code provider_id} conhecido (nunca contagens globais da tabela) porque
 * o contentor Postgres é partilhado por toda a suite (herdado de F00) — ver
 * nota em {@code FootballTestSupport}.
 */
class FixtureCatalogSyncIT extends AbstractIntegrationTest {

    private static final List<Long> LIGAS_CONFIGURADAS = List.of(94L, 39L, 140L, 135L, 78L, 61L);
    private static final List<Long> JOGOS_DO_CATALOGO = List.of(9401L, 9402L, 3901L, 3902L, 14001L, 13501L, 7801L, 6101L);
    private static final List<Long> EQUIPAS_DO_CATALOGO = List.of(
            940101L, 940102L, 940103L, 940104L, 390101L, 390102L, 390103L, 390104L,
            1400101L, 1400102L, 1350101L, 1350102L, 780101L, 780102L, 610101L, 610102L);

    @Autowired
    private FootballSyncService footballSyncService;
    @Autowired
    private FixedFootballDataProvider fake;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Clock clock;
    @Autowired
    private LeagueRepository leagueRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private FixtureRepository fixtureRepository;
    @Autowired
    private PendingResultRepository pendingResultRepository;
    @Autowired
    private ApiCallBudgetService budgetService;
    @Autowired
    private CrestCacheService crestCacheService;
    @Autowired
    private FootballMetrics metrics;

    private FootballTestSupport footballTestSupport;

    @BeforeEach
    void setUp() {
        footballTestSupport = new FootballTestSupport(jdbcTemplate);
        footballTestSupport.limparOrcamento();
        fake.limparRegisto();
    }

    @Test
    void aSincronizacaoDoCatalogoGravaOsJogosDasSeisLigasConfiguradas() {
        footballSyncService.sincronizarCatalogo();

        for (long ligaProviderId : LIGAS_CONFIGURADAS) {
            assertThat(footballTestSupport.existeLigaComProviderId(ligaProviderId))
                    .as("liga %s", ligaProviderId).isTrue();
        }
        for (long fixtureProviderId : JOGOS_DO_CATALOGO) {
            assertThat(footballTestSupport.existeJogoComProviderId(fixtureProviderId))
                    .as("jogo %s", fixtureProviderId).isTrue();
        }
    }

    @Test
    void aJanelaPedidaAoFornecedorEDeHojeAteSeteDiasDepois() {
        LocalDate hoje = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate ate = hoje.plusDays(7);

        footballSyncService.sincronizarCatalogo();

        List<ProviderCall> chamadasDeJogos = fake.chamadas().stream()
                .filter(c -> c.tipo() == ProviderCall.Tipo.JOGOS_ENTRE_DATAS)
                .toList();
        assertThat(chamadasDeJogos).hasSize(6);
        for (ProviderCall chamada : chamadasDeJogos) {
            assertThat(chamada.de()).isEqualTo(hoje);
            assertThat(chamada.ate()).isEqualTo(ate);
        }
    }

    @Test
    void jogosForaDaJanelaDeSeteDiasNaoSaoGravados() {
        long fixtureForaDaJanela = 999001L;
        LocalDate hoje = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        fake.definirResultado(94L, 2026, hoje.plusDays(10), fixtureForaDaJanela, FixtureStatus.SCHEDULED, null, null);

        footballSyncService.sincronizarCatalogo();

        assertThat(footballTestSupport.existeJogoComProviderId(fixtureForaDaJanela)).isFalse();
    }

    @Test
    void oCatalogoCustaUmaChamadaPorLigaMaisNoMaximoUmaDeBackfill() {
        footballSyncService.sincronizarCatalogo();

        assertThat(fake.contarChamadas()).isBetween(6, 7);
        long chamadasDeBackfill = fake.chamadas().stream()
                .filter(c -> c.tipo() == ProviderCall.Tipo.EQUIPAS_DA_LIGA)
                .count();
        assertThat(chamadasDeBackfill).isLessThanOrEqualTo(1);
    }

    @Test
    void correrDuasVezesNaoDuplicaJogosNemEquipas() {
        footballSyncService.sincronizarCatalogo();
        long jogosDepoisDaPrimeira = footballTestSupport.contarJogosComProviderIdEm(JOGOS_DO_CATALOGO);

        footballSyncService.sincronizarCatalogo();
        long jogosDepoisDaSegunda = footballTestSupport.contarJogosComProviderIdEm(JOGOS_DO_CATALOGO);

        assertThat(jogosDepoisDaPrimeira).isEqualTo(8);
        assertThat(jogosDepoisDaSegunda).isEqualTo(8);
    }

    @Test
    void umaRemarcacaoDoFornecedorAtualizaAHoraEOEstadoDoJogo() {
        footballSyncService.sincronizarCatalogo();
        Map<String, Object> antes = footballTestSupport.lerJogoPorProviderId(9401L);
        assertThat(antes.get("status")).isEqualTo("SCHEDULED");

        fake.remarcarJogoDoCatalogo(9401L, null, LocalTime.of(20, 30), FixtureStatus.POSTPONED);
        footballSyncService.sincronizarCatalogo();

        Map<String, Object> depois = footballTestSupport.lerJogoPorProviderId(9401L);
        assertThat(depois.get("status")).isEqualTo("POSTPONED");
        java.sql.Timestamp kickoff = (java.sql.Timestamp) depois.get("kickoff_at");
        assertThat(kickoff.toInstant().atZone(ZoneOffset.UTC).getHour()).isEqualTo(20);
        assertThat(kickoff.toInstant().atZone(ZoneOffset.UTC).getMinute()).isEqualTo(30);
    }

    @Test
    void ligasSaoGravadasComNomePaisEmblemaEEpoca() {
        footballSyncService.sincronizarCatalogo();

        Map<String, Object> liga = footballTestSupport.lerLigaPorProviderId(94L);
        assertThat(liga.get("name")).isEqualTo("Primeira Liga");
        assertThat(liga.get("country")).isEqualTo("Portugal");
        assertThat(liga.get("logo_url")).isNotNull();
        assertThat(liga.get("season")).isEqualTo(2026);
    }

    @Test
    void equipasSaoGravadasComNomeNomeCurtoPaisEEmblema() {
        // O backfill preenche no máximo uma liga por execução; corremos o
        // suficiente para que todas as 6 ligas configuradas convirjam,
        // independentemente do estado com que a base de dados partilhada
        // começou (ver nota da classe).
        for (int i = 0; i < 8; i++) {
            footballSyncService.sincronizarCatalogo();
        }

        for (long teamProviderId : EQUIPAS_DO_CATALOGO) {
            Map<String, Object> equipa = footballTestSupport.lerEquipaPorProviderId(teamProviderId);
            assertThat(equipa.get("name")).as("name de %s", teamProviderId).isNotNull();
            assertThat(equipa.get("short_name")).as("short_name de %s", teamProviderId).isNotNull();
            assertThat(equipa.get("country")).as("country de %s", teamProviderId).isNotNull();
            assertThat(equipa.get("logo_url")).as("logo_url de %s", teamProviderId).isNotNull();
        }
    }

    @Test
    void oNomeCurtoEPreenchidoPorBackfillLimitadoAUmaLigaPorExecucao() {
        long ligasCompletasAntes = footballTestSupport.contarLigasTotalmenteBackfilled();

        footballSyncService.sincronizarCatalogo();

        long chamadasDeBackfill = fake.chamadas().stream()
                .filter(c -> c.tipo() == ProviderCall.Tipo.EQUIPAS_DA_LIGA)
                .count();
        long ligasCompletasDepois = footballTestSupport.contarLigasTotalmenteBackfilled();

        assertThat(chamadasDeBackfill).isLessThanOrEqualTo(1);
        assertThat(ligasCompletasDepois - ligasCompletasAntes).isBetween(0L, 1L);
    }

    @Test
    void oNomeNormalizadoEGeradoNaSincronizacaoEGravadoNaColuna() {
        footballSyncService.sincronizarCatalogo();

        Map<String, Object> equipa = footballTestSupport.lerEquipaPorProviderId(940101L);
        assertThat(equipa.get("name")).isEqualTo("SL Benfica");
        assertThat(equipa.get("normalized_name")).isEqualTo(TeamNameNormalizer.normalizar("SL Benfica"));
        assertThat(equipa.get("normalized_name")).isEqualTo("benfica");
    }

    @Test
    void mudancaDeNomeNoFornecedorRegeneraONomeNormalizado() {
        footballSyncService.sincronizarCatalogo();
        Map<String, Object> antes = footballTestSupport.lerEquipaPorProviderId(390101L);
        assertThat(antes.get("normalized_name")).isEqualTo("arsenal");

        fake.renomearEquipa(390101L, "Arsenal FC");
        footballSyncService.sincronizarCatalogo();

        Map<String, Object> depois = footballTestSupport.lerEquipaPorProviderId(390101L);
        assertThat(depois.get("name")).isEqualTo("Arsenal FC");
        assertThat(depois.get("normalized_name")).isEqualTo("arsenal");
    }

    @Test
    void aMesmaLigaEmEpocaNovaAtualizaALinhaEmVezDeCriarOutra() {
        footballSyncService(2025).sincronizarCatalogo();
        Map<String, Object> ligaEm2025 = footballTestSupport.lerLigaPorProviderId(39L);
        assertThat(ligaEm2025.get("season")).isEqualTo(2025);
        Object idAntes = ligaEm2025.get("id");

        footballSyncService(2026).sincronizarCatalogo();
        Map<String, Object> ligaEm2026 = footballTestSupport.lerLigaPorProviderId(39L);

        assertThat(ligaEm2026.get("id")).isEqualTo(idAntes);
        assertThat(ligaEm2026.get("season")).isEqualTo(2026);
    }

    /** Constrói um {@link FootballSyncService} isolado, só com a liga 39, numa época explícita (X1). */
    private FootballSyncService footballSyncService(int epoca) {
        SeerHubProperties.Football.LeagueConfig ligaConfig =
                new SeerHubProperties.Football.LeagueConfig(39L, "Premier League", "England");
        SeerHubProperties.Football football = new SeerHubProperties.Football(
                null, null, null, 1000, 0, epoca, null, null, null, null, null, null, null, List.of(ligaConfig));
        SeerHubProperties properties = new SeerHubProperties(null, football, null);
        SeasonResolver seasonResolver = new SeasonResolver(clock, properties);
        return new FootballSyncService(fake, leagueRepository, teamRepository, fixtureRepository,
                pendingResultRepository, budgetService, seasonResolver, crestCacheService, properties, clock, metrics);
    }
}
