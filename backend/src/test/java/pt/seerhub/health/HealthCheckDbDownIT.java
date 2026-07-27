package pt.seerhub.health;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * X1: com a base de dados em baixo, o health check devolve 503 com o
 * componente "db" a DOWN, e a aplicação não rebenta (continua de pé,
 * capaz de responder ao próprio health check).
 *
 * <p>Esta classe declara o seu próprio contentor (não usa o partilhado de
 * {@code AbstractIntegrationTest}) porque vai parti-lo deliberadamente; usar
 * o contentor partilhado destruiria o ambiente das restantes classes de
 * teste.
 *
 * <p>D3 do plano de F01: como {@code show-details} passou a
 * {@code when-authorized}, o pedido precisa de estar autenticado para ver
 * o componente {@code db}. Não pode autenticar-se com um access token real
 * (o login precisa da base de dados, que está propositadamente parada):
 * usa-se {@code .with(user(...))} do {@code spring-security-test} para
 * simular um principal autenticado só dentro do MockMvc, sem tocar na
 * base de dados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HealthCheckDbDownIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void comBaseDeDadosParadaHealthDevolve503EComponenteDbDown() throws Exception {
        POSTGRES.stop();

        mockMvc.perform(get("/actuator/health").with(user("saude")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.db.status").value("DOWN"));
    }
}
