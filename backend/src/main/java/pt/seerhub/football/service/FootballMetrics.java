package pt.seerhub.football.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

import pt.seerhub.config.SeerHubProperties;

/**
 * Consumo diário de chamadas exposto numa métrica (R5, critério 9), para
 * caber no limite do plano contratado. {@code spring-boot-starter-actuator}
 * já traz o Micrometer — nenhuma dependência nova.
 */
@Component
public class FootballMetrics {

    public static final String METRICA_CHAMADAS_HOJE = "seerhub.football.api.calls.today";
    public static final String METRICA_ORCAMENTO_RESTANTE = "seerhub.football.api.budget.remaining";
    public static final String METRICA_CHAMADAS_TOTAL = "seerhub.football.api.calls.total";

    private final ApiCallBudgetService budgetService;
    private final SeerHubProperties properties;
    private final Counter contadorSucesso;
    private final Counter contadorFalha;
    private final Counter contadorSemOrcamento;

    public FootballMetrics(MeterRegistry meterRegistry, ApiCallBudgetService budgetService, SeerHubProperties properties) {
        this.budgetService = budgetService;
        this.properties = properties;

        Gauge.builder(METRICA_CHAMADAS_HOJE, budgetService, ApiCallBudgetService::consumidoHoje)
                .description("Número de chamadas à API-Football já feitas hoje (dia UTC).")
                .register(meterRegistry);

        Gauge.builder(METRICA_ORCAMENTO_RESTANTE, this, FootballMetrics::orcamentoRestante)
                .description("Quantas chamadas ainda restam hoje até ao orçamento diário.")
                .register(meterRegistry);

        this.contadorSucesso = Counter.builder(METRICA_CHAMADAS_TOTAL).tag("outcome", "sucesso").register(meterRegistry);
        this.contadorFalha = Counter.builder(METRICA_CHAMADAS_TOTAL).tag("outcome", "falha").register(meterRegistry);
        this.contadorSemOrcamento = Counter.builder(METRICA_CHAMADAS_TOTAL).tag("outcome", "sem_orcamento").register(meterRegistry);
    }

    public void registarSucesso() {
        contadorSucesso.increment();
    }

    public void registarFalha() {
        contadorFalha.increment();
    }

    public void registarSemOrcamento() {
        contadorSemOrcamento.increment();
    }

    private double orcamentoRestante() {
        int limite = properties.football() != null ? properties.football().orcamentoDiario() : 100;
        return budgetService.restanteHoje(limite);
    }
}
