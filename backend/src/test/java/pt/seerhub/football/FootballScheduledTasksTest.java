package pt.seerhub.football;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.football.provider.FootballDataProvider;
import pt.seerhub.football.repo.FixtureRepository;
import pt.seerhub.football.repo.LeagueRepository;
import pt.seerhub.football.repo.PendingResultRepository;
import pt.seerhub.football.repo.TeamRepository;
import pt.seerhub.football.service.ApiCallBudgetService;
import pt.seerhub.football.service.CrestCacheService;
import pt.seerhub.football.service.FixtureCatalogSyncTask;
import pt.seerhub.football.service.FootballMetrics;
import pt.seerhub.football.service.FootballSyncService;
import pt.seerhub.football.service.PendingResultSyncTask;
import pt.seerhub.football.service.SeasonResolver;
import pt.seerhub.football.service.SyncOutcome;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * R5, critérios 1a/1b/2a e casos de fronteira X3/X4. Sem contexto Spring —
 * {@code CronExpression} para a periodicidade e {@code Mockito.mock} para
 * isolar as tarefas do serviço real (nenhuma dependência nova: Mockito já
 * vem de {@code spring-boot-starter-test}).
 */
class FootballScheduledTasksTest {

    @Test
    void oCronDoCatalogoPorOmissaoDisparaDeDozeEmDozeHoras() {
        CronExpression cron = CronExpression.parse(FixtureCatalogSyncTask.CRON_POR_OMISSAO);
        LocalDateTime referencia = LocalDateTime.of(2026, 7, 27, 0, 0);

        LocalDateTime primeira = cron.next(referencia);
        LocalDateTime segunda = cron.next(primeira);

        assertThat(primeira).isNotNull();
        assertThat(segunda).isNotNull();
        assertThat(Duration.between(primeira, segunda)).isEqualTo(Duration.ofHours(12));
    }

    @Test
    void aTarefaDoCatalogoEstaAgendadaComCronConfiguravelEmUtc() throws NoSuchMethodException {
        Scheduled anotacao = FixtureCatalogSyncTask.class.getMethod("correr").getAnnotation(Scheduled.class);

        assertThat(anotacao).isNotNull();
        assertThat(anotacao.zone()).isEqualTo("UTC");
        assertThat(anotacao.cron())
                .contains("seerhub.football.catalog-cron")
                .contains(FixtureCatalogSyncTask.CRON_POR_OMISSAO);
    }

    @Test
    void oCronDeResultadosPorOmissaoDisparaDeQuinzeEmQuinzeMinutosEmUtc() throws NoSuchMethodException {
        CronExpression cron = CronExpression.parse(PendingResultSyncTask.CRON_POR_OMISSAO);
        LocalDateTime referencia = LocalDateTime.of(2026, 7, 27, 10, 1);

        LocalDateTime primeira = cron.next(referencia);
        LocalDateTime segunda = cron.next(primeira);

        assertThat(Duration.between(primeira, segunda)).isEqualTo(Duration.ofMinutes(15));

        Scheduled anotacao = PendingResultSyncTask.class.getMethod("correr").getAnnotation(Scheduled.class);
        assertThat(anotacao.zone()).isEqualTo("UTC");
        assertThat(anotacao.cron())
                .contains("seerhub.football.result-cron")
                .contains(PendingResultSyncTask.CRON_POR_OMISSAO);
    }

    @Test
    void asTarefasDelegamNoServicoEEngolemFalhas() {
        FootballSyncService servicoCatalogo = mock(FootballSyncService.class);
        when(servicoCatalogo.sincronizarCatalogo()).thenThrow(new RuntimeException("falha simulada"));
        FixtureCatalogSyncTask tarefaCatalogo = new FixtureCatalogSyncTask(servicoCatalogo);
        assertThatCode(tarefaCatalogo::correr).doesNotThrowAnyException();

        FootballSyncService servicoResultados = mock(FootballSyncService.class);
        when(servicoResultados.sincronizarResultadosPendentes()).thenThrow(new RuntimeException("falha simulada"));
        PendingResultSyncTask tarefaResultados = new PendingResultSyncTask(servicoResultados);
        assertThatCode(tarefaResultados::correr).doesNotThrowAnyException();
    }

    @Test
    void umaSegundaExecucaoConcorrenteEIgnoradaEmVezDeDuplicarConsumo() throws InterruptedException {
        FootballDataProvider provider = mock(FootballDataProvider.class);
        LeagueRepository leagueRepository = mock(LeagueRepository.class);
        TeamRepository teamRepository = mock(TeamRepository.class);
        FixtureRepository fixtureRepository = mock(FixtureRepository.class);
        PendingResultRepository pendingResultRepository = mock(PendingResultRepository.class);
        ApiCallBudgetService budgetService = mock(ApiCallBudgetService.class);
        SeasonResolver seasonResolver = mock(SeasonResolver.class);
        CrestCacheService crestCacheService = mock(CrestCacheService.class);
        FootballMetrics metrics = mock(FootballMetrics.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-27T10:00:00Z"), ZoneOffset.UTC);

        SeerHubProperties.Football.LeagueConfig ligaConfig =
                new SeerHubProperties.Football.LeagueConfig(39L, "Premier League", "England");
        SeerHubProperties.Football football = new SeerHubProperties.Football(
                null, null, null, 100, 20, 2026, null, null, null, null, null, null, null, List.of(ligaConfig));
        SeerHubProperties properties = new SeerHubProperties(null, football, null);

        when(budgetService.tentarReservar(anyInt())).thenReturn(true);
        when(seasonResolver.epocaAtual()).thenReturn(2026);
        when(teamRepository.idsDeLigasComEquipasSemNomeCurto()).thenReturn(List.of());

        CountDownLatch entrouNaChamada = new CountDownLatch(1);
        CountDownLatch podeContinuar = new CountDownLatch(1);
        when(provider.jogosDaLigaEntre(anyLong(), anyInt(), any(), any())).thenAnswer(invocation -> {
            entrouNaChamada.countDown();
            podeContinuar.await(5, TimeUnit.SECONDS);
            return List.of();
        });

        FootballSyncService service = new FootballSyncService(provider, leagueRepository, teamRepository,
                fixtureRepository, pendingResultRepository, budgetService, seasonResolver, crestCacheService,
                properties, clock, metrics);

        AtomicReference<SyncOutcome> primeiraExecucao = new AtomicReference<>();
        Thread thread = new Thread(() -> primeiraExecucao.set(service.sincronizarCatalogo()));
        thread.start();

        boolean entrou = entrouNaChamada.await(5, TimeUnit.SECONDS);
        assertThat(entrou).isTrue();

        SyncOutcome segundaExecucao = service.sincronizarCatalogo();

        podeContinuar.countDown();
        thread.join(5000);

        assertThat(segundaExecucao.ignoradoPorConcorrencia()).isTrue();
        assertThat(primeiraExecucao.get()).isNotNull();
        assertThat(primeiraExecucao.get().ignoradoPorConcorrencia()).isFalse();
    }
}
