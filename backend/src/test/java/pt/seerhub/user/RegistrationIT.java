package pt.seerhub.user;

import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.AuthTestSupport;
import pt.seerhub.user.service.AuthService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 1a, 1b, 1c, 1d, 3a — registo (R1, critérios 1 e 3). */
class RegistrationIT extends AbstractIntegrationTest {

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
    void registoComEmailNovoDevolve201ECriaUtilizadorComPapelUser() throws Exception {
        String email = authTestSupport.emailUnico("nova-conta");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", PASSWORD_VALIDA))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.globalRole").value("USER"));

        Integer contagem = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE email = ?", Integer.class, email);
        assertThat(contagem).isEqualTo(1);
    }

    @Test
    void registoComPasswordDeNoveCaracteresDevolve400ENaoCriaUtilizador() throws Exception {
        String email = authTestSupport.emailUnico("password-curta");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", "123456789"))))
                .andExpect(status().isBadRequest());

        Integer contagem = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE email = ?", Integer.class, email);
        assertThat(contagem).isEqualTo(0);
    }

    @Test
    void passwordEGuardadaComBcryptDeForcaMinimaDezENuncaEmClaro() throws Exception {
        String email = authTestSupport.emailUnico("bcrypt");

        authTestSupport.registarEAutenticar(email, PASSWORD_VALIDA);

        String hashGuardado = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, email);

        assertThat(hashGuardado).isNotEqualTo(PASSWORD_VALIDA);
        // Formato BCrypt: $2a$<custo>$... — custo 10, exatamente dois dígitos.
        assertThat(hashGuardado).matches("^\\$2[aby]\\$10\\$.+$");
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD_VALIDA, hashGuardado)).isTrue();
    }

    @Test
    void emailENormalizadoAntesDeGuardarEDeVerificarUnicidade() throws Exception {
        String prefixo = "normaliza-" + java.util.UUID.randomUUID();
        String emailComEspacosEMaiusculas = "  " + prefixo.toUpperCase(Locale.ROOT) + "@Exemplo.PT  ";
        String emailNormalizadoEquivalente = (prefixo + "@exemplo.pt").toLowerCase(Locale.ROOT);

        authTestSupport.registarEAutenticar(emailComEspacosEMaiusculas, PASSWORD_VALIDA);

        // A conta foi gravada já normalizada.
        Integer contagemNormalizado = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE email = ?", Integer.class, emailNormalizadoEquivalente);
        assertThat(contagemNormalizado).isEqualTo(1);

        // Um segundo registo com a variante normalizada é tratado como o mesmo email: 409.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", emailNormalizadoEquivalente, "password", PASSWORD_VALIDA))))
                .andExpect(status().isConflict());
    }

    @Test
    void registoComEmailJaExistenteDevolve409ComMensagemQueNaoRevelaAExistenciaDaConta() throws Exception {
        String email = "ana.silva@exemplo.pt";
        // Garantir que este teste não depende de estado de outro (contentor partilhado):
        // se o email já existir de uma corrida anterior, a asserção seguinte ainda é válida
        // (o registo duplicado dá sempre 409), mas criamos a conta aqui para o cenário ser
        // determinístico dentro deste teste.
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);
        authTestSupport.registarEAutenticar(email, PASSWORD_VALIDA);

        MvcResult resultado = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", PASSWORD_VALIDA))))
                // 1. Estado correto.
                .andExpect(status().isConflict())
                // 2. Mensagem exatamente igual à constante pública, e nada mais.
                .andExpect(jsonPath("$.detail").value(AuthService.MENSAGEM_REGISTO_RECUSADO))
                .andReturn();

        String detail = resultado.getResponse().getContentAsString();

        // 3. A mensagem não contém o email submetido, nem em minúsculas nem em maiúsculas,
        //    nem o seu local-part, nem o domínio.
        // 4. A mensagem não contém nenhum termo que revele a causa.
        assertThat(detail.toLowerCase(Locale.ROOT))
                .doesNotContain("ana.silva")
                .doesNotContain("exemplo.pt")
                .doesNotContain("existe")
                .doesNotContain("existente")
                .doesNotContain("registad")
                .doesNotContain("duplicad")
                .doesNotContain("utilizad")
                .doesNotContain("conta");
        assertThat(JsonPath.<Object>read(detail, "$")).asInstanceOf(MAP)
                .doesNotContainKey("erros");
    }
}
