package pt.seerhub.user;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.AuthTestSupport;
import pt.seerhub.user.api.AuthResponse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 5a, 5b — logout (R1, critério 5). */
class LogoutIT extends AbstractIntegrationTest {

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
    void logoutDevolve204EORefreshTokenUsadoDeixaDeServir() throws Exception {
        String email = authTestSupport.emailUnico("logout");
        AuthResponse sessao = authTestSupport.registarEAutenticar(email, PASSWORD_VALIDA);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + sessao.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", sessao.refreshToken()))))
                .andExpect(status().isNoContent());

        // O refresh token usado no logout já não serve para renovar.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", sessao.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutDeUmaSessaoNaoInvalidaORefreshTokenDeOutraSessao() throws Exception {
        String email = authTestSupport.emailUnico("duas-sessoes");
        authTestSupport.registarEAutenticar(email, PASSWORD_VALIDA);

        // Duas sessões independentes (dois logins) do mesmo utilizador.
        AuthResponse sessaoA = authTestSupport.login(email, PASSWORD_VALIDA);
        AuthResponse sessaoB = authTestSupport.login(email, PASSWORD_VALIDA);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + sessaoA.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", sessaoA.refreshToken()))))
                .andExpect(status().isNoContent());

        // A sessão B continua válida: o refresh dela ainda funciona.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", sessaoB.refreshToken()))))
                .andExpect(status().isOk());
    }
}
