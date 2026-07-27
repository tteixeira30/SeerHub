package pt.seerhub.football;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.football.domain.FixtureStatus;
import pt.seerhub.football.provider.FixedFootballDataProvider;
import pt.seerhub.football.repo.PendingResultGroup;
import pt.seerhub.football.repo.PendingResultRepository;
import pt.seerhub.football.service.FootballSyncService;
import pt.seerhub.football.service.SyncOutcome;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.AuthTestSupport;
import pt.seerhub.support.CommunityTestSupport;
import pt.seerhub.support.FootballTestSupport;
import pt.seerhub.user.api.AuthResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R5, critérios 2b-2k — a sincronização de resultados é a pedido, nunca por
 * sondagem fixa. Cada teste cria a sua própria liga/equipas/jogo com
 * {@code provider_id}s únicos e apaga tudo em {@link #tearDown()} — a
 * consulta de procura (§2.4-E do plano) é deliberadamente global, sem
 * âmbito de comunidade, por isso um jogo pendente esquecido por um teste
 * contaminaria "zero chamadas" de outro (ver nota em
 * {@code FootballTestSupport}).
 */
class PendingResultSyncIT extends AbstractIntegrationTest {

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
    private PendingResultRepository pendingResultRepository;

    private FootballTestSupport footballTestSupport;
    private CommunityTestSupport communityTestSupport;
    private AuthTestSupport authTestSupport;

    private long tipId;
    private final AtomicLong contadorId = new AtomicLong(System.nanoTime() % 1_000_000_000L);
    private final List<Long> jogosCriados = new ArrayList<>();
    private final List<Long> equipasCriadas = new ArrayList<>();
    private final List<Long> ligasCriadas = new ArrayList<>();
    private long tipCriada;

    @BeforeEach
    void setUp() throws Exception {
        footballTestSupport = new FootballTestSupport(jdbcTemplate);
        communityTestSupport = new CommunityTestSupport(mockMvc, objectMapper, jdbcTemplate);
        authTestSupport = new AuthTestSupport(mockMvc, objectMapper, jdbcTemplate, properties);

        footballTestSupport.limparOrcamento();
        fake.limparRegisto();

        AuthResponse auth = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("football-results"), "password12345");
        var community = communityTestSupport.criar(auth.accessToken(), communityTestSupport.nomeUnico("liga-res"));
        tipId = footballTestSupport.inserirTip(community.id(), auth.user().id(), "PENDING");
        tipCriada = tipId;
    }

    @AfterEach
    void tearDown() {
        footballTestSupport.apagarTip(tipCriada);
        for (Long fixtureId : jogosCriados) {
            footballTestSupport.apagarJogo(fixtureId);
        }
        for (Long teamId : equipasCriadas) {
            footballTestSupport.apagarEquipa(teamId);
        }
        for (Long leagueId : ligasCriadas) {
            footballTestSupport.apagarLiga(leagueId);
        }
    }

    @Test
    void umDiaSemSelecoesPendentesCustaZeroChamadas() {
        SyncOutcome resultado = footballSyncService.sincronizarResultadosPendentes();

        assertThat(resultado.chamadasEfetuadas()).isEqualTo(0);
        assertThat(fake.contarChamadas()).isEqualTo(0);
    }

    @Test
    void aProcuraEAvaliadaEmBaseDeDadosAntesDeQualquerChamada() {
        Instant kickoff = clock.instant().minus(Duration.ofHours(3));
        LigaDeTeste liga = criarLiga();
        FixtureDeTeste jogo = criarJogo(liga.id(), kickoff, "SCHEDULED");
        footballTestSupport.inserirSelecaoPendente(tipId, jogo.id());

        List<PendingResultGroup> grupos = pendingResultRepository.gruposPorResolver(
                clock.instant().minus(Duration.ofHours(2)),
                clock.instant().minus(Duration.ofDays(7)),
                clock.instant().minus(Duration.ofMinutes(30)), 4);
        assertThat(grupos).hasSize(1);
        assertThat(grupos.get(0).ligaProviderId()).isEqualTo(liga.providerId());

        fake.definirResultado(liga.providerId(), 2026, dataUtc(kickoff), jogo.providerId(),
                FixtureStatus.FINISHED, 2, 1);
        SyncOutcome resultado = footballSyncService.sincronizarResultadosPendentes();

        assertThat(resultado.chamadasEfetuadas()).isEqualTo(1);
        assertThat(fake.contarChamadas()).isEqualTo(1);
    }

    @Test
    void jogoComenosDeDuasHorasDesdeOInicioNaoGeraChamada() {
        Instant kickoff = clock.instant().minus(Duration.ofMinutes(90));
        LigaDeTeste liga = criarLiga();
        FixtureDeTeste jogo = criarJogo(liga.id(), kickoff, "SCHEDULED");
        footballTestSupport.inserirSelecaoPendente(tipId, jogo.id());

        SyncOutcome resultado = footballSyncService.sincronizarResultadosPendentes();

        assertThat(resultado.chamadasEfetuadas()).isEqualTo(0);
        assertThat(fake.contarChamadas()).isEqualTo(0);
    }

    @Test
    void jogoComecadoHaMaisDeDuasHorasEPorTerminarGeraChamadaEGravaOResultado() {
        Instant kickoff = clock.instant().minus(Duration.ofHours(3));
        LigaDeTeste liga = criarLiga();
        FixtureDeTeste jogo = criarJogo(liga.id(), kickoff, "SCHEDULED");
        footballTestSupport.inserirSelecaoPendente(tipId, jogo.id());
        fake.definirResultado(liga.providerId(), 2026, dataUtc(kickoff), jogo.providerId(),
                FixtureStatus.FINISHED, 2, 1);

        SyncOutcome resultado = footballSyncService.sincronizarResultadosPendentes();

        assertThat(resultado.chamadasEfetuadas()).isEqualTo(1);
        Map<String, Object> jogoDepois = footballTestSupport.lerJogo(jogo.id());
        assertThat(jogoDepois.get("status")).isEqualTo("FINISHED");
        assertThat(jogoDepois.get("home_score")).isEqualTo(2);
        assertThat(jogoDepois.get("away_score")).isEqualTo(1);
        assertThat(jogoDepois.get("last_synced_at")).isNotNull();
    }

    @Test
    void jogoJaTerminadoNaoGeraNovaChamada() {
        Instant kickoff = clock.instant().minus(Duration.ofHours(3));
        LigaDeTeste liga = criarLiga();
        FixtureDeTeste jogo = criarJogo(liga.id(), kickoff, "FINISHED");
        footballTestSupport.inserirSelecaoPendente(tipId, jogo.id());

        SyncOutcome resultado = footballSyncService.sincronizarResultadosPendentes();

        assertThat(resultado.chamadasEfetuadas()).isEqualTo(0);
        assertThat(fake.contarChamadas()).isEqualTo(0);
    }

    @Test
    void selecaoJaResolvidaNaoGeraChamada() {
        Instant kickoff = clock.instant().minus(Duration.ofHours(3));
        LigaDeTeste liga = criarLiga();
        FixtureDeTeste jogo = criarJogo(liga.id(), kickoff, "SCHEDULED");
        footballTestSupport.inserirSelecao(tipId, jogo.id(), "WON");

        SyncOutcome resultado = footballSyncService.sincronizarResultadosPendentes();

        assertThat(resultado.chamadasEfetuadas()).isEqualTo(0);
        assertThat(fake.contarChamadas()).isEqualTo(0);
    }

    @Test
    void selecaoSemJogoAssociadoNaoGeraChamada() {
        footballTestSupport.inserirSelecao(tipId, null, "PENDING");

        SyncOutcome resultado = footballSyncService.sincronizarResultadosPendentes();

        assertThat(resultado.chamadasEfetuadas()).isEqualTo(0);
        assertThat(fake.contarChamadas()).isEqualTo(0);
    }

    @Test
    void grupoSincronizadoRecentementeNaoERepetidoNoCicloSeguinte() {
        Instant kickoff = clock.instant().minus(Duration.ofHours(3));
        LigaDeTeste liga = criarLiga();
        FixtureDeTeste jogo = criarJogo(liga.id(), kickoff, "SCHEDULED");
        footballTestSupport.inserirSelecaoPendente(tipId, jogo.id());
        footballTestSupport.definirUltimaSincronizacao(jogo.id(), clock.instant().minus(Duration.ofMinutes(5)));

        SyncOutcome resultado = footballSyncService.sincronizarResultadosPendentes();

        assertThat(resultado.chamadasEfetuadas()).isEqualTo(0);
        assertThat(fake.contarChamadas()).isEqualTo(0);
    }

    @Test
    void jogoForaDaJanelaDeResultadosDeixaDeGerarChamadas() {
        Instant kickoff = clock.instant().minus(Duration.ofDays(10));
        LigaDeTeste liga = criarLiga();
        FixtureDeTeste jogo = criarJogo(liga.id(), kickoff, "SCHEDULED");
        footballTestSupport.inserirSelecaoPendente(tipId, jogo.id());

        SyncOutcome resultado = footballSyncService.sincronizarResultadosPendentes();

        assertThat(resultado.chamadasEfetuadas()).isEqualTo(0);
        assertThat(fake.contarChamadas()).isEqualTo(0);
    }

    @Test
    void aSincronizacaoNuncaAlteraSelecoesNemTips() {
        Instant kickoff = clock.instant().minus(Duration.ofHours(3));
        LigaDeTeste liga = criarLiga();
        FixtureDeTeste jogo = criarJogo(liga.id(), kickoff, "SCHEDULED");
        long selecaoId = footballTestSupport.inserirSelecaoPendente(tipId, jogo.id());
        fake.definirResultado(liga.providerId(), 2026, dataUtc(kickoff), jogo.providerId(),
                FixtureStatus.FINISHED, 3, 0);

        footballSyncService.sincronizarResultadosPendentes();

        assertThat(footballTestSupport.estadosDasSelecoes(tipId)).containsOnly("PENDING");
        assertThat(footballTestSupport.lerColunaDaTip(tipId, "status")).isEqualTo("PENDING");
        assertThat(selecaoId).isPositive();
    }

    private LigaDeTeste criarLiga() {
        long providerId = contadorId.incrementAndGet();
        long id = footballTestSupport.inserirLiga(providerId, "Liga de Teste " + providerId, 2026);
        ligasCriadas.add(id);
        return new LigaDeTeste(id, providerId);
    }

    private long criarEquipa() {
        long providerId = contadorId.incrementAndGet();
        long id = footballTestSupport.inserirEquipa(providerId, "Equipa " + providerId, "equipa " + providerId);
        equipasCriadas.add(id);
        return id;
    }

    private FixtureDeTeste criarJogo(long ligaId, Instant kickoffAt, String status) {
        long homeId = criarEquipa();
        long awayId = criarEquipa();
        long providerId = contadorId.incrementAndGet();
        long id = footballTestSupport.inserirJogo(providerId, ligaId, homeId, awayId, kickoffAt, status);
        jogosCriados.add(id);
        return new FixtureDeTeste(id, providerId);
    }

    private static java.time.LocalDate dataUtc(Instant instante) {
        return instante.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private record LigaDeTeste(long id, long providerId) {
    }

    private record FixtureDeTeste(long id, long providerId) {
    }
}
