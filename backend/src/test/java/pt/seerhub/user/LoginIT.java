package pt.seerhub.user;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.AuthTestSupport;
import pt.seerhub.user.api.AuthResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 2a, 3b, X2, X3, X5 — login (R1, critérios 2 e 3, e X5). */
class LoginIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SeerHubProperties properties;

    private AuthTestSupport authTestSupport;

    private static final String PASSWORD_VALIDA = "password-longa-123";

    @BeforeEach
    void montarSuporte() {
        authTestSupport = new AuthTestSupport(mockMvc, objectMapper, jdbcTemplate, properties);
    }

    @Test
    void loginComCredenciaisCorretasDevolveParDeTokensQueAbreEndpointProtegido() throws Exception {
        String email = authTestSupport.emailUnico("login-ok");
        authTestSupport.registarEAutenticar(email, PASSWORD_VALIDA);

        AuthResponse resposta = authTestSupport.login(email, PASSWORD_VALIDA);

        assertThat(resposta.accessToken()).isNotBlank();
        assertThat(resposta.refreshToken()).isNotBlank();
        assertThat(resposta.user().email()).isEqualTo(email);

        // O access token devolvido abre de facto um endpoint protegido.
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + resposta.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void loginComPasswordErradaDevolve401ENaoEmiteTokens() throws Exception {
        String email = authTestSupport.emailUnico("password-errada");
        authTestSupport.registarEAutenticar(email, PASSWORD_VALIDA);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", "password-completamente-errada"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void loginComEmailDesconhecidoDevolve401ENaoEmiteTokens() throws Exception {
        String emailDesconhecido = authTestSupport.emailUnico("nunca-registado");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", emailDesconhecido, "password", PASSWORD_VALIDA))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void loginDeUtilizadorSuspensoDevolveAMesmaResposta401DeCredenciaisErradas() throws Exception {
        String emailSuspenso = authTestSupport.emailUnico("suspenso");
        authTestSupport.registarEAutenticar(emailSuspenso, PASSWORD_VALIDA);
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE email = ?", emailSuspenso);

        String emailPasswordErrada = authTestSupport.emailUnico("comparacao-password-errada");
        authTestSupport.registarEAutenticar(emailPasswordErrada, PASSWORD_VALIDA);

        Map<String, Object> corpoSuspenso = corpoSemCorrelationIdNemInstance(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", emailSuspenso, "password", PASSWORD_VALIDA))))
                .andExpect(status().isUnauthorized())
                .andReturn());

        Map<String, Object> corpoPasswordErrada = corpoSemCorrelationIdNemInstance(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", emailPasswordErrada, "password", "password-errada-de-proposito"))))
                .andExpect(status().isUnauthorized())
                .andReturn());

        assertThat(corpoSuspenso).isEqualTo(corpoPasswordErrada);
    }

    @Test
    void loginComEmailDesconhecidoEComPasswordErradaProduzemRespostasIndistinguiveis() throws Exception {
        String emailDesconhecido = authTestSupport.emailUnico("desconhecido");

        String emailComPasswordErrada = authTestSupport.emailUnico("com-password-errada");
        authTestSupport.registarEAutenticar(emailComPasswordErrada, PASSWORD_VALIDA);

        MvcResult resultadoDesconhecido = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", emailDesconhecido, "password", PASSWORD_VALIDA))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult resultadoPasswordErrada = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", emailComPasswordErrada, "password", "password-errada-de-proposito"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        // Ambos 401, confirmado acima. Agora a prova de que "noutro contexto"
        // (não só o registo) a resposta não distingue "conta não existe" de
        // "password errada": os dois corpos, sem correlationId nem instance
        // (que variam por pedido), têm de ser byte a byte iguais.
        Map<String, Object> corpoDesconhecido = corpoSemCorrelationIdNemInstance(resultadoDesconhecido);
        Map<String, Object> corpoPasswordErrada = corpoSemCorrelationIdNemInstance(resultadoPasswordErrada);

        assertThat(corpoDesconhecido).isEqualTo(corpoPasswordErrada);

        // E os cabeçalhos relevantes (fora do correlationId) também coincidem.
        String tipoDesconhecido = resultadoDesconhecido.getResponse().getContentType();
        String tipoPasswordErrada = resultadoPasswordErrada.getResponse().getContentType();
        assertThat(tipoDesconhecido).isEqualTo(tipoPasswordErrada);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> corpoSemCorrelationIdNemInstance(MvcResult resultado) throws Exception {
        String json = resultado.getResponse().getContentAsString();
        Map<String, Object> corpo = new HashMap<>(objectMapper.readValue(json, Map.class));
        corpo.remove("correlationId");
        corpo.remove("instance");
        return corpo;
    }
}
