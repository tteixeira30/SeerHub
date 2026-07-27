package pt.seerhub.config;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E3: as propriedades tipadas ligam corretamente quando o ambiente está
 * completo. X2: falta do segredo JWT impede o arranque com uma mensagem
 * que nomeia a propriedade em falta.
 */
class SeerHubPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracaoDeTeste.class);

    @Test
    void ligaTodasAsPropriedadesQuandoOAmbienteEstaCompleto() {
        runner.withPropertyValues(
                "seerhub.security.jwt-secret=segredo-de-teste-bem-comprido-32",
                "seerhub.security.access-ttl=PT15M",
                "seerhub.security.refresh-ttl=P30D",
                "seerhub.football.api-key=chave-de-teste",
                "seerhub.football.base-url=http://localhost:9999",
                "seerhub.uploads.dir=/tmp/seerhub-uploads"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            SeerHubProperties properties = context.getBean(SeerHubProperties.class);

            assertThat(properties.security().jwtSecret()).isEqualTo("segredo-de-teste-bem-comprido-32");
            assertThat(properties.security().accessTtl()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.security().refreshTtl()).isEqualTo(Duration.ofDays(30));
            assertThat(properties.football().apiKey()).isEqualTo("chave-de-teste");
            assertThat(properties.football().baseUrl()).isEqualTo("http://localhost:9999");
            assertThat(properties.uploads().dir()).isEqualTo(Path.of("/tmp/seerhub-uploads"));
        });
    }

    @Test
    void faltaDeJwtSecretImpedeOArranqueComMensagemQueNomeiaAPropriedade() {
        // Em application.yml real, access-ttl e refresh-ttl têm sempre um valor
        // resolvido (por omissão), só jwt-secret é obrigatório sem omissão.
        // Reproduz-se aqui o mesmo desenho: o grupo "security" existe, só a
        // propriedade do segredo falta.
        runner.withPropertyValues(
                "seerhub.security.access-ttl=PT15M",
                "seerhub.security.refresh-ttl=P30D",
                "seerhub.football.api-key=chave-de-teste"
        ).run(context -> {
            assertThat(context).hasFailed();

            String tracoCompleto = tracoDeErroCompleto(context.getStartupFailure());
            String tracoEmMinusculas = tracoCompleto.toLowerCase();

            assertThat(tracoEmMinusculas).containsAnyOf("jwtsecret", "jwt-secret");
        });
    }

    private static String tracoDeErroCompleto(Throwable erro) {
        StringBuilder builder = new StringBuilder();
        Throwable atual = erro;
        int voltas = 0;
        while (atual != null && voltas < 20) {
            builder.append(atual).append(" | ");
            for (var elementoStack : atual.getSuppressed()) {
                builder.append(elementoStack).append(" | ");
            }
            atual = atual.getCause();
            voltas++;
        }
        return builder.toString();
    }

    @EnableConfigurationProperties(SeerHubProperties.class)
    static class ConfiguracaoDeTeste {
    }
}
