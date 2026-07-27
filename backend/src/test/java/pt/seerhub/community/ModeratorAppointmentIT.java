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
import pt.seerhub.community.service.ModerationService;
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

/** 1a–1l — nomeação e remoção de moderadores (R4, critério 1). */
class ModeratorAppointmentIT extends AbstractIntegrationTest {

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

    private AuthResponse novoUtilizador(String prefixo) throws Exception {
        return authTestSupport.registarEAutenticar(authTestSupport.emailUnico(prefixo), PASSWORD_VALIDA);
    }

    private String corpoNomeacao(Long userId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("userId", userId));
    }

    @Test
    void donoNomeiaModeradorDeEntreOsMembrosAtivos() throws Exception {
        AuthResponse dono = novoUtilizador("dono-1a");
        CommunityResponse comunidade = communityTestSupport.criar(dono.accessToken(), communityTestSupport.nomeUnico("Comunidade 1A"));

        AuthResponse membro = novoUtilizador("membro-1a");
        communityTestSupport.inserirMembership(comunidade.id(), membro.user().id(), "MEMBER", "ACTIVE",
                Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/moderators")
                        .header("Authorization", "Bearer " + dono.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNomeacao(membro.user().id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(membro.user().id()))
                .andExpect(jsonPath("$.role").value("MODERATOR"))
                .andExpect(jsonPath("$.active").value(true));

        Map<String, Object> linha = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());
        assertThat(linha.get("role")).isEqualTo("MODERATOR");
    }

    @Test
    void nomearModeradorNaoAlteraStatusNemExpiresAt() throws Exception {
        AuthResponse dono = novoUtilizador("dono-1b");
        CommunityResponse comunidade = communityTestSupport.criar(dono.accessToken(), communityTestSupport.nomeUnico("Comunidade 1B"));

        AuthResponse membro = novoUtilizador("membro-1b");
        Instant expiraEm = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        communityTestSupport.inserirMembership(comunidade.id(), membro.user().id(), "MEMBER", "ACTIVE", expiraEm);

        Map<String, Object> antes = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/moderators")
                        .header("Authorization", "Bearer " + dono.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNomeacao(membro.user().id())))
                .andExpect(status().isCreated());

        Map<String, Object> depois = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());
        assertThat(depois.get("status")).isEqualTo(antes.get("status"));
        assertThat(depois.get("expires_at")).isEqualTo(antes.get("expires_at"));
        assertThat(depois.get("role")).isEqualTo("MODERATOR").isNotEqualTo(antes.get("role"));
    }

    @Test
    void donoRemoveModeradorQueVoltaAMembro() throws Exception {
        AuthResponse dono = novoUtilizador("dono-1c");
        CommunityResponse comunidade = communityTestSupport.criar(dono.accessToken(), communityTestSupport.nomeUnico("Comunidade 1C"));

        AuthResponse moderador = novoUtilizador("moderador-1c");
        communityTestSupport.inserirMembership(comunidade.id(), moderador.user().id(), "MODERATOR", "ACTIVE",
                Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(delete("/api/communities/" + comunidade.slug() + "/moderators/" + moderador.user().id())
                        .header("Authorization", "Bearer " + dono.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(moderador.user().id()))
                .andExpect(jsonPath("$.role").value("MEMBER"));

        Map<String, Object> linha = communityTestSupport.lerMembership(comunidade.id(), moderador.user().id());
        assertThat(linha.get("role")).isEqualTo("MEMBER");
    }

    @Test
    void despromocaoDevolveOUtilizadorAoEstadoDaSubscricaoSubjacente() throws Exception {
        AuthResponse dono = novoUtilizador("dono-1d");
        CommunityResponse comunidade = communityTestSupport.criar(dono.accessToken(), communityTestSupport.nomeUnico("Comunidade 1D"));

        AuthResponse moderador = novoUtilizador("moderador-1d");
        Instant expiradoNoPassado = Instant.now().minus(1, ChronoUnit.DAYS);
        communityTestSupport.inserirMembership(comunidade.id(), moderador.user().id(), "MODERATOR", "ACTIVE",
                expiradoNoPassado);

        mockMvc.perform(delete("/api/communities/" + comunidade.slug() + "/moderators/" + moderador.user().id())
                        .header("Authorization", "Bearer " + dono.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.active").value(false));

        // A prova real: sem acesso premium (não é gestor, e a data já passou).
        mockMvc.perform(get("/api/communities/" + comunidade.slug() + "/member-area")
                        .header("Authorization", "Bearer " + moderador.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void nomearAlguemQueNaoEMembroDevolve409() throws Exception {
        AuthResponse dono = novoUtilizador("dono-1e");
        CommunityResponse comunidade = communityTestSupport.criar(dono.accessToken(), communityTestSupport.nomeUnico("Comunidade 1E"));
        AuthResponse estranho = novoUtilizador("estranho-1e");

        long contagemAntes = communityTestSupport.contarMemberships(comunidade.id());

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/moderators")
                        .header("Authorization", "Bearer " + dono.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNomeacao(estranho.user().id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(ModerationService.MENSAGEM_NAO_E_MEMBRO_ATIVO));

        assertThat(communityTestSupport.contarMemberships(comunidade.id())).isEqualTo(contagemAntes);
    }

    @Test
    void nomearMembroComSubscricaoExpiradaDevolve409() throws Exception {
        AuthResponse dono = novoUtilizador("dono-1f");
        CommunityResponse comunidade = communityTestSupport.criar(dono.accessToken(), communityTestSupport.nomeUnico("Comunidade 1F"));

        AuthResponse membro = novoUtilizador("membro-1f");
        communityTestSupport.inserirMembership(comunidade.id(), membro.user().id(), "MEMBER", "EXPIRED",
                Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/moderators")
                        .header("Authorization", "Bearer " + dono.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNomeacao(membro.user().id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(ModerationService.MENSAGEM_NAO_E_MEMBRO_ATIVO));

        Map<String, Object> linha = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());
        assertThat(linha.get("role")).isEqualTo("MEMBER");
    }

    @Test
    void nomearAlguemQueJaEModeradorDevolve409() throws Exception {
        AuthResponse dono = novoUtilizador("dono-1g");
        CommunityResponse comunidade = communityTestSupport.criar(dono.accessToken(), communityTestSupport.nomeUnico("Comunidade 1G"));

        AuthResponse moderador = novoUtilizador("moderador-1g");
        communityTestSupport.inserirMembership(comunidade.id(), moderador.user().id(), "MODERATOR", "ACTIVE",
                Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/moderators")
                        .header("Authorization", "Bearer " + dono.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNomeacao(moderador.user().id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(ModerationService.MENSAGEM_JA_E_MODERADOR));
    }

    @Test
    void nomearODonoDevolve409() throws Exception {
        AuthResponse dono = novoUtilizador("dono-1h");
        CommunityResponse comunidade = communityTestSupport.criar(dono.accessToken(), communityTestSupport.nomeUnico("Comunidade 1H"));

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/moderators")
                        .header("Authorization", "Bearer " + dono.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNomeacao(dono.user().id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(ModerationService.MENSAGEM_E_O_DONO));
    }

    @Test
    void nomearUtilizadorInexistenteDevolve409ComAMesmaMensagem() throws Exception {
        AuthResponse dono = novoUtilizador("dono-1i");
        CommunityResponse comunidade = communityTestSupport.criar(dono.accessToken(), communityTestSupport.nomeUnico("Comunidade 1I"));

        long userIdInexistente = 999_999_999L;

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/moderators")
                        .header("Authorization", "Bearer " + dono.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNomeacao(userIdInexistente)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(ModerationService.MENSAGEM_NAO_E_MEMBRO_ATIVO));
    }

    @Test
    void removerAlguemQueNaoEModeradorDevolve409() throws Exception {
        AuthResponse dono = novoUtilizador("dono-1j");
        CommunityResponse comunidade = communityTestSupport.criar(dono.accessToken(), communityTestSupport.nomeUnico("Comunidade 1J"));

        AuthResponse membro = novoUtilizador("membro-1j");
        communityTestSupport.inserirMembership(comunidade.id(), membro.user().id(), "MEMBER", "ACTIVE",
                Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(delete("/api/communities/" + comunidade.slug() + "/moderators/" + membro.user().id())
                        .header("Authorization", "Bearer " + dono.accessToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(ModerationService.MENSAGEM_NAO_E_MODERADOR));
    }

    @Test
    void listaDeMembrosIncluiODonoMesmoSemLinhaDeMembership() throws Exception {
        AuthResponse dono = novoUtilizador("dono-1k");
        CommunityResponse comunidade = communityTestSupport.criar(dono.accessToken(), communityTestSupport.nomeUnico("Comunidade 1K"));

        AuthResponse membro = novoUtilizador("membro-1k");
        communityTestSupport.inserirMembership(comunidade.id(), membro.user().id(), "MEMBER", "ACTIVE",
                Instant.now().plus(30, ChronoUnit.DAYS));

        // Simula o fallback sintético de F03: o dono sem nenhuma linha de membership.
        communityTestSupport.apagarMembership(comunidade.id(), dono.user().id());

        mockMvc.perform(get("/api/communities/" + comunidade.slug() + "/members")
                        .header("Authorization", "Bearer " + dono.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.userId == " + dono.user().id() + ")].role").value("OWNER"))
                .andExpect(jsonPath("$[?(@.userId == " + membro.user().id() + ")].role").value("MEMBER"));
    }

    /**
     * D-4 de F01: o papel de comunidade nunca viaja no JWT — é lido da base
     * de dados a cada pedido. Este teste prova que a promoção tem efeito
     * imediato, com o access token literalmente o mesmo (nunca reemitido),
     * usando {@code /access} (que devolve o papel e as permissões) em vez de
     * {@code /members}, que é {@code MANAGE_MODERATORS} — só do dono, nunca
     * do moderador.
     */
    @Test
    void moderadorNomeadoGanhaAsPermissoesNoMesmoInstante() throws Exception {
        AuthResponse dono = novoUtilizador("dono-1l");
        CommunityResponse comunidade = communityTestSupport.criar(dono.accessToken(), communityTestSupport.nomeUnico("Comunidade 1L"));

        AuthResponse membro = novoUtilizador("membro-1l");
        communityTestSupport.inserirMembership(comunidade.id(), membro.user().id(), "MEMBER", "ACTIVE",
                Instant.now().plus(30, ChronoUnit.DAYS));

        // Antes da nomeação: papel MEMBER, sem PUBLISH_TIPS/SETTLE_TIPS.
        mockMvc.perform(get("/api/communities/" + comunidade.slug() + "/access")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.manager").value(false));

        mockMvc.perform(post("/api/communities/" + comunidade.slug() + "/moderators")
                        .header("Authorization", "Bearer " + dono.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNomeacao(membro.user().id())))
                .andExpect(status().isCreated());

        // Com o MESMO access token (nunca emitido de novo), já é MODERATOR.
        mockMvc.perform(get("/api/communities/" + comunidade.slug() + "/access")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MODERATOR"))
                .andExpect(jsonPath("$.manager").value(true))
                .andExpect(jsonPath("$.permissions", org.hamcrest.Matchers.hasItems("PUBLISH_TIPS", "SETTLE_TIPS")));
    }
}
