package pt.seerhub.community;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import pt.seerhub.community.service.MembershipExpiryTask;
import pt.seerhub.community.service.SubscriptionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Critério 3 (parte unitária) — R3, sem contexto Spring. Usa
 * {@code Mockito.mock} (já vem em {@code spring-boot-starter-test}, sem
 * dependência nova — risco em aberto 4 do plano de F03).
 */
class MembershipExpiryTaskTest {

    @Test
    void oCronPorOmissaoDisparaUmaVezPorDia() {
        CronExpression cron = CronExpression.parse(MembershipExpiryTask.CRON_POR_OMISSAO);
        ZonedDateTime referencia = ZonedDateTime.ofInstant(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC);

        ZonedDateTime primeira = cron.next(referencia);
        ZonedDateTime segunda = cron.next(primeira);

        assertThat(primeira).isNotNull();
        assertThat(segunda).isNotNull();
        assertThat(Duration.between(primeira, segunda)).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void oMetodoEstaAgendadoComCronConfiguravelEmUtc() throws NoSuchMethodException {
        Method metodo = MembershipExpiryTask.class.getMethod("correr");
        Scheduled scheduled = metodo.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.zone()).isEqualTo("UTC");
        assertThat(scheduled.cron())
                .isEqualTo("${seerhub.subscriptions.expiry-cron:" + MembershipExpiryTask.CRON_POR_OMISSAO + "}");
    }

    @Test
    void correrDelegaNoServicoEEngoleFalhas() {
        SubscriptionService servicoDuplo = mock(SubscriptionService.class);
        when(servicoDuplo.expirarMembershipsVencidas()).thenThrow(new RuntimeException("falha simulada"));

        MembershipExpiryTask tarefa = new MembershipExpiryTask(servicoDuplo);

        assertThatCode(tarefa::correr).doesNotThrowAnyException();
        verify(servicoDuplo, times(1)).expirarMembershipsVencidas();
    }
}
