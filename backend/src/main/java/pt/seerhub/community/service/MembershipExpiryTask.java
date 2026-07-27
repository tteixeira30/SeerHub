package pt.seerhub.community.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A tarefa diária (R3, critério 3): marca {@code EXPIRED} toda a membership
 * {@code ACTIVE}/{@code CANCELLED} cuja {@code expiresAt} já passou. Delega
 * inteiramente em {@link SubscriptionService#expirarMembershipsVencidas()} —
 * é o mesmo método que os testes de integração invocam diretamente (D-14,
 * D-15 do plano de F03).
 *
 * <p>Cron configurável por {@code seerhub.subscriptions.expiry-cron}, com
 * {@link #CRON_POR_OMISSAO} como valor por omissão (03:15 UTC). No perfil
 * {@code test} a propriedade é {@code "-"} ({@code Scheduled.CRON_DISABLED}):
 * a tarefa nunca dispara sozinha durante a suite.
 */
@Component
public class MembershipExpiryTask {

    /** 03:15 UTC — longe do pico e depois da meia-noite em Portugal continental. */
    public static final String CRON_POR_OMISSAO = "0 15 3 * * *";

    private static final Logger log = LoggerFactory.getLogger(MembershipExpiryTask.class);

    private final SubscriptionService subscriptionService;

    public MembershipExpiryTask(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /** D-15: captura e regista qualquer exceção — um {@code @Scheduled} que rebenta não mata o agendador. */
    @Scheduled(cron = "${seerhub.subscriptions.expiry-cron:" + CRON_POR_OMISSAO + "}", zone = "UTC")
    public void correr() {
        try {
            int linhas = subscriptionService.expirarMembershipsVencidas();
            log.info("Expiração diária de memberships concluída: {} linha(s) afetada(s).", linhas);
        } catch (Exception ex) {
            log.warn("Falha ao correr a tarefa de expiração diária de memberships.", ex);
        }
    }
}
