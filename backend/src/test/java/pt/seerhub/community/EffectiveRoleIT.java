package pt.seerhub.community;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import pt.seerhub.community.api.CommunityResponse;
import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.AuthTestSupport;
import pt.seerhub.support.CommunityTestSupport;
import pt.seerhub.user.api.AuthResponse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 2c, 3d, 5a–5f — o papel efetivo e as permissões, visíveis na API a partir
 * de dados reais na base de dados (R4, critério 5 e secção 5 da spec).
 */
class EffectiveRoleIT extends AbstractIntegrationTest {

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

    @Test
    void acessoDeModeradorDevolvePublicarEResolverTips() throws Exception {
        AuthResponse dono = novoUtilizador("dono-2c");
        CommunityResponse comunidade = communityTestSupport.criar(dono.accessToken(), communityTestSupport.nomeUnico("Comunidade 2C"));

        AuthResponse moderador = novoUtilizador("moderador-2c");
        communityTestSupport.inserirMembership(comunidade.id(), moderador.user().id(), "MODERATOR", "ACTIVE",
                Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/communities/" + comunidade.slug() + "/access")
                        .header("Authorization", "Bearer " + moderador.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MODERATOR"))
                .andExpect(jsonPath("$.manager").value(true))
                .andExpect(jsonPath("$.permissions").isArray())
                .andExpect(jsonPath("$.permissions", org.hamcrest.Matchers.hasItems("PUBLISH_TIPS", "SETTLE_TIPS")));
    }

    @Test
    void perfilDaComunidadeDevolveOPapelEPermissoesDoMembro() throws Exception {
        AuthResponse dono = novoUtilizador("dono-5a");
        CommunityResponse comunidade = communityTestSupport.criar(dono.accessToken(), communityTestSupport.nomeUnico("Comunidade 5A"));

        AuthResponse membro = novoUtilizador("membro-5a");
        communityTestSupport.inserirMembership(comunidade.id(), membro.user().id(), "MEMBER", "ACTIVE",
                Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/communities/" + comunidade.slug())
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("MEMBER"))
                .andExpect(jsonPath("$.viewerPermissions", org.hamcrest.Matchers.containsInAnyOrder(
                        "READ_TIPS", "READ_CHAT", "WRITE_CHAT")));
    }

    @Test
    void perfilDaComunidadeParaAnonimoNaoTemPapelNemPermissoes() throws Exception {
        AuthResponse dono = novoUtilizador("dono-5b");
        CommunityResponse comunidade = communityTestSupport.criar(dono.accessToken(), communityTestSupport.nomeUnico("Comunidade 5B"));

        mockMvc.perform(get("/api/communities/" + comunidade.slug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").doesNotExist())
                .andExpect(jsonPath("$.viewerPermissions").isArray())
                .andExpect(jsonPath("$.viewerPermissions").isEmpty());
    }

    @Test
    void utilizadorAcumulaPapeisEmComunidadesDiferentes() throws Exception {
        AuthResponse utilizador = novoUtilizador("acumula-5d");

        // Dono de A.
        CommunityResponse comunidadeA = communityTestSupport.criar(utilizador.accessToken(), communityTestSupport.nomeUnico("Comunidade 5D A"));

        // Moderador de B (criada por outro dono).
        AuthResponse outroDono = novoUtilizador("outro-dono-5d");
        CommunityResponse comunidadeB = communityTestSupport.criar(outroDono.accessToken(), communityTestSupport.nomeUnico("Comunidade 5D B"));
        communityTestSupport.inserirMembership(comunidadeB.id(), utilizador.user().id(), "MODERATOR", "ACTIVE",
                Instant.now().plus(30, ChronoUnit.DAYS));

        // Membro de C (criada por outro dono).
        AuthResponse maisUmDono = novoUtilizador("mais-um-dono-5d");
        CommunityResponse comunidadeC = communityTestSupport.criar(maisUmDono.accessToken(), communityTestSupport.nomeUnico("Comunidade 5D C"));
        communityTestSupport.inserirMembership(comunidadeC.id(), utilizador.user().id(), "MEMBER", "ACTIVE",
                Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/me/community-roles")
                        .header("Authorization", "Bearer " + utilizador.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.communityId == " + comunidadeA.id() + ")].role").value("OWNER"))
                .andExpect(jsonPath("$[?(@.communityId == " + comunidadeB.id() + ")].role").value("MODERATOR"))
                .andExpect(jsonPath("$[?(@.communityId == " + comunidadeC.id() + ")].role").value("MEMBER"));
    }

    @Test
    void exMembroMantemOPapelMasPerdeAsPermissoes() throws Exception {
        AuthResponse dono = novoUtilizador("dono-5e");
        CommunityResponse comunidade = communityTestSupport.criar(dono.accessToken(), communityTestSupport.nomeUnico("Comunidade 5E"));

        AuthResponse exMembro = novoUtilizador("exmembro-5e");
        communityTestSupport.inserirMembership(comunidade.id(), exMembro.user().id(), "MEMBER", "EXPIRED",
                Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/communities/" + comunidade.slug())
                        .header("Authorization", "Bearer " + exMembro.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerRole").value("MEMBER"))
                .andExpect(jsonPath("$.viewerPermissions").isArray())
                .andExpect(jsonPath("$.viewerPermissions").isEmpty());
    }

    @Test
    void listagemDeComunidadesDevolveOPapelEfetivoEmCadaLinha() throws Exception {
        AuthResponse dono = novoUtilizador("dono-5f");
        CommunityResponse comunidade = communityTestSupport.criar(dono.accessToken(), communityTestSupport.nomeUnico("Comunidade 5F"));

        AuthResponse membro = novoUtilizador("membro-5f");
        communityTestSupport.inserirMembership(comunidade.id(), membro.user().id(), "MEMBER", "ACTIVE",
                Instant.now().plus(30, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/communities")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == '" + comunidade.slug() + "')].viewerRole").value("MEMBER"));

        mockMvc.perform(get("/api/communities")
                        .header("Authorization", "Bearer " + dono.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == '" + comunidade.slug() + "')].viewerRole").value("OWNER"));
    }
}
