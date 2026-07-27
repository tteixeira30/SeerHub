package pt.seerhub.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.user.api.AuthResponse;
import pt.seerhub.user.domain.GlobalRole;
import pt.seerhub.user.security.AuthenticatedUser;
import pt.seerhub.user.service.JwtService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Utilitário partilhado de autenticação para os {@code *IT} de F01 (e das
 * features seguintes que precisem de autenticar-se nos seus próprios
 * testes). Classe simples, sem anotações Spring — construída explicitamente
 * em {@code @BeforeEach} de cada classe de teste com as colaborações já
 * autowired nela.
 */
public class AuthTestSupport {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final SeerHubProperties properties;

    public AuthTestSupport(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
            SeerHubProperties properties) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    /** Regista um utilizador novo. O registo (R1) já devolve um par de tokens autenticado. */
    public AuthResponse registarEAutenticar(String email, String password) throws Exception {
        return registar(email, password, null);
    }

    public AuthResponse registar(String email, String password, String displayName) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("email", email);
        corpo.put("password", password);
        if (displayName != null) {
            corpo.put("displayName", displayName);
        }

        MvcResult resultado = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(resultado.getResponse().getContentAsString(), AuthResponse.class);
    }

    public AuthResponse login(String email, String password) throws Exception {
        Map<String, String> corpo = Map.of("email", email, "password", password);

        MvcResult resultado = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(resultado.getResponse().getContentAsString(), AuthResponse.class);
    }

    /**
     * Regista um utilizador e promove-o a ADMIN por JDBC direto — não há
     * endpoint de promoção antes de F14 (dívida deliberada, ver handoff).
     * Faz login de novo depois da promoção porque o papel viaja no access
     * token à emissão (D-4): o token do registo ainda diria USER.
     */
    public AuthResponse registarAdmin(String email, String password) throws Exception {
        registarEAutenticar(email, password);
        jdbcTemplate.update("UPDATE users SET global_role = 'ADMIN' WHERE email = ?", email);
        return login(email, password);
    }

    /** Access token assinado com o segredo real, mas já expirado (4c). */
    public String accessTokenExpirado(Long userId, String email, GlobalRole role) {
        Instant instantePassado = Instant.now()
                .minus(properties.security().accessTtl())
                .minus(Duration.ofMinutes(5));
        JwtService jwtServiceNoPassado = new JwtService(properties, Clock.fixed(instantePassado, ZoneOffset.UTC));
        return jwtServiceNoPassado.gerarAccessToken(new AuthenticatedUser(userId, email, role));
    }

    /** Access token bem-formado, mas assinado com um segredo diferente do configurado (4d). */
    public String accessTokenComOutroSegredo(Long userId, String email, GlobalRole role) {
        SeerHubProperties outrasPropriedades = new SeerHubProperties(
                new SeerHubProperties.Security(
                        "outro-segredo-completamente-diferente-com-32-bytes",
                        properties.security().accessTtl(),
                        properties.security().refreshTtl()),
                properties.football(),
                properties.uploads());
        JwtService jwtServiceOutroSegredo = new JwtService(outrasPropriedades, Clock.systemUTC());
        return jwtServiceOutroSegredo.gerarAccessToken(new AuthenticatedUser(userId, email, role));
    }

    /** Email único por teste, para os testes não dependerem da ordem de execução (contentor partilhado). */
    public String emailUnico(String prefixo) {
        return prefixo + "-" + UUID.randomUUID() + "@exemplo.pt";
    }
}
