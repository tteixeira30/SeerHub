package pt.seerhub.football.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.football.provider.ApiFootballDataProvider;
import pt.seerhub.football.provider.FixedFootballDataProvider;
import pt.seerhub.football.provider.FootballDataProvider;

/**
 * Escolha da implementação de {@link FootballDataProvider} por propriedade
 * (R5, critério 7): {@code seerhub.football.provider = api-football} (perfil
 * por omissão) ou {@code fake} (perfil de teste — {@code application-test.yml}).
 *
 * <p>{@code backend/pom.xml} não ganha nenhuma dependência nova:
 * {@code RestClient} já vem de {@code spring-boot-starter-web} (Spring
 * Framework 6.1).
 */
@Configuration
public class FootballProviderConfig {

    private static final String BASE_URL_OMISSAO = "https://v3.football.api-sports.io";

    @Bean
    public RestClient footballRestClient(SeerHubProperties properties) {
        SeerHubProperties.Football football = properties.football();
        Duration timeout = football != null ? football.timeoutDoPedido() : Duration.ofSeconds(10);
        String baseUrl = football != null && football.baseUrl() != null ? football.baseUrl() : BASE_URL_OMISSAO;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) timeout.toMillis());
        requestFactory.setReadTimeout((int) timeout.toMillis());

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seerhub.football", name = "provider", havingValue = "api-football", matchIfMissing = true)
    public FootballDataProvider apiFootballDataProvider(RestClient footballRestClient, SeerHubProperties properties) {
        return new ApiFootballDataProvider(footballRestClient, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "seerhub.football", name = "provider", havingValue = "fake")
    public FixedFootballDataProvider fixedFootballDataProvider(Clock clock) {
        return new FixedFootballDataProvider(clock);
    }
}
