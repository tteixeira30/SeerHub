package pt.seerhub.community;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import pt.seerhub.community.api.CommunityResponse;
import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.AuthTestSupport;
import pt.seerhub.support.CommunityTestSupport;
import pt.seerhub.user.api.AuthResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A matriz papel × endpoint (R4, critério 4, §3.2 do plano de F04): 25
 * testes de integração sobre os quatro endpoints protegidos por
 * {@code CommunityPermission}. Cada célula de recusa assere o código de
 * estado exato <b>e</b> que o estado persistido não mudou; cada endpoint
 * tem pelo menos uma célula de sucesso (dono), o controlo que prova que um
 * {@code 403} vem do papel e não de uma rota partida.
 *
 * <p>Cada teste monta o seu próprio cenário (dono + comunidade) — nenhuma
 * célula de sucesso muta um cenário partilhado (risco 2 do plano: o
 * contentor Postgres é partilhado e nunca limpo entre classes).
 */
class CommunityPermissionMatrixIT extends AbstractIntegrationTest {

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

    private record Cenario(AuthResponse dono, CommunityResponse comunidade) {
    }

    private Cenario criarCenario(String prefixo) throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-" + prefixo), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade " + prefixo), 500);
        return new Cenario(dono, comunidade);
    }

    private AuthResponse visitante(String prefixo) throws Exception {
        return authTestSupport.registarEAutenticar(authTestSupport.emailUnico("visitante-" + prefixo), PASSWORD_VALIDA);
    }

    private AuthResponse membroAtivo(String prefixo, CommunityResponse comunidade) throws Exception {
        AuthResponse membro = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("membro-" + prefixo), PASSWORD_VALIDA);
        communityTestSupport.inserirMembership(comunidade.id(), membro.user().id(), "MEMBER", "ACTIVE",
                Instant.now().plus(30, ChronoUnit.DAYS));
        return membro;
    }

    private AuthResponse exMembro(String prefixo, CommunityResponse comunidade) throws Exception {
        AuthResponse exMembro = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("exmembro-" + prefixo), PASSWORD_VALIDA);
        communityTestSupport.inserirMembership(comunidade.id(), exMembro.user().id(), "MEMBER", "EXPIRED",
                Instant.now().minus(1, ChronoUnit.DAYS));
        return exMembro;
    }

    private AuthResponse moderador(String prefixo, CommunityResponse comunidade) throws Exception {
        AuthResponse moderador = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("moderador-" + prefixo), PASSWORD_VALIDA);
        communityTestSupport.inserirMembership(comunidade.id(), moderador.user().id(), "MODERATOR", "ACTIVE",
                Instant.now().plus(30, ChronoUnit.DAYS));
        return moderador;
    }

    private String corpoNomeacao(Long userId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("userId", userId));
    }

    private String corpoEdicao(String name, int priceMonthlyCents) throws Exception {
        return objectMapper.writeValueAsString(Map.of("name", name, "priceMonthlyCents", priceMonthlyCents));
    }

    // ---- PUT /api/communities/{slug} (2d, 4t–4x) ----

    @Test
    void moderadorNaoAlteraOPrecoDevolve403() throws Exception {
        Cenario cenario = criarCenario("2d");
        AuthResponse moderador = moderador("2d", cenario.comunidade());

        mockMvc.perform(put("/api/communities/" + cenario.comunidade().slug())
                        .header("Authorization", "Bearer " + moderador.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoEdicao(cenario.comunidade().name(), 12345)))
                .andExpect(status().isForbidden());

        assertThat(communityTestSupport.lerColunaDaComunidade(cenario.comunidade().slug(), "price_monthly_cents"))
                .isEqualTo(500);
    }

    @Test
    void anonimoNaoEditaAComunidadeDevolve401() throws Exception {
        Cenario cenario = criarCenario("4t");

        mockMvc.perform(put("/api/communities/" + cenario.comunidade().slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoEdicao(cenario.comunidade().name(), 111)))
                .andExpect(status().isUnauthorized());

        assertThat(communityTestSupport.lerColunaDaComunidade(cenario.comunidade().slug(), "price_monthly_cents"))
                .isEqualTo(500);
    }

    @Test
    void visitanteNaoEditaAComunidadeDevolve403() throws Exception {
        Cenario cenario = criarCenario("4u");
        AuthResponse visitante = visitante("4u");

        mockMvc.perform(put("/api/communities/" + cenario.comunidade().slug())
                        .header("Authorization", "Bearer " + visitante.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoEdicao(cenario.comunidade().name(), 111)))
                .andExpect(status().isForbidden());

        assertThat(communityTestSupport.lerColunaDaComunidade(cenario.comunidade().slug(), "price_monthly_cents"))
                .isEqualTo(500);
    }

    @Test
    void membroAtivoNaoEditaAComunidadeDevolve403() throws Exception {
        Cenario cenario = criarCenario("4v");
        AuthResponse membro = membroAtivo("4v", cenario.comunidade());

        mockMvc.perform(put("/api/communities/" + cenario.comunidade().slug())
                        .header("Authorization", "Bearer " + membro.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoEdicao(cenario.comunidade().name(), 111)))
                .andExpect(status().isForbidden());

        assertThat(communityTestSupport.lerColunaDaComunidade(cenario.comunidade().slug(), "price_monthly_cents"))
                .isEqualTo(500);
    }

    @Test
    void exMembroNaoEditaAComunidadeDevolve403() throws Exception {
        Cenario cenario = criarCenario("4w");
        AuthResponse exMembro = exMembro("4w", cenario.comunidade());

        mockMvc.perform(put("/api/communities/" + cenario.comunidade().slug())
                        .header("Authorization", "Bearer " + exMembro.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoEdicao(cenario.comunidade().name(), 111)))
                .andExpect(status().isForbidden());

        assertThat(communityTestSupport.lerColunaDaComunidade(cenario.comunidade().slug(), "price_monthly_cents"))
                .isEqualTo(500);
    }

    @Test
    void donoEditaAComunidadeDevolve200() throws Exception {
        Cenario cenario = criarCenario("4x");
        String novoNome = communityTestSupport.nomeUnico("Comunidade 4X Editada");

        mockMvc.perform(put("/api/communities/" + cenario.comunidade().slug())
                        .header("Authorization", "Bearer " + cenario.dono().accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoEdicao(novoNome, 777)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(novoNome))
                .andExpect(jsonPath("$.priceMonthlyCents").value(777));

        assertThat(communityTestSupport.lerColunaDaComunidade(cenario.comunidade().slug(), "price_monthly_cents"))
                .isEqualTo(777);
    }

    // ---- POST /api/communities/{slug}/moderators (2e/4e, 4a–4g) ----

    @Test
    void anonimoNaoNomeiaModeradorDevolve401() throws Exception {
        Cenario cenario = criarCenario("4a");
        AuthResponse membro = membroAtivo("4a", cenario.comunidade());

        mockMvc.perform(post("/api/communities/" + cenario.comunidade().slug() + "/moderators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNomeacao(membro.user().id())))
                .andExpect(status().isUnauthorized());

        assertThat(communityTestSupport.lerMembership(cenario.comunidade().id(), membro.user().id()).get("role"))
                .isEqualTo("MEMBER");
    }

    @Test
    void visitanteNaoNomeiaModeradorDevolve403() throws Exception {
        Cenario cenario = criarCenario("4b");
        AuthResponse visitante = visitante("4b");
        AuthResponse membro = membroAtivo("4b", cenario.comunidade());

        mockMvc.perform(post("/api/communities/" + cenario.comunidade().slug() + "/moderators")
                        .header("Authorization", "Bearer " + visitante.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNomeacao(membro.user().id())))
                .andExpect(status().isForbidden());

        assertThat(communityTestSupport.lerMembership(cenario.comunidade().id(), membro.user().id()).get("role"))
                .isEqualTo("MEMBER");
    }

    @Test
    void membroAtivoNaoNomeiaModeradorDevolve403() throws Exception {
        Cenario cenario = criarCenario("4c");
        AuthResponse membro = membroAtivo("4c", cenario.comunidade());
        AuthResponse outroMembro = membroAtivo("4c-outro", cenario.comunidade());

        mockMvc.perform(post("/api/communities/" + cenario.comunidade().slug() + "/moderators")
                        .header("Authorization", "Bearer " + membro.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNomeacao(outroMembro.user().id())))
                .andExpect(status().isForbidden());

        assertThat(communityTestSupport.lerMembership(cenario.comunidade().id(), outroMembro.user().id()).get("role"))
                .isEqualTo("MEMBER");
    }

    @Test
    void exMembroNaoNomeiaModeradorDevolve403() throws Exception {
        Cenario cenario = criarCenario("4d");
        AuthResponse exMembro = exMembro("4d", cenario.comunidade());
        AuthResponse membro = membroAtivo("4d-alvo", cenario.comunidade());

        mockMvc.perform(post("/api/communities/" + cenario.comunidade().slug() + "/moderators")
                        .header("Authorization", "Bearer " + exMembro.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNomeacao(membro.user().id())))
                .andExpect(status().isForbidden());

        assertThat(communityTestSupport.lerMembership(cenario.comunidade().id(), membro.user().id()).get("role"))
                .isEqualTo("MEMBER");
    }

    @Test
    void moderadorNaoNomeiaOutroModeradorDevolve403() throws Exception {
        Cenario cenario = criarCenario("2e4e");
        AuthResponse moderador = moderador("2e4e", cenario.comunidade());
        AuthResponse membro = membroAtivo("2e4e-alvo", cenario.comunidade());

        mockMvc.perform(post("/api/communities/" + cenario.comunidade().slug() + "/moderators")
                        .header("Authorization", "Bearer " + moderador.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNomeacao(membro.user().id())))
                .andExpect(status().isForbidden());

        assertThat(communityTestSupport.lerMembership(cenario.comunidade().id(), membro.user().id()).get("role"))
                .isEqualTo("MEMBER");
    }

    @Test
    void donoNomeiaModeradorDevolve201() throws Exception {
        Cenario cenario = criarCenario("4f");
        AuthResponse membro = membroAtivo("4f", cenario.comunidade());

        mockMvc.perform(post("/api/communities/" + cenario.comunidade().slug() + "/moderators")
                        .header("Authorization", "Bearer " + cenario.dono().accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNomeacao(membro.user().id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MODERATOR"));

        assertThat(communityTestSupport.lerMembership(cenario.comunidade().id(), membro.user().id()).get("role"))
                .isEqualTo("MODERATOR");
    }

    @Test
    void adminGlobalSemRelacaoNaoNomeiaModeradorDevolve403() throws Exception {
        Cenario cenario = criarCenario("4g");
        AuthResponse membro = membroAtivo("4g", cenario.comunidade());
        AuthResponse admin = authTestSupport.registarAdmin(authTestSupport.emailUnico("admin-4g"), PASSWORD_VALIDA);

        mockMvc.perform(post("/api/communities/" + cenario.comunidade().slug() + "/moderators")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNomeacao(membro.user().id())))
                .andExpect(status().isForbidden());

        assertThat(communityTestSupport.lerMembership(cenario.comunidade().id(), membro.user().id()).get("role"))
                .isEqualTo("MEMBER");
    }

    // ---- DELETE /api/communities/{slug}/moderators/{userId} (2f/4l, 4h–4m) ----

    @Test
    void anonimoNaoRemoveModeradorDevolve401() throws Exception {
        Cenario cenario = criarCenario("4h");
        AuthResponse moderador = moderador("4h", cenario.comunidade());

        mockMvc.perform(delete("/api/communities/" + cenario.comunidade().slug() + "/moderators/" + moderador.user().id()))
                .andExpect(status().isUnauthorized());

        assertThat(communityTestSupport.lerMembership(cenario.comunidade().id(), moderador.user().id()).get("role"))
                .isEqualTo("MODERATOR");
    }

    @Test
    void visitanteNaoRemoveModeradorDevolve403() throws Exception {
        Cenario cenario = criarCenario("4i");
        AuthResponse visitante = visitante("4i");
        AuthResponse moderador = moderador("4i", cenario.comunidade());

        mockMvc.perform(delete("/api/communities/" + cenario.comunidade().slug() + "/moderators/" + moderador.user().id())
                        .header("Authorization", "Bearer " + visitante.accessToken()))
                .andExpect(status().isForbidden());

        assertThat(communityTestSupport.lerMembership(cenario.comunidade().id(), moderador.user().id()).get("role"))
                .isEqualTo("MODERATOR");
    }

    @Test
    void membroAtivoNaoRemoveModeradorDevolve403() throws Exception {
        Cenario cenario = criarCenario("4j");
        AuthResponse membro = membroAtivo("4j", cenario.comunidade());
        AuthResponse moderador = moderador("4j", cenario.comunidade());

        mockMvc.perform(delete("/api/communities/" + cenario.comunidade().slug() + "/moderators/" + moderador.user().id())
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isForbidden());

        assertThat(communityTestSupport.lerMembership(cenario.comunidade().id(), moderador.user().id()).get("role"))
                .isEqualTo("MODERATOR");
    }

    @Test
    void exMembroNaoRemoveModeradorDevolve403() throws Exception {
        Cenario cenario = criarCenario("4k");
        AuthResponse exMembro = exMembro("4k", cenario.comunidade());
        AuthResponse moderador = moderador("4k", cenario.comunidade());

        mockMvc.perform(delete("/api/communities/" + cenario.comunidade().slug() + "/moderators/" + moderador.user().id())
                        .header("Authorization", "Bearer " + exMembro.accessToken()))
                .andExpect(status().isForbidden());

        assertThat(communityTestSupport.lerMembership(cenario.comunidade().id(), moderador.user().id()).get("role"))
                .isEqualTo("MODERATOR");
    }

    @Test
    void moderadorNaoRemoveModeradorDevolve403() throws Exception {
        Cenario cenario = criarCenario("2f4l");
        AuthResponse moderador = moderador("2f4l", cenario.comunidade());
        AuthResponse outroModerador = moderador("2f4l-outro", cenario.comunidade());

        mockMvc.perform(delete("/api/communities/" + cenario.comunidade().slug() + "/moderators/" + outroModerador.user().id())
                        .header("Authorization", "Bearer " + moderador.accessToken()))
                .andExpect(status().isForbidden());

        assertThat(communityTestSupport.lerMembership(cenario.comunidade().id(), outroModerador.user().id()).get("role"))
                .isEqualTo("MODERATOR");
    }

    @Test
    void donoRemoveModeradorDevolve200() throws Exception {
        Cenario cenario = criarCenario("4m");
        AuthResponse moderador = moderador("4m", cenario.comunidade());

        mockMvc.perform(delete("/api/communities/" + cenario.comunidade().slug() + "/moderators/" + moderador.user().id())
                        .header("Authorization", "Bearer " + cenario.dono().accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));

        assertThat(communityTestSupport.lerMembership(cenario.comunidade().id(), moderador.user().id()).get("role"))
                .isEqualTo("MEMBER");
    }

    // ---- GET /api/communities/{slug}/members (4n–4s) ----

    @Test
    void anonimoNaoListaMembrosDevolve401() throws Exception {
        Cenario cenario = criarCenario("4n");

        mockMvc.perform(get("/api/communities/" + cenario.comunidade().slug() + "/members"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void visitanteNaoListaMembrosDevolve403() throws Exception {
        Cenario cenario = criarCenario("4o");
        AuthResponse visitante = visitante("4o");

        mockMvc.perform(get("/api/communities/" + cenario.comunidade().slug() + "/members")
                        .header("Authorization", "Bearer " + visitante.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void membroAtivoNaoListaMembrosDevolve403() throws Exception {
        Cenario cenario = criarCenario("4p");
        AuthResponse membro = membroAtivo("4p", cenario.comunidade());

        mockMvc.perform(get("/api/communities/" + cenario.comunidade().slug() + "/members")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void exMembroNaoListaMembrosDevolve403() throws Exception {
        Cenario cenario = criarCenario("4q");
        AuthResponse exMembro = exMembro("4q", cenario.comunidade());

        mockMvc.perform(get("/api/communities/" + cenario.comunidade().slug() + "/members")
                        .header("Authorization", "Bearer " + exMembro.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderadorNaoListaMembrosDevolve403() throws Exception {
        Cenario cenario = criarCenario("4r");
        AuthResponse moderador = moderador("4r", cenario.comunidade());

        mockMvc.perform(get("/api/communities/" + cenario.comunidade().slug() + "/members")
                        .header("Authorization", "Bearer " + moderador.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void donoListaMembrosDevolve200() throws Exception {
        Cenario cenario = criarCenario("4s");
        AuthResponse membro = membroAtivo("4s", cenario.comunidade());

        mockMvc.perform(get("/api/communities/" + cenario.comunidade().slug() + "/members")
                        .header("Authorization", "Bearer " + cenario.dono().accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
