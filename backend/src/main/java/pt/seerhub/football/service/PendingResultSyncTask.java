package pt.seerhub.football.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarefa agendada de resultados a pedido (R5, critério 2a). Cron
 * configurável por {@code seerhub.football.result-cron}, com
 * {@link #CRON_POR_OMISSAO} como valor por omissão (a cada 15 minutos). No
 * perfil {@code test} a propriedade é {@code "-"}.
 *
 * <p>Mesmo padrão exato de {@code MembershipExpiryTask}/{@code FixtureCatalogSyncTask}:
 * delega e engole qualquer exceção (X3).
 */
@Component
public class PendingResultSyncTask {

    public static final String CRON_POR_OMISSAO = "0 */15 * * * *";

    private static final Logger log = LoggerFactory.getLogger(PendingResultSyncTask.class);

    private final FootballSyncService footballSyncService;

    public PendingResultSyncTask(FootballSyncService footballSyncService) {
        this.footballSyncService = footballSyncService;
    }

    @Scheduled(cron = "${seerhub.football.result-cron:" + CRON_POR_OMISSAO + "}", zone = "UTC")
    public void correr() {
        try {
            SyncOutcome resultado = footballSyncService.sincronizarResultadosPendentes();
            log.info("Sincronização de resultados concluída: {} grupo(s) considerado(s), {} chamada(s), "
                            + "{} ignorado(s) por orçamento.",
                    resultado.gruposConsiderados(), resultado.chamadasEfetuadas(), resultado.gruposIgnoradosPorOrcamento());
        } catch (Exception ex) {
            log.warn("Falha ao correr a tarefa de sincronização de resultados de futebol.", ex);
        }
    }
}
