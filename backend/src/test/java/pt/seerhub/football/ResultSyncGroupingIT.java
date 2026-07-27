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

import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.football.domain.FixtureStatus;
import pt.seerhub.football.provider.FixedFootballDataProvider;
import pt.seerhub.football.provider.ProviderCall;
import pt.seerhub.football.service.FootballSyncService;
import pt.seerhub.football.service.SyncOutcome;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.AuthTestSupport;
import pt.seerhub.support.CommunityTestSupport;
import pt.seerhub.support.FootballTestSupport;
import pt.seerhub.user.api.AuthResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O teste que a spec exige por escrito (R5, critério 4, §3.1 do plano): N
 * seleções pendentes em M jogos da mesma liga e do mesmo dia custam UMA
 * chamada, não M. A asserção que carrega a feature.
 */
class ResultSyncGroupingIT extends AbstractIntegrationTest {

    private static final long LIGA_PROVIDER_ID = 39L;
    private static final int EPOCA = 2026;

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

    private FootballTestSupport footballTestSupport;
    private CommunityTestSupport communityTestSupport;
    private AuthTestSupport authTestSupport;

    private long tipId;
    private final AtomicLong contadorId = new AtomicLong(39_100_000L + (System.nanoTime() % 90_000L));
    private final List<Long> jogosCriados = new ArrayList<>();
    private final List<Long> equipasCriadas = new ArrayList<>();
    private long ligaId;
    private long tipCriada;

    @BeforeEach
    void setUp() throws Exception {
        footballTestSupport = new FootballTestSupport(jdbcTemplate);
        communityTestSupport = new CommunityTestSupport(mockMvc, objectMapper, jdbcTemplate);
        authTestSupport = new AuthTestSupport(mockMvc, objectMapper, jdbcTemplate, properties);

        footballTestSupport.limparOrcamento();
        fake.limparRegisto();

        AuthResponse auth = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("football-grouping"), "password12345");
        var community = communityTestSupport.criar(auth.accessToken(), communityTestSupport.nomeUnico("liga-grp"));
        tipId = footballTestSupport.inserirTip(community.id(), auth.user().id(), "PENDING");
        tipCriada = tipId;

        ligaId = footballTestSupport.inserirLiga(LIGA_PROVIDER_ID, "Premier League", EPOCA);
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
        // A liga 39 é partilhada com outras classes (ex.: FixtureCatalogSyncIT); nunca apagada aqui.
    }

    @Test
    void cincoSelecoesPendentesEmTresJogosDaMesmaLigaEDiaCustamUmaSoChamada() {
        LocalDate hoje = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate dia = hoje.minusDays(1);

        long jogo1 = criarJogo(dia.atTime(14, 0));
        long jogo2 = criarJogo(dia.atTime(16, 30));
        long jogo3 = criarJogo(dia.atTime(19, 0));

        // 5 seleções pendentes distribuídas 2/2/1 pelos três jogos.
        footballTestSupport.inserirSelecaoPendente(tipId, jogo1);
        footballTestSupport.inserirSelecaoPendente(tipId, jogo1);
        footballTestSupport.inserirSelecaoPendente(tipId, jogo2);
        footballTestSupport.inserirSelecaoPendente(tipId, jogo2);
        footballTestSupport.inserirSelecaoPendente(tipId, jogo3);

        registarResultadoNoFornecedor(dia, providerIdDoJogo(jogo1), FixtureStatus.FINISHED, 2, 1);
        registarResultadoNoFornecedor(dia, providerIdDoJogo(jogo2), FixtureStatus.FINISHED, 0, 0);
        registarResultadoNoFornecedor(dia, providerIdDoJogo(jogo3), FixtureStatus.FINISHED, 1, 3);

        SyncOutcome resultado = footballSyncService.sincronizarResultadosPendentes();

        assertThat(fake.chamadas()).hasSize(1);
        assertThat(fake.chamadas().get(0)).isEqualTo(
                new ProviderCall(ProviderCall.Tipo.JOGOS_DO_DIA, LIGA_PROVIDER_ID, EPOCA, dia, dia));
        assertThat(resultado.chamadasEfetuadas()).isEqualTo(1);
        assertThat(footballTestSupport.consumoDoDia()).isEqualTo(1);

        for (long jogoId : List.of(jogo1, jogo2, jogo3)) {
            Map<String, Object> jogo = footballTestSupport.lerJogo(jogoId);
            assertThat(jogo.get("status")).isEqualTo("FINISHED");
            assertThat(jogo.get("last_synced_at")).isNotNull();
        }
        assertThat(footballTestSupport.estadosDasSelecoes(tipId)).containsOnly("PENDING");
    }

    @Test
    void diasDistintosOuLigasDistintasCustamUmaChamadaCadaUm() {
        LocalDate hoje = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate diaA = hoje.minusDays(1);
        LocalDate diaB = hoje.minusDays(2);

        long jogoDiaA1 = criarJogo(diaA.atTime(14, 0));
        long jogoDiaA2 = criarJogo(diaA.atTime(16, 0));
        long jogoDiaB = criarJogo(diaB.atTime(14, 0));

        long outraLigaId = footballTestSupport.inserirLiga(140L, "La Liga", EPOCA);
        long jogoOutraLiga = criarJogo(outraLigaId, 140L, diaA.atTime(14, 0));

        footballTestSupport.inserirSelecaoPendente(tipId, jogoDiaA1);
        footballTestSupport.inserirSelecaoPendente(tipId, jogoDiaA2);
        footballTestSupport.inserirSelecaoPendente(tipId, jogoDiaB);
        footballTestSupport.inserirSelecaoPendente(tipId, jogoOutraLiga);

        registarResultadoNoFornecedor(diaA, providerIdDoJogo(jogoDiaA1), FixtureStatus.FINISHED, 1, 0);
        registarResultadoNoFornecedor(diaA, providerIdDoJogo(jogoDiaA2), FixtureStatus.FINISHED, 2, 2);
        registarResultadoNoFornecedor(diaB, providerIdDoJogo(jogoDiaB), FixtureStatus.FINISHED, 0, 1);
        fake.definirResultado(140L, EPOCA, diaA, providerIdDoJogo(jogoOutraLiga), FixtureStatus.FINISHED, 3, 1);

        SyncOutcome resultado = footballSyncService.sincronizarResultadosPendentes();

        assertThat(fake.chamadas()).hasSize(3);
        assertThat(resultado.chamadasEfetuadas()).isEqualTo(3);
    }

    @Test
    void aChamadaIdentificaLigaEpocaEDiaENuncaUmJogoIndividual() {
        LocalDate hoje = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate dia = hoje.minusDays(3);

        long jogo = criarJogo(dia.atTime(20, 0));
        footballTestSupport.inserirSelecaoPendente(tipId, jogo);
        registarResultadoNoFornecedor(dia, providerIdDoJogo(jogo), FixtureStatus.FINISHED, 1, 1);

        footballSyncService.sincronizarResultadosPendentes();

        assertThat(fake.chamadas()).hasSize(1);
        ProviderCall chamada = fake.chamadas().get(0);
        assertThat(chamada.tipo()).isEqualTo(ProviderCall.Tipo.JOGOS_DO_DIA);
        assertThat(chamada.ligaProviderId()).isEqualTo(LIGA_PROVIDER_ID);
        assertThat(chamada.epoca()).isEqualTo(EPOCA);
        assertThat(chamada.de()).isEqualTo(dia);
        assertThat(chamada.ate()).isEqualTo(dia);
    }

    private void registarResultadoNoFornecedor(LocalDate dia, long fixtureProviderId, FixtureStatus status,
            int homeScore, int awayScore) {
        fake.definirResultado(LIGA_PROVIDER_ID, EPOCA, dia, fixtureProviderId, status, homeScore, awayScore);
    }

    private long criarJogo(java.time.LocalDateTime kickoffLocal) {
        return criarJogo(ligaId, LIGA_PROVIDER_ID, kickoffLocal);
    }

    private long criarJogo(long ligaIdLocal, long ligaProviderIdLocal, java.time.LocalDateTime kickoffLocal) {
        Instant kickoffAt = kickoffLocal.toInstant(ZoneOffset.UTC);
        // Confirma que o jogo começou há mais de 2h (pré-condição de todos os cenários deste teste).
        assertThat(Duration.between(kickoffAt, clock.instant())).isGreaterThan(Duration.ofHours(2));

        long homeId = criarEquipa();
        long awayId = criarEquipa();
        long fixtureProviderId = contadorId.incrementAndGet();
        long id = footballTestSupport.inserirJogo(fixtureProviderId, ligaIdLocal, homeId, awayId, kickoffAt, "SCHEDULED");
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
}
