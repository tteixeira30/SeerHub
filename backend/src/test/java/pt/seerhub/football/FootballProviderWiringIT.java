package pt.seerhub.football;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import pt.seerhub.football.provider.ApiFootballDataProvider;
import pt.seerhub.football.provider.FixedFootballDataProvider;
import pt.seerhub.football.provider.FootballDataProvider;
import pt.seerhub.support.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/** R5, critério 7a. */
class FootballProviderWiringIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private FootballDataProvider footballDataProvider;

    @Test
    void existemDuasImplementacoesDoFornecedorEOContextoDeTesteSoTemADeDadosFixos() {
        // Duas implementações existem como classes de produção.
        assertThat(ApiFootballDataProvider.class).isNotNull();
        assertThat(FixedFootballDataProvider.class).isNotNull();

        // Mas o contexto de teste (perfil "fake") só regista uma.
        String[] nomesDeBeans = applicationContext.getBeanNamesForType(FootballDataProvider.class);
        assertThat(nomesDeBeans).hasSize(1);
        assertThat(footballDataProvider).isInstanceOf(FixedFootballDataProvider.class);

        assertThat(applicationContext.getBeanNamesForType(ApiFootballDataProvider.class)).isEmpty();
    }
}
