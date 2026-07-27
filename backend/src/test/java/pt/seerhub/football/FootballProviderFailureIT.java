package pt.seerhub.football;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.football.domain.FixtureStatus;
import pt.seerhub.football.provider.ApiFootballDataProvider;
import pt.seerhub.football.provider.FixedFootballDataProvider;
import pt.seerhub.football.repo.FixtureRepository;
import pt.seerhub.football.repo.LeagueRepository;
import pt.seerhub.football.repo.PendingResultRepository;
import pt.seerhub.football.repo.TeamRepository;
import pt.seerhub.football.service.ApiCallBudgetService;
import pt.seerhub.football.service.CrestCacheService;
import pt.seerhub.football.service.FootballCatalogService;
import pt.seerhub.football.service.FootballMetrics;
import pt.seerhub.football.service.FootballSyncService;
import pt.seerhub.football.service.SeasonResolver;
import pt.seerhub.football.service.SyncOutcome;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.AuthTestSupport;
import pt.seerhub.support.CommunityTestSupport;
import pt.seerhub.support.FootballTestSupport;
import pt.seerhub.user.api.AuthResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R5, critério 7d e casos de fronteira 8a-8f. Nenhum pedido de utilizador
 * falha por causa do fornecedor estar em baixo; nenhuma exceção do
 * fornecedor escapa do serviço de sincronização.
 */
class FootballProviderFailureIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SeerHubProperties properties;
    @Autowired
    private FootballSyncService footballSyncService;
    @Autowired
    private FixedFootballDataProvider fake;
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
    @Autowired
    private FootballCatalogService footballCatalogService;

    private FootballTestSupport footballTestSupport;
    private CommunityTestSupport communityTestSupport;
    private AuthTestSupport authTestSupport;

    private long tipId;
    private long tipCriada;
    private final AtomicLong contadorId = new AtomicLong(77_000_000L + (System.nanoTime() % 90_000L));
    private final List<Long> jogosCriados = new ArrayList<>();
    private final List<Long> equipasCriadas = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        footballTestSupport = new FootballTestSupport(jdbcTemplate);
        communityTestSupport = new CommunityTestSupport(mockMvc, objectMapper, jdbcTemplate);
        authTestSupport = new AuthTestSupport(mockMvc, objectMapper, jdbcTemplate, properties);

        footballTestSupport.limparOrcamento();
        fake.limparRegisto();
        fake.reencherLiga(94L);

        AuthResponse auth = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("football-failure"), "password12345");
        var community = communityTestSupport.criar(auth.accessToken(), communityTestSupport.nomeUnico("liga-fail"));
        tipId = footballTestSupport.inserirTip(community.id(), auth.user().id(), "PENDING");
        tipCriada = tipId;
    }

    @AfterEach
    void tearDown() {
        fake.reencherLiga(94L);
        footballTestSupport.apagarTip(tipCriada);
        for (Long fixtureId : jogosCriados) {
            footballTestSupport.apagarJogo(fixtureId);
        }
        for (Long teamId : equipasCriadas) {
            footballTestSupport.apagarEquipa(teamId);
        }
    }

    @Test
    void semChaveDeApiASincronizacaoRegistaEDevolveSemRebentar() {
        SeerHubProperties.Football.LeagueConfig ligaConfig =
                new SeerHubProperties.Football.LeagueConfig(999999L, "Liga Sem Chave", "Nowhere");
        SeerHubProperties.Football footballSemChave = new SeerHubProperties.Football(
                "", "http://localhost:1", "api-football", 1000, 20, 2026,
                null, null, null, null, 4, null, null, List.of(ligaConfig));
        SeerHubProperties propriedadesSemChave = new SeerHubProperties(null, footballSemChave, null);

        RestClient restClient = RestClient.builder().baseUrl("http://localhost:1").build();
        ApiFootballDataProvider providerReal = new ApiFootballDataProvider(restClient, propriedadesSemChave);
        SeasonResolver seasonResolverSemChave = new SeasonResolver(clock, propriedadesSemChave);
        FootballSyncService servicoSemChave = new FootballSyncService(providerReal, leagueRepository, teamRepository,
                fixtureRepository, pendingResultRepository, budgetService, seasonResolverSemChave, crestCacheService,
                propriedadesSemChave, clock, metrics);

        // Se sincronizarCatalogo() deixasse escapar a exceção do fornecedor, este
        // teste falharia aqui mesmo, antes de chegar a qualquer asserção — a prova
        // de que "nada rebenta" é o próprio teste correr até ao fim.
        SyncOutcome resultado = servicoSemChave.sincronizarCatalogo();

        assertThat(resultado.falhas()).isEqualTo(1);
        assertThat(resultado.chamadasEfetuadas()).isEqualTo(0);
    }

    @Test
    void falhaDoFornecedorERegistadaENaoPropagaExcecao() {
        fake.falharNaProximaChamada();

        SyncOutcome resultado = footballSyncService.sincronizarCatalogo();

        assertThat(resultado.falhas()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void umaLigaEmFalhaNaoImpedeAsRestantes() {
        // A primeira liga da configuração (94, Primeira Liga) falha; as
        // restantes cinco (39, 140, 135, 78, 61) continuam a sincronizar.
        fake.falharNaProximaChamada();

        SyncOutcome resultado = footballSyncService.sincronizarCatalogo();

        assertThat(resultado.falhas()).isEqualTo(1);
        assertThat(footballTestSupport.existeJogoComProviderId(3901L)).isTrue();
        assertThat(footballTestSupport.existeLigaComProviderId(39L)).isTrue();
    }

    @Test
    void comOFornecedorEmBaixoVariosCiclosOSeguinteRecuperaSozinho() {
        for (int i = 0; i < 3; i++) {
            fake.falharNaProximaChamada();
            SyncOutcome resultado = footballSyncService.sincronizarCatalogo();
            assertThat(resultado.falhas()).isGreaterThanOrEqualTo(1);
        }

        SyncOutcome resultadoFinal = footballSyncService.sincronizarCatalogo();
        assertThat(resultadoFinal.falhas()).isEqualTo(0);
        assertThat(resultadoFinal.chamadasEfetuadas()).isGreaterThanOrEqualTo(6);
    }

    @Test
    void comOFornecedorEmBaixoOsPedidosDeUtilizadorRespondemDaBaseDeDados() throws Exception {
        footballSyncService.sincronizarCatalogo();
        Map<String, Object> equipa = footballTestSupport.lerEquipaPorProviderId(940101L);
        long teamId = ((Number) equipa.get("id")).longValue();

        fake.falharNaProximaChamada();

        mockMvc.perform(get("/api/football/crests/teams/{id}", teamId)).andExpect(status().isOk());
        assertThat(footballCatalogService.equipa(teamId)).isPresent();
    }

    @Test
    void ligaSemJogosNoFornecedorNaoApagaOCatalogoNemFalha() {
        footballSyncService.sincronizarCatalogo();
        long antesDeEsvaziar = footballTestSupport.contarJogosComProviderIdEm(List.of(9401L, 9402L));

        fake.esvaziarLiga(94L);
        SyncOutcome resultado = footballSyncService.sincronizarCatalogo();

        assertThat(resultado.falhas()).isEqualTo(0);
        long depoisDeEsvaziar = footballTestSupport.contarJogosComProviderIdEm(List.of(9401L, 9402L));
        assertThat(depoisDeEsvaziar).isEqualTo(antesDeEsvaziar);
    }

    @Test
    void jogoAdiadoOuCanceladoGravaOEstadoSaiDaFilaENaoResolveSelecoes() {
        long liga = footballTestSupport.inserirLiga(77001L, "Liga de Teste", 2026);
        long jogo = criarJogo(liga, 77001L, Duration.ofHours(3));
        footballTestSupport.inserirSelecaoPendente(tipId, jogo);

        fake.definirEstado(77001L, 2026, dataUtc(jogo), providerIdDoJogo(jogo), FixtureStatus.POSTPONED);
        SyncOutcome resultado = footballSyncService.sincronizarResultadosPendentes();

        assertThat(resultado.chamadasEfetuadas()).isEqualTo(1);
        Map<String, Object> jogoDepois = footballTestSupport.lerJogo(jogo);
        assertThat(jogoDepois.get("status")).isEqualTo("POSTPONED");
        assertThat(footballTestSupport.estadosDasSelecoes(tipId)).containsOnly("PENDING");

        fake.limparRegisto();
        SyncOutcome resultado2 = footballSyncService.sincronizarResultadosPendentes();
        assertThat(resultado2.chamadasEfetuadas()).isEqualTo(0);
    }

    private long criarJogo(long ligaId, long ligaProviderId, Duration desdeOInicio) {
        Instant kickoffAt = clock.instant().minus(desdeOInicio);
        long homeId = criarEquipa();
        long awayId = criarEquipa();
        long fixtureProviderId = contadorId.incrementAndGet();
        long id = footballTestSupport.inserirJogo(fixtureProviderId, ligaId, homeId, awayId, kickoffAt, "SCHEDULED");
        jogosCriados.add(id);
        return id;
    }

    private long criarEquipa() {
        long providerId = contadorId.incrementAndGet();
        long id = footballTestSupport.inserirEquipa(providerId, "Equipa " + providerId, "equipa " + providerId);
        equipasCriadas.add(id);
        return id;
    }

    private long providerIdDoJogo(long fixtureId) {
        return (Long) footballTestSupport.lerColunaDoJogo(fixtureId, "provider_id");
    }

    private LocalDate dataUtc(long fixtureId) {
        java.sql.Timestamp kickoff = (java.sql.Timestamp) footballTestSupport.lerColunaDoJogo(fixtureId, "kickoff_at");
        return kickoff.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
    }
}
