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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 1a, 1b, 1d, 1e, 1f, 1g, 2a, 2b, 5a, 5b, X4 — criação de comunidades (R2, critérios 1, 2 e 5). */
class CommunityCreationIT extends AbstractIntegrationTest {

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
    void criarComunidadeComNomeValidoDevolve201ComSlugDerivadoDoNome() throws Exception {
        AuthResponse sessao = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-1a"), PASSWORD_VALIDA);
        String nome = communityTestSupport.nomeUnico("Tips do Ze");

        mockMvc.perform(post("/api/communities")
                        .header("Authorization", "Bearer " + sessao.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", nome, "priceMonthlyCents", 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(nome))
                .andExpect(jsonPath("$.slug").value(pt.seerhub.community.service.SlugGenerator.base(nome)))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.ownedByViewer").value(true));
    }

    @Test
    void nomesRepetidosGeramSlugsComSufixoNumericoIncremental() throws Exception {
        AuthResponse sessao = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-1b"), PASSWORD_VALIDA);
        String nome = communityTestSupport.nomeUnico("Nome Repetido");
        String slugBase = pt.seerhub.community.service.SlugGenerator.base(nome);

        CommunityResponse primeira = communityTestSupport.criar(sessao.accessToken(), nome);
        CommunityResponse segunda = communityTestSupport.criar(sessao.accessToken(), nome);
        CommunityResponse terceira = communityTestSupport.criar(sessao.accessToken(), nome);

        assertThat(primeira.slug()).isEqualTo(slugBase);
        assertThat(segunda.slug()).isEqualTo(slugBase + "-2");
        assertThat(terceira.slug()).isEqualTo(slugBase + "-3");

        Integer contagem = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM communities WHERE slug IN (?, ?, ?)", Integer.class,
                primeira.slug(), segunda.slug(), terceira.slug());
        assertThat(contagem).isEqualTo(3);
    }

    @Test
    void nomeComMenosDeTresCaracteresDevolve400ENaoPersiste() throws Exception {
        AuthResponse sessao = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-1d"), PASSWORD_VALIDA);

        mockMvc.perform(post("/api/communities")
                        .header("Authorization", "Bearer " + sessao.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "ab", "priceMonthlyCents", 0))))
                .andExpect(status().isBadRequest());

        Integer contagem = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM communities WHERE name = ?", Integer.class, "ab");
        assertThat(contagem).isEqualTo(0);
    }

    @Test
    void nomeComMaisDeSessentaCaracteresDevolve400ENaoPersiste() throws Exception {
        AuthResponse sessao = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-1e"), PASSWORD_VALIDA);
        String nomeDemasiadoLongo = "a".repeat(61);

        mockMvc.perform(post("/api/communities")
                        .header("Authorization", "Bearer " + sessao.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", nomeDemasiadoLongo, "priceMonthlyCents", 0))))
                .andExpect(status().isBadRequest());

        Integer contagem = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM communities WHERE name = ?", Integer.class, nomeDemasiadoLongo);
        assertThat(contagem).isEqualTo(0);
    }

    @Test
    void criarComunidadeSemTokenDevolve401() throws Exception {
        mockMvc.perform(post("/api/communities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Comunidade Sem Token", "priceMonthlyCents", 0))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void nomesNosLimitesDeTresESessentaCaracteresSaoAceites() throws Exception {
        AuthResponse sessao = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-1g"), PASSWORD_VALIDA);
        // 3 caracteres exatos, único por corrida.
        String nomeMinimo = "A" + java.util.UUID.randomUUID().toString().substring(0, 2);
        // 60 caracteres exatos, único por corrida (sufixo de 8 caracteres no fim).
        String sufixo = java.util.UUID.randomUUID().toString().substring(0, 8);
        String nomeMaximo = "n".repeat(60 - sufixo.length() - 1) + "-" + sufixo;

        mockMvc.perform(post("/api/communities")
                        .header("Authorization", "Bearer " + sessao.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", nomeMinimo, "priceMonthlyCents", 0))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/communities")
                        .header("Authorization", "Bearer " + sessao.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", nomeMaximo, "priceMonthlyCents", 0))))
                .andExpect(status().isCreated());
    }

    @Test
    void criadorFicaComMembershipOwnerAtivaSemDataDeExpiracao() throws Exception {
        AuthResponse sessao = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-2a"), PASSWORD_VALIDA);
        String nome = communityTestSupport.nomeUnico("Comunidade Dois A");

        CommunityResponse resposta = communityTestSupport.criar(sessao.accessToken(), nome);

        Map<String, Object> membership = jdbcTemplate.queryForMap(
                "SELECT community_id, user_id, role, status, expires_at, version "
                        + "FROM community_memberships WHERE community_id = ? AND user_id = ?",
                resposta.id(), sessao.user().id());

        assertThat(membership.get("role")).isEqualTo("OWNER");
        assertThat(membership.get("status")).isEqualTo("ACTIVE");
        assertThat(membership.get("expires_at")).isNull();
        assertThat(((Number) membership.get("version")).longValue()).isZero();
        assertThat(((Number) membership.get("community_id")).longValue()).isEqualTo(resposta.id());
        assertThat(((Number) membership.get("user_id")).longValue()).isEqualTo(sessao.user().id());
    }

    @Test
    void comunidadeCriadaApareceEmAsMinhasComunidadesComoDono() throws Exception {
        AuthResponse sessao = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-2b"), PASSWORD_VALIDA);
        String nome = communityTestSupport.nomeUnico("Comunidade Dois B");

        CommunityResponse resposta = communityTestSupport.criar(sessao.accessToken(), nome);

        mockMvc.perform(get("/api/me/communities")
                        .header("Authorization", "Bearer " + sessao.accessToken()))
                .andExpect(status().isOk())
                // Utilizador novo, criado só para este teste: a única comunidade sua é a acabada de criar.
                .andExpect(jsonPath("$[0].slug").value(resposta.slug()))
                .andExpect(jsonPath("$[0].ownedByViewer").value(true));
    }

    @Test
    void criarUmaQuartaComunidadeAtivaDevolve409ENaoPersisteNada() throws Exception {
        AuthResponse sessao = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-5a"), PASSWORD_VALIDA);

        communityTestSupport.criar(sessao.accessToken(), communityTestSupport.nomeUnico("Comunidade Um"));
        communityTestSupport.criar(sessao.accessToken(), communityTestSupport.nomeUnico("Comunidade Dois"));
        communityTestSupport.criar(sessao.accessToken(), communityTestSupport.nomeUnico("Comunidade Tres"));

        String nomeQuarta = communityTestSupport.nomeUnico("Comunidade Quatro");

        mockMvc.perform(post("/api/communities")
                        .header("Authorization", "Bearer " + sessao.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", nomeQuarta, "priceMonthlyCents", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(CommunityService.MENSAGEM_LIMITE_ATINGIDO));

        Integer contagemComunidades = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM communities WHERE name = ?", Integer.class, nomeQuarta);
        assertThat(contagemComunidades).isEqualTo(0);

        Integer contagemAtivas = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM communities WHERE owner_id = ? AND status = 'ACTIVE'",
                Integer.class, sessao.user().id());
        assertThat(contagemAtivas).isEqualTo(3);
    }

    @Test
    void comunidadeSuspensaNaoContaParaOLimiteDeTresAtivas() throws Exception {
        AuthResponse sessao = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-5b"), PASSWORD_VALIDA);

        communityTestSupport.criar(sessao.accessToken(), communityTestSupport.nomeUnico("Comunidade Um"));
        communityTestSupport.criar(sessao.accessToken(), communityTestSupport.nomeUnico("Comunidade Dois"));
        CommunityResponse terceira = communityTestSupport.criar(
                sessao.accessToken(), communityTestSupport.nomeUnico("Comunidade Tres"));
        communityTestSupport.suspender(terceira.slug());

        String nomeQuarta = communityTestSupport.nomeUnico("Comunidade Quatro Permitida");

        mockMvc.perform(post("/api/communities")
                        .header("Authorization", "Bearer " + sessao.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", nomeQuarta, "priceMonthlyCents", 0))))
                .andExpect(status().isCreated());
    }

    @Test
    void moedaFicaEurNaBaseDeDadosSemEstarMapeadaNaEntidade() throws Exception {
        AuthResponse sessao = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-x4"), PASSWORD_VALIDA);
        String nome = communityTestSupport.nomeUnico("Comunidade Moeda");

        CommunityResponse resposta = communityTestSupport.criar(sessao.accessToken(), nome);

        assertThat(resposta.currency()).isEqualTo("EUR");
        String moedaGravada = (String) communityTestSupport.lerColunaDaComunidade(resposta.slug(), "currency");
        assertThat(moedaGravada.trim()).isEqualTo("EUR");
    }
}
