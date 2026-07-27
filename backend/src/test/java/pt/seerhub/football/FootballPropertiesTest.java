package pt.seerhub.football;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import pt.seerhub.config.SeerHubProperties;

import static org.assertj.core.api.Assertions.assertThat;

/** R5, critério 3a/3b — mesmo estilo de {@code SeerHubPropertiesTest}. */
class FootballPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracaoDeTeste.class);

    @Test
    void oOrcamentoDiarioVemDaVariavelDeAmbienteComOmissaoDeCem() {
        runner.withPropertyValues(
                "seerhub.football.api-key=chave-de-teste",
                "seerhub.football.daily-budget=250"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            SeerHubProperties properties = context.getBean(SeerHubProperties.class);
            assertThat(properties.football().orcamentoDiario()).isEqualTo(250);
        });

        runner.withPropertyValues("seerhub.football.api-key=chave-de-teste")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SeerHubProperties properties = context.getBean(SeerHubProperties.class);
                    assertThat(properties.football().orcamentoDiario()).isEqualTo(100);
                });
    }

    @Test
    void orcamentoVazioCaiNaOmissaoSemImpedirOArranque() {
        runner.withPropertyValues(
                "seerhub.football.api-key=chave-de-teste",
                "seerhub.football.daily-budget="
        ).run(context -> {
            assertThat(context).hasNotFailed();
            SeerHubProperties properties = context.getBean(SeerHubProperties.class);
            assertThat(properties.football().orcamentoDiario()).isEqualTo(100);
        });
    }

    @EnableConfigurationProperties(SeerHubProperties.class)
    static class ConfiguracaoDeTeste {
    }
}
