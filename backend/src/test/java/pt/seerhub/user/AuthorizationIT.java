package pt.seerhub.user;

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
import pt.seerhub.user.domain.GlobalRole;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 4a–4f — autorização (R1, critério 4): 401 sem token válido, 403 com papel insuficiente. */
class AuthorizationIT extends AbstractIntegrationTest {

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
    void endpointProtegidoSemTokenDevolve401ComProblemDetailECorrelationId() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Autenticação necessária."))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void tokenMalformadoDevolve401() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer isto-nao-e-um-jwt-valido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessTokenExpiradoDevolve401() throws Exception {
        String tokenExpirado = authTestSupport.accessTokenExpirado(1L, "expirado@exemplo.pt", GlobalRole.USER);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + tokenExpirado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessTokenAssinadoComOutroSegredoDevolve401() throws Exception {
        String tokenOutroSegredo = authTestSupport.accessTokenComOutroSegredo(1L, "outro@exemplo.pt", GlobalRole.USER);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + tokenOutroSegredo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void utilizadorComPapelUserEmEndpointDeAdminDevolve403() throws Exception {
        String email = authTestSupport.emailUnico("user-comum");
        AuthResponse sessao = authTestSupport.registarEAutenticar(email, PASSWORD_VALIDA);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + sessao.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Não tem permissões para aceder a este recurso."));
    }

    @Test
    void utilizadorComPapelAdminNoMesmoEndpointDevolve200() throws Exception {
        String email = authTestSupport.emailUnico("admin");
        AuthResponse sessaoAdmin = authTestSupport.registarAdmin(email, PASSWORD_VALIDA);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + sessaoAdmin.accessToken()))
                .andExpect(status().isOk());
    }
}
