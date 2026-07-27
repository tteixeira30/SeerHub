package pt.seerhub.football;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import pt.seerhub.football.repo.ApiCallBudgetRepository;
import pt.seerhub.football.service.ApiCallBudgetService;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.FootballTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O alicerce da feature (passo 1 do plano de F05): a reserva atómica do
 * orçamento diário. É o único sítio onde uma corrida de concorrência pode
 * existir, por isso é o primeiro a ficar verde, antes de mais uma linha de
 * código ser escrita.
 */
class ApiCallBudgetIT extends AbstractIntegrationTest {

    @Autowired
    private ApiCallBudgetService budgetService;

    @Autowired
    private ApiCallBudgetRepository budgetRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private FootballTestSupport footballTestSupport;

    @BeforeEach
    void setUp() {
        footballTestSupport = new FootballTestSupport(jdbcTemplate);
        footballTestSupport.limparOrcamento();
    }

    @Test
    void comLimiteDefinidoSaoConcedidasExatamenteNReservasEASeguinteERecusada() {
        int limite = 5;
        int concedidas = 0;
        for (int i = 0; i < 5; i++) {
            if (budgetService.tentarReservar(limite)) {
                concedidas++;
            }
        }
        boolean sexta = budgetService.tentarReservar(limite);

        assertThat(concedidas).isEqualTo(5);
        assertThat(sexta).isFalse();
        assertThat(budgetService.consumidoHoje()).isEqualTo(5);
        assertThat(footballTestSupport.consumoDoDia()).isEqualTo(5);
    }

    @Test
    void oContadorSobreviveAReinicioPorqueViveNaBaseDeDados() {
        budgetService.tentarReservar(10);
        budgetService.tentarReservar(10);
        budgetService.tentarReservar(10);

        // Equivalente testável de um reinício de processo: instância nova,
        // mesmo repositório (a base de dados, não a JVM).
        ApiCallBudgetService novaInstancia = new ApiCallBudgetService(budgetRepository, Clock.systemUTC());

        assertThat(novaInstancia.consumidoHoje()).isEqualTo(3);
        assertThat(novaInstancia.tentarReservar(3)).isFalse();
    }

    @Test
    void oConsumoDeOntemNaoContaParaHoje() {
        LocalDate ontem = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        footballTestSupport.definirConsumo(ontem, 100);

        assertThat(budgetService.consumidoHoje()).isEqualTo(0);
        assertThat(budgetService.tentarReservar(1)).isTrue();
        assertThat(footballTestSupport.consumoDoDia(ontem)).isEqualTo(100);
    }

    @Test
    void reservasConcorrentesNuncaUltrapassamOTeto() throws InterruptedException {
        int limite = 50;
        int threads = 32;
        int tentativasPorThread = 200 / threads + 1;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch partida = new CountDownLatch(1);
        AtomicInteger sucessos = new AtomicInteger();

        List<Runnable> tarefas = new java.util.ArrayList<>();
        for (int t = 0; t < threads; t++) {
            tarefas.add(() -> {
                try {
                    partida.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int i = 0; i < tentativasPorThread; i++) {
                    if (budgetService.tentarReservar(limite)) {
                        sucessos.incrementAndGet();
                    }
                }
            });
        }
        tarefas.forEach(executor::submit);
        partida.countDown();
        executor.shutdown();
        executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(sucessos.get()).isEqualTo(50);
        assertThat(footballTestSupport.consumoDoDia()).isEqualTo(50);
    }

    @Test
    void aTabelaDoOrcamentoTemUmaLinhaPorDiaComChavePrimaria() {
        LocalDate dia = LocalDate.now(ZoneOffset.UTC);
        budgetService.tentarReservar(10);
        budgetService.tentarReservar(10);

        // Uma só linha por dia — a chave primária impede duplicação; se não
        // existisse, o ON CONFLICT (day) do repositório falharia a compilar
        // a query no Postgres (sem índice/constraint única sobre "day").
        Integer linhas = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM api_call_budget WHERE day = ?", Integer.class, dia);
        assertThat(linhas).isEqualTo(1);

        Boolean temChavePrimaria = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.table_constraints tc "
                        + "JOIN information_schema.key_column_usage kcu "
                        + "  ON tc.constraint_name = kcu.constraint_name "
                        + " WHERE tc.table_name = 'api_call_budget' "
                        + "   AND tc.constraint_type = 'PRIMARY KEY' "
                        + "   AND kcu.column_name = 'day')",
                Boolean.class);
        assertThat(temChavePrimaria).isTrue();

        Instant atualizadoEm = budgetRepository.atualizadoEm(dia);
        assertThat(atualizadoEm).isNotNull();
    }
}
