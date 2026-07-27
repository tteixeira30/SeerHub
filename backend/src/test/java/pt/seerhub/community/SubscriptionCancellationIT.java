package pt.seerhub.community;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import pt.seerhub.community.api.CommunityResponse;
import pt.seerhub.community.service.SubscriptionService;
import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.AuthTestSupport;
import pt.seerhub.support.CommunityTestSupport;
import pt.seerhub.user.api.AuthResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Critério 2 — R3: {@code DELETE /api/communities/{slug}/subscription}
 * cancela mas mantém acesso até {@code expires_at}.
 *
 * <p>2c/2d são a prova de acesso, não de coluna (regra dura do plano): o
 * pedido a {@code member-area} depois do {@code DELETE} é a asserção
 * central, não a leitura da coluna {@code status} sozinha.
 */
class SubscriptionCancellationIT extends AbstractIntegrationTest {

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

    private CommunityResponse comunidadeComDono(AuthResponse dono, String nome) throws Exception {
        return communityTestSupport.criar(dono.accessToken(), communityTestSupport.nomeUnico(nome));
    }

    @Test
    void cancelarColocaAMembershipEmCancelada() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-2a"), PASSWORD_VALIDA);
        CommunityResponse comunidade = comunidadeComDono(dono, "Comunidade Dois A");
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-2a"), PASSWORD_VALIDA);
        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Map<String, Object> membership = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());
        assertThat(membership.get("status")).isEqualTo("CANCELLED");
    }

    @Test
    void cancelarNaoAlteraAExpiracao() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-2b"), PASSWORD_VALIDA);
        CommunityResponse comunidade = comunidadeComDono(dono, "Comunidade Dois B");
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-2b"), PASSWORD_VALIDA);
        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isCreated());

        Map<String, Object> antes = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());

        mockMvc.perform(delete("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isOk());

        Map<String, Object> depois = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());
        assertThat(depois.get("expires_at")).isEqualTo(antes.get("expires_at"));
    }

    @Test
    void depoisDeCancelarOAcessoPremiumContinuaAFuncionarAteAExpiracao() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-2c"), PASSWORD_VALIDA);
        CommunityResponse comunidade = comunidadeComDono(dono, "Comunidade Dois C");
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-2c"), PASSWORD_VALIDA);
        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isOk());

        // A prova de acesso, não de coluna: um pedido real ao conteúdo premium continua a passar.
        mockMvc.perform(get("/api/communities/" + comunidade.slug() + "/member-area")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void depoisDaDataDeExpiracaoOAcessoPremiumDeixaDeFuncionar() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-2d"), PASSWORD_VALIDA);
        CommunityResponse comunidade = comunidadeComDono(dono, "Comunidade Dois D");
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-2d"), PASSWORD_VALIDA);
        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isOk());

        // Empurra expires_at da mesma linha para o passado.
        communityTestSupport.definirExpiracao(
                comunidade.id(), membro.user().id(), Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/communities/" + comunidade.slug() + "/member-area")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelarSemSubscricaoDevolve404() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-2e"), PASSWORD_VALIDA);
        CommunityResponse comunidade = comunidadeComDono(dono, "Comunidade Dois E");
        AuthResponse estranho = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("estranho-2e"), PASSWORD_VALIDA);

        mockMvc.perform(delete("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + estranho.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(SubscriptionService.MENSAGEM_SUBSCRICAO_NAO_ENCONTRADA));
    }

    @Test
    void cancelarNaoTocaNaSubscricaoDeOutroUtilizador() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-2f"), PASSWORD_VALIDA);
        CommunityResponse comunidade = comunidadeComDono(dono, "Comunidade Dois F");
        AuthResponse membro1 = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro1-2f"), PASSWORD_VALIDA);
        AuthResponse membro2 = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro2-2f"), PASSWORD_VALIDA);

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro1.accessToken()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro2.accessToken()))
                .andExpect(status().isCreated());

        Map<String, Object> antesMembro2 = communityTestSupport.lerMembership(comunidade.id(), membro2.user().id());

        mockMvc.perform(delete("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro1.accessToken()))
                .andExpect(status().isOk());

        Map<String, Object> depoisMembro2 = communityTestSupport.lerMembership(comunidade.id(), membro2.user().id());
        assertThat(depoisMembro2).isEqualTo(antesMembro2);
        assertThat(depoisMembro2.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void donoNaoPodeCancelarASuaPropriaMembership() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-2g"), PASSWORD_VALIDA);
        CommunityResponse comunidade = comunidadeComDono(dono, "Comunidade Dois G");

        mockMvc.perform(delete("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + dono.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(SubscriptionService.MENSAGEM_SEM_SUBSCRICAO_PARA_CANCELAR));
    }

    @Test
    void cancelarDuasVezesEIdempotente() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-2h"), PASSWORD_VALIDA);
        CommunityResponse comunidade = comunidadeComDono(dono, "Comunidade Dois H");
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-2h"), PASSWORD_VALIDA);
        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Map<String, Object> primeiroCancelamento = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());

        mockMvc.perform(delete("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Map<String, Object> segundoCancelamento = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());
        assertThat(segundoCancelamento.get("expires_at")).isEqualTo(primeiroCancelamento.get("expires_at"));
    }

    @Test
    void cancelarMembershipJaExpiradaDevolve409() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-2i"), PASSWORD_VALIDA);
        CommunityResponse comunidade = comunidadeComDono(dono, "Comunidade Dois I");
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-2i"), PASSWORD_VALIDA);
        communityTestSupport.inserirMembership(
                comunidade.id(), membro.user().id(), "MEMBER", "EXPIRED", Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(delete("/api/communities/" + comunidade.slug() + "/subscription")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(SubscriptionService.MENSAGEM_SUBSCRICAO_JA_EXPIRADA));
    }

    @Test
    void cancelarSemTokenDevolve401() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-2j"), PASSWORD_VALIDA);
        CommunityResponse comunidade = comunidadeComDono(dono, "Comunidade Dois J");

        mockMvc.perform(delete("/api/communities/" + comunidade.slug() + "/subscription"))
                .andExpect(status().isUnauthorized());
    }
}
