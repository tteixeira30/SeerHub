package pt.seerhub.football.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarefa agendada do catálogo (R5, critério 1a/1b). Cron configurável por
 * {@code seerhub.football.catalog-cron}, com {@link #CRON_POR_OMISSAO} como
 * valor por omissão (00:07 e 12:07 UTC — 12 em 12 horas). No perfil
 * {@code test} a propriedade é {@code "-"}: a tarefa nunca dispara sozinha
 * durante a suite.
 *
 * <p>Mesmo padrão exato de {@code MembershipExpiryTask} (F03): delega
 * inteiramente no serviço e engole qualquer exceção com {@code log.warn} —
 * um {@code @Scheduled} que rebenta mata o agendador inteiro (caso de
 * fronteira X3).
 */
@Component
public class FixtureCatalogSyncTask {

    public static final String CRON_POR_OMISSAO = "0 7 */12 * * *";

    private static final Logger log = LoggerFactory.getLogger(FixtureCatalogSyncTask.class);

    private final FootballSyncService footballSyncService;

    public FixtureCatalogSyncTask(FootballSyncService footballSyncService) {
        this.footballSyncService = footballSyncService;
    }

    @Scheduled(cron = "${seerhub.football.catalog-cron:" + CRON_POR_OMISSAO + "}", zone = "UTC")
    public void correr() {
        try {
            SyncOutcome resultado = footballSyncService.sincronizarCatalogo();
            log.info("Sincronização do catálogo concluída: {} chamada(s), {} jogo(s) atualizados, {} falha(s).",
                    resultado.chamadasEfetuadas(), resultado.jogosAtualizados(), resultado.falhas());
        } catch (Exception ex) {
            log.warn("Falha ao correr a tarefa de sincronização do catálogo de futebol.", ex);
        }
    }
}
