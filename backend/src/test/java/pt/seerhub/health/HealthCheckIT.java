package pt.seerhub.health;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.AuthTestSupport;
import pt.seerhub.user.api.AuthResponse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R15.5a: o endpoint de health check devolve 200 e verifica a base de dados.
 *
 * <p>D1/D2 do plano de F01: dívida de F00 fechada —
 * {@code show-details: when-authorized}. Um pedido anónimo continua a
 * receber 200 (o {@code healthcheck} do {@code docker-compose.yml} não
 * pode quebrar), mas sem o campo {@code components}; só um pedido
 * autenticado vê o componente {@code db}.
 */
class HealthCheckIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SeerHubProperties properties;

    private AuthTestSupport authTestSupport;

    @BeforeEach
    void montarSuporte() {
        authTestSupport = new AuthTestSupport(mockMvc, objectMapper, jdbcTemplate, properties);
    }

    @Test
    void healthDevolve200ComComponenteDbUp() throws Exception {
        AuthResponse sessao = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("health"), "password-longa-123");

        mockMvc.perform(get("/actuator/health")
                        .header("Authorization", "Bearer " + sessao.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    void healthAnonimoDevolve200SemExporOsComponentes() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }
}
