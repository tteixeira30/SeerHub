package pt.seerhub.football.service;

/**
 * Resultado de uma execução de sincronização (catálogo ou resultados),
 * devolvido por {@link FootballSyncService}. Nunca lança exceção para fora —
 * falhas do fornecedor entram em {@link #falhas()}.
 */
public record SyncOutcome(
        int gruposConsiderados,
        int chamadasEfetuadas,
        int gruposIgnoradosPorOrcamento,
        int jogosAtualizados,
        int falhas,
        boolean ignoradoPorConcorrencia) {

    public static SyncOutcome semTrabalho() {
        return new SyncOutcome(0, 0, 0, 0, 0, false);
    }

    /** Uma segunda execução concorrente encontrou o lock tomado — devolve de imediato, sem consumir orçamento. */
    public static SyncOutcome ignorado() {
        return new SyncOutcome(0, 0, 0, 0, 0, true);
    }
}
