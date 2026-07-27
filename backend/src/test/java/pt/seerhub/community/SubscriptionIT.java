package pt.seerhub.community;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import pt.seerhub.community.api.CommunityResponse;
import pt.seerhub.community.service.CommunityAccessRules;
import pt.seerhub.community.service.SubscriptionService;
import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.AuthTestSupport;
import pt.seerhub.support.CommunityTestSupport;
import pt.seerhub.user.api.AuthResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Critério 1 (subscrever), critério 4 (várias comunidades em simultâneo) e
 * X4 — R3. {@code POST /api/communities/{slug}/subscription},
 * {@code GET /api/me/subscriptions}.
 */
class SubscriptionIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SeerHubProperties properties;

    private AuthTestSupport authTestSupport;
    private CommunityTestSupport communityTestSupport;

    private static final String PASSWORD_VALIDA = "password-longa-123";

    @BeforeEach
    void montarSuporte() {
        authTestSupport = new AuthTestSupport(mockMvc, objectMapper, jdbcTemplate, properties);
        communityTestSupport = new CommunityTestSupport(mockMvc, objectMapper, jdbcTemplate);
    }

    @Test
    void subscreverCriaMembershipAtivaComPapelMembro() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-1a"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Um A"));
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-1a"), PASSWORD_VALIDA);

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(comunidade.slug())))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Map<String, Object> membership = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());
        assertThat(membership.get("role")).isEqualTo("MEMBER");
        assertThat(membership.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void subscreverDefineExpiracaoATrintaDias() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-1b"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Um B"));
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-1b"), PASSWORD_VALIDA);

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isCreated());

        Map<String, Object> membership = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());
        java.sql.Timestamp expiraEm = (java.sql.Timestamp) membership.get("expires_at");
        Instant esperado = Instant.now().plus(30, ChronoUnit.DAYS);

        assertThat(expiraEm.toInstant()).isCloseTo(esperado, within(Duration.ofMinutes(2)));
    }

    @Test
    void subscreverGravaOParComunidadeUtilizadorCorreto() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-1d"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Um D"));
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-1d"), PASSWORD_VALIDA);

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isCreated());

        Map<String, Object> membership = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());
        assertThat(((Number) membership.get("community_id")).longValue()).isEqualTo(comunidade.id());
        assertThat(((Number) membership.get("user_id")).longValue()).isEqualTo(membro.user().id());
        assertThat(membership.get("joined_at")).isNotNull();
        assertThat(((Number) membership.get("version")).longValue()).isZero();
    }

    @Test
    void subscricaoApareceEmAsMinhasSubscricoes() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-1e"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Um E"));
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-1e"), PASSWORD_VALIDA);

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/me/subscriptions")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value(comunidade.slug()))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void subscreverDuasVezesDevolve409ENaoCriaSegundaLinha() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-1f"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Um F"));
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-1f"), PASSWORD_VALIDA);

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(SubscriptionService.MENSAGEM_JA_SUBSCREVEU));

        // Uma linha do dono + uma do membro, nunca uma segunda do membro.
        assertThat(communityTestSupport.contarMemberships(comunidade.id())).isEqualTo(2);
    }

    @Test
    void restricaoDeUnicidadeRecusaSegundaLinhaParaOMesmoPar() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-1g"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Um G"));
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-1g"), PASSWORD_VALIDA);

        Instant expiraEm = Instant.now().plus(30, ChronoUnit.DAYS);
        communityTestSupport.inserirMembership(comunidade.id(), membro.user().id(), "MEMBER", "ACTIVE", expiraEm);

        assertThatThrownBy(() -> communityTestSupport.inserirMembership(
                comunidade.id(), membro.user().id(), "MEMBER", "ACTIVE", expiraEm))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void subscreverComunidadeSuspensaDevolve409ENaoCriaMembership() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-1h"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Um H"));
        communityTestSupport.suspender(comunidade.slug());

        AuthResponse estranho = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("estranho-1h"), PASSWORD_VALIDA);

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + estranho.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(CommunityAccessRules.MENSAGEM_COMUNIDADE_SUSPENSA));

        // Só a linha OWNER de F02 — nenhuma linha MEMBER foi criada.
        assertThat(communityTestSupport.contarMemberships(comunidade.id())).isEqualTo(1);
    }

    @Test
    void donoEModeradorASubscreverDevolvem409() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-1i"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Um I"));

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + dono.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(SubscriptionService.MENSAGEM_JA_TEM_ACESSO_COMO_GESTOR));

        AuthResponse moderador = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("moderador-1i"), PASSWORD_VALIDA);
        communityTestSupport.inserirMembership(comunidade.id(), moderador.user().id(), "MODERATOR", "ACTIVE", null);

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + moderador.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(SubscriptionService.MENSAGEM_JA_TEM_ACESSO_COMO_GESTOR));

        // Nenhuma linha MEMBER foi criada por qualquer uma das duas tentativas.
        assertThat(communityTestSupport.contarMemberships(comunidade.id())).isEqualTo(2);
    }

    @Test
    void resubscreverRenovaALinhaExistenteSemCriarOutra() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-1j"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Um J"));
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-1j"), PASSWORD_VALIDA);

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isCreated());

        // Simula a passagem da tarefa diária: a linha já está EXPIRED, com data no passado.
        jdbcTemplate.update(
                "UPDATE community_memberships SET status = 'EXPIRED', expires_at = ? "
                        + "WHERE community_id = ? AND user_id = ?",
                java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), comunidade.id(), membro.user().id());

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Map<String, Object> membership = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());
        assertThat(membership.get("status")).isEqualTo("ACTIVE");
        java.sql.Timestamp novaExpiracao = (java.sql.Timestamp) membership.get("expires_at");
        assertThat(novaExpiracao.toInstant()).isAfter(Instant.now());
        assertThat(communityTestSupport.contarMemberships(comunidade.id())).isEqualTo(2);
    }

    @Test
    void reativarSubscricaoCanceladaMantemAExpiracao() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-1k"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Um K"));
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-1k"), PASSWORD_VALIDA);

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isCreated());

        Map<String, Object> antesDeCancelar = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());
        java.sql.Timestamp expiracaoOriginal = (java.sql.Timestamp) antesDeCancelar.get("expires_at");

        mockMvc.perform(delete("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Map<String, Object> depois = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());
        assertThat(depois.get("status")).isEqualTo("ACTIVE");
        assertThat(((java.sql.Timestamp) depois.get("expires_at")).toInstant())
                .isEqualTo(expiracaoOriginal.toInstant());
    }

    @Test
    void subscreverSemTokenDevolve401() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-1l"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Um L"));

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void subscreverSlugInexistenteDevolve404() throws Exception {
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-1m"), PASSWORD_VALIDA);

        mockMvc.perform(post("/api/communities/slug-que-nao-existe-de-todo/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(SubscriptionService.MENSAGEM_COMUNIDADE_NAO_ENCONTRADA));
    }

    @Test
    void utilizadorPodeSubscreverVariasComunidadesEmSimultaneoSemLimite() throws Exception {
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-4a"), PASSWORD_VALIDA);

        for (int i = 0; i < 5; i++) {
            AuthResponse dono = authTestSupport.registarEAutenticar(
                    authTestSupport.emailUnico("dono-4a-" + i), PASSWORD_VALIDA);
            CommunityResponse comunidade = communityTestSupport.criar(
                    dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Quatro A " + i));

            mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                            .header("Authorization", "Bearer " + membro.accessToken()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        Integer totalAtivas = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM community_memberships WHERE user_id = ? AND role = 'MEMBER' AND status = 'ACTIVE'",
                Integer.class, membro.user().id());
        assertThat(totalAtivas).isEqualTo(5);
    }

    @Test
    void asMinhasSubscricoesDevolvemTodasAsComunidadesSubscritas() throws Exception {
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-4b"), PASSWORD_VALIDA);

        for (int i = 0; i < 5; i++) {
            AuthResponse dono = authTestSupport.registarEAutenticar(
                    authTestSupport.emailUnico("dono-4b-" + i), PASSWORD_VALIDA);
            CommunityResponse comunidade = communityTestSupport.criar(
                    dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Quatro B " + i));

            mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                            .header("Authorization", "Bearer " + membro.accessToken()))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/me/subscriptions")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[4].active").value(true));
    }

    @Test
    void asMinhasSubscricoesSemTokenDevolve401() throws Exception {
        mockMvc.perform(get("/api/me/subscriptions"))
                .andExpect(status().isUnauthorized());
    }
}
