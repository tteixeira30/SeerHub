package pt.seerhub.football;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.football.provider.FixedFootballDataProvider;
import pt.seerhub.football.service.FootballSyncService;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.AuthTestSupport;
import pt.seerhub.support.FootballTestSupport;
import pt.seerhub.user.api.AuthResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** R5, critério 9 — consumo diário exposto numa métrica. */
class FootballMetricsIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SeerHubProperties properties;
    @Autowired
    private FootballSyncService footballSyncService;
    @Autowired
    private FixedFootballDataProvider fake;

    private FootballTestSupport footballTestSupport;
    private AuthTestSupport authTestSupport;

    @BeforeEach
    void setUp() {
        footballTestSupport = new FootballTestSupport(jdbcTemplate);
        authTestSupport = new AuthTestSupport(mockMvc, objectMapper, jdbcTemplate, properties);
        footballTestSupport.limparOrcamento();
        fake.limparRegisto();
    }

    @Test
    void oNumeroDeChamadasDoDiaEExpostoNaMetrica() throws Exception {
        AuthResponse admin = authTestSupport.registarAdmin(
                authTestSupport.emailUnico("football-metrics-a"), "password12345");

        mockMvc.perform(get("/actuator/metrics/seerhub.football.api.calls.today")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("seerhub.football.api.calls.today"))
                .andExpect(jsonPath("$.measurements[0].value").exists());
    }

    @Test
    void aMetricaAcompanhaOConsumoDepoisDeUmaSincronizacao() throws Exception {
        AuthResponse admin = authTestSupport.registarAdmin(
                authTestSupport.emailUnico("football-metrics-b"), "password12345");

        footballSyncService.sincronizarCatalogo();
        int consumoReal = footballTestSupport.consumoDoDia();

        var resultado = mockMvc.perform(get("/actuator/metrics/seerhub.football.api.calls.today")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andReturn();

        String corpo = resultado.getResponse().getContentAsString();
        assertThat(corpo).contains("\"value\":" + consumoReal + ".0");
    }

    @Test
    void oEndpointDeMetricasRespondeAoAdminERecusaAosRestantes() throws Exception {
        AuthResponse admin = authTestSupport.registarAdmin(
                authTestSupport.emailUnico("football-metrics-c"), "password12345");
        AuthResponse utilizador = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("football-metrics-user"), "password12345");

        mockMvc.perform(get("/actuator/metrics/seerhub.football.api.calls.today")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics/seerhub.football.api.calls.today")
                        .header("Authorization", "Bearer " + utilizador.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/metrics/seerhub.football.api.calls.today"))
                .andExpect(status().isUnauthorized());
    }
}
