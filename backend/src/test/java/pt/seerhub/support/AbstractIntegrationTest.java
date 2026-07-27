package pt.seerhub.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base de todos os testes de integração ({@code *IT}).
 *
 * <p>O contentor Postgres é estático e partilhado entre todas as subclasses:
 * arranca uma vez, num bloco estático, e nunca é parado explicitamente — o
 * Ryuk do Testcontainers trata da limpeza no fim da JVM. Isto significa que
 * as 16 features seguintes pagam o custo de arranque do Postgres uma vez por
 * execução da suite, não uma vez por classe de teste.
 *
 * <p>Testes que precisam de simular a base de dados em baixo (ex.:
 * {@code HealthCheckDbDownIT}) NÃO estendem esta classe — declaram o seu
 * próprio contentor descartável.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        POSTGRES.start();
    }
}
