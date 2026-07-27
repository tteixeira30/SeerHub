package pt.seerhub.football.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;

import pt.seerhub.football.repo.ApiCallBudgetRepository;

/**
 * Porta de serviço sobre {@link ApiCallBudgetRepository} — o dia é sempre o
 * dia UTC do {@link Clock} injetado (nunca {@code LocalDate.now()} sem
 * relógio, regra herdada de F00/F03).
 *
 * <p>Construída sem depender de contexto Spring para poder ser instanciada
 * diretamente em teste (3e: "o equivalente testável de um reinício de
 * processo" é construir uma instância nova com o mesmo repositório).
 */
@Service
public class ApiCallBudgetService {

    private final ApiCallBudgetRepository repository;
    private final Clock clock;

    public ApiCallBudgetService(ApiCallBudgetRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Tenta reservar uma unidade de orçamento para hoje (dia UTC), até {@code limite}. */
    public boolean tentarReservar(int limite) {
        return repository.tentarReservar(hoje(), limite);
    }

    /** Consumo já registado hoje (dia UTC). */
    public int consumidoHoje() {
        return repository.consumidasEm(hoje());
    }

    /** Quanto ainda resta hoje, dado o limite indicado; nunca negativo. */
    public int restanteHoje(int limite) {
        return Math.max(0, limite - consumidoHoje());
    }

    /**
     * Esgota o dia por sinal autoritativo do próprio fornecedor (429, ou
     * {@code x-ratelimit-requests-remaining: 0}) — mais autoritativo do que
     * a nossa própria contagem (§2.4-B do plano).
     */
    public void esgotarDiaPorSinalDoFornecedor(int limite) {
        repository.esgotarDia(hoje(), limite);
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }
}
