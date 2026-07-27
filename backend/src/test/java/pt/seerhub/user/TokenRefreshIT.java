package pt.seerhub.user;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 2c, 2d, 2e, X1, X4 — refresh (R1, critério 2: rotativo e revogável). */
class TokenRefreshIT extends AbstractIntegrationTest {

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
    void refreshTokenEmitidoNoLoginExpiraTrintaDiasDepois() throws Exception {
        String email = authTestSupport.emailUnico("refresh-30-dias");
        AuthResponse resposta = authTestSupport.registarEAutenticar(email, PASSWORD_VALIDA);

        Instant issuedAt = lerInstante("issued_at", resposta.refreshToken());
        Instant expiresAt = lerInstante("expires_at", resposta.refreshToken());

        assertThat(java.time.Duration.between(issuedAt, expiresAt)).isEqualTo(java.time.Duration.ofDays(30));
        assertThat(properties.security().refreshTtl()).isEqualTo(java.time.Duration.ofDays(30));
    }

    @Test
    void refreshDevolveParNovoEOTokenAnteriorDeixaDeServir() throws Exception {
        String email = authTestSupport.emailUnico("refresh-rotativo");
        AuthResponse original = authTestSupport.registarEAutenticar(email, PASSWORD_VALIDA);

        AuthResponse novo = objectMapper.readValue(
                refrescarObtendoCorpo(original.refreshToken()), AuthResponse.class);

        assertThat(novo.refreshToken()).isNotEqualTo(original.refreshToken());
        assertThat(novo.accessToken()).isNotBlank();

        // O token anterior já não serve: uma segunda tentativa de refresh com ele é recusada.
        refrescar(original.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenRevogadoNaBaseDeDadosDevolve401() throws Exception {
        String email = authTestSupport.emailUnico("refresh-revogado");
        AuthResponse resposta = authTestSupport.registarEAutenticar(email, PASSWORD_VALIDA);

        String hash = sha256Hex(resposta.refreshToken());
        jdbcTemplate.update("UPDATE refresh_tokens SET revoked_at = now() WHERE token_hash = ?", hash);

        refrescar(resposta.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void reutilizarUmRefreshTokenJaRodadoDevolve401ERevogaAFamiliaInteira() throws Exception {
        String email = authTestSupport.emailUnico("reutilizacao");
        AuthResponse original = authTestSupport.registarEAutenticar(email, PASSWORD_VALIDA);

        String corpoNovo = refrescarObtendoCorpo(original.refreshToken());
        AuthResponse novo = objectMapper.readValue(corpoNovo, AuthResponse.class);

        // Reutilizar o token original (já rodado) é o sinal de roubo/duplicação.
        refrescar(original.refreshToken()).andExpect(status().isUnauthorized());

        // A família inteira foi revogada: o sucessor legítimo também deixou de servir.
        refrescar(novo.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenExpiradoDevolve401() throws Exception {
        String email = authTestSupport.emailUnico("refresh-expirado");
        AuthResponse resposta = authTestSupport.registarEAutenticar(email, PASSWORD_VALIDA);

        // issued_at também recuado para não violar ck_rt_expiry (expires_at > issued_at).
        String hash = sha256Hex(resposta.refreshToken());
        jdbcTemplate.update("UPDATE refresh_tokens SET issued_at = now() - interval '31 days', "
                + "expires_at = now() - interval '1 minute' WHERE token_hash = ?", hash);

        refrescar(resposta.refreshToken()).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions refrescar(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }

    private String refrescarObtendoCorpo(String refreshToken) throws Exception {
        return refrescar(refreshToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
    }

    private Instant lerInstante(String coluna, String refreshTokenBruto) {
        String hash = sha256Hex(refreshTokenBruto);
        return jdbcTemplate.queryForObject(
                "SELECT " + coluna + " FROM refresh_tokens WHERE token_hash = ?",
                java.sql.Timestamp.class, hash).toInstant();
    }

    private static String sha256Hex(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
