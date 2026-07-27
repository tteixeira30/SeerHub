package pt.seerhub.community;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import pt.seerhub.community.api.CommunityResponse;
import pt.seerhub.community.service.CommunityService;
import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.AuthTestSupport;
import pt.seerhub.support.CommunityTestSupport;
import pt.seerhub.user.api.AuthResponse;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 6a, 6b, 6e, X1, X2, X3 — visibilidade de uma comunidade suspensa (R2, critério 6). */
class CommunityVisibilityIT extends AbstractIntegrationTest {

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
    void comunidadeSuspensaDesapareceDaListagemPublica() throws Exception {
        AuthResponse sessao = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-6a"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                sessao.accessToken(), communityTestSupport.nomeUnico("Comunidade Seis A"));

        // Presente antes de suspender.
        mockMvc.perform(get("/api/communities"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(comunidade.slug())));

        communityTestSupport.suspender(comunidade.slug());

        // Ausente depois de suspender.
        mockMvc.perform(get("/api/communities"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(comunidade.slug()))));
    }

    @Test
    void comunidadeSuspensaMantemLeituraAoMembroERecusaQuemNaoEMembro() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-6b"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Seis B"), 1234);
        communityTestSupport.suspender(comunidade.slug());

        // Metade "mantém acesso de leitura": o dono tem membership (OWNER) e continua a ler tudo.
        mockMvc.perform(get("/api/communities/" + comunidade.slug())
                        .header("Authorization", "Bearer " + dono.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(comunidade.name()))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.priceMonthlyCents").value(1234))
                .andExpect(jsonPath("$.slug").value(comunidade.slug()))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        // Metade "recusa novas subscrições": (i) autenticado sem membership → 404.
        AuthResponse estranho = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("estranho-6b"), PASSWORD_VALIDA);
        mockMvc.perform(get("/api/communities/" + comunidade.slug())
                        .header("Authorization", "Bearer " + estranho.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(CommunityService.MENSAGEM_COMUNIDADE_NAO_ENCONTRADA));

        // (ii) pedido anónimo → 404.
        mockMvc.perform(get("/api/communities/" + comunidade.slug()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(CommunityService.MENSAGEM_COMUNIDADE_NAO_ENCONTRADA));

        // (iii) o slug não consta da listagem pública.
        mockMvc.perform(get("/api/communities"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(comunidade.slug()))));
    }

    @Test
    void donoContinuaAVerEAEditarAComunidadeSuspensa() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-6e"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Seis E"));
        communityTestSupport.suspender(comunidade.slug());

        mockMvc.perform(get("/api/communities/" + comunidade.slug())
                        .header("Authorization", "Bearer " + dono.accessToken()))
                .andExpect(status().isOk());

        String novoNome = communityTestSupport.nomeUnico("Comunidade Seis E Editada");
        mockMvc.perform(put("/api/communities/" + comunidade.slug())
                        .header("Authorization", "Bearer " + dono.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", novoNome, "priceMonthlyCents", 500))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(novoNome))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void slugInexistenteDevolve404() throws Exception {
        mockMvc.perform(get("/api/communities/slug-que-nao-existe-de-todo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(CommunityService.MENSAGEM_COMUNIDADE_NAO_ENCONTRADA));
    }

    @Test
    void listagemPublicaEAcessivelSemAutenticacao() throws Exception {
        mockMvc.perform(get("/api/communities"))
                .andExpect(status().isOk());
    }

    @Test
    void asMinhasComunidadesSemTokenDevolve401() throws Exception {
        mockMvc.perform(get("/api/me/communities"))
                .andExpect(status().isUnauthorized());
    }
}
