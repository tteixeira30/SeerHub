package pt.seerhub.community;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 3a–3h, 4a, X5, X6 — edição de comunidades (R2, critérios 3 e 4). */
class CommunityEditIT extends AbstractIntegrationTest {

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

    private Map<String, Object> corpoEdicao(String name, String description, String avatarUrl, String bannerUrl,
            int priceMonthlyCents) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("name", name);
        if (description != null) {
            corpo.put("description", description);
        }
        if (avatarUrl != null) {
            corpo.put("avatarUrl", avatarUrl);
        }
        if (bannerUrl != null) {
            corpo.put("bannerUrl", bannerUrl);
        }
        corpo.put("priceMonthlyCents", priceMonthlyCents);
        return corpo;
    }

    @Test
    void donoEditaNomeDescricaoAvatarBannerEPrecoEAsAlteracoesPersistem() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-3a"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Tres A"));

        String novoNome = communityTestSupport.nomeUnico("Comunidade Tres A Editada");
        String novaDescricao = "Descrição nova e completa.";
        String novoAvatar = "https://exemplo.pt/avatar.png";
        String novoBanner = "https://exemplo.pt/banner.png";

        mockMvc.perform(put("/api/communities/" + comunidade.slug())
                        .header("Authorization", "Bearer " + dono.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                corpoEdicao(novoNome, novaDescricao, novoAvatar, novoBanner, 999))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(novoNome))
                .andExpect(jsonPath("$.description").value(novaDescricao))
                .andExpect(jsonPath("$.avatarUrl").value(novoAvatar))
                .andExpect(jsonPath("$.bannerUrl").value(novoBanner))
                .andExpect(jsonPath("$.priceMonthlyCents").value(999));

        mockMvc.perform(get("/api/communities/" + comunidade.slug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(novoNome))
                .andExpect(jsonPath("$.description").value(novaDescricao))
                .andExpect(jsonPath("$.avatarUrl").value(novoAvatar))
                .andExpect(jsonPath("$.bannerUrl").value(novoBanner))
                .andExpect(jsonPath("$.priceMonthlyCents").value(999));
    }

    @Test
    void precoEGuardadoEmCentimosInteirosNaColunaInteger() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-3b"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Tres B"));

        mockMvc.perform(put("/api/communities/" + comunidade.slug())
                        .header("Authorization", "Bearer " + dono.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                corpoEdicao(comunidade.name(), null, null, null, 2599))))
                .andExpect(status().isOk());

        Integer precoGravado = (Integer) communityTestSupport.lerColunaDaComunidade(
                comunidade.slug(), "price_monthly_cents");
        assertThat(precoGravado).isEqualTo(2599);
        String moeda = ((String) communityTestSupport.lerColunaDaComunidade(comunidade.slug(), "currency")).trim();
        assertThat(moeda).isEqualTo("EUR");
    }

    @Test
    void precoNegativoDevolve400ENaoAlteraOValorGuardado() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-3c"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Tres C"), 700);

        mockMvc.perform(put("/api/communities/" + comunidade.slug())
                        .header("Authorization", "Bearer " + dono.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                corpoEdicao(comunidade.name(), null, null, null, -1))))
                .andExpect(status().isBadRequest());

        Integer precoGravado = (Integer) communityTestSupport.lerColunaDaComunidade(
                comunidade.slug(), "price_monthly_cents");
        assertThat(precoGravado).isEqualTo(700);
    }

    @Test
    void precoNaoInteiroDevolve400ENaoETruncado() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-3d"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Tres D"), 700);

        String corpoBruto = "{\"name\":\"" + comunidade.name() + "\",\"priceMonthlyCents\":12.50}";

        mockMvc.perform(put("/api/communities/" + comunidade.slug())
                        .header("Authorization", "Bearer " + dono.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoBruto))
                .andExpect(status().isBadRequest());

        Integer precoGravado = (Integer) communityTestSupport.lerColunaDaComunidade(
                comunidade.slug(), "price_monthly_cents");
        // Nem o valor antigo alterado, nem truncado para 12: continua exatamente 700.
        assertThat(precoGravado).isEqualTo(700).isNotEqualTo(12);
    }

    @Test
    void utilizadorQueNaoEDonoAEditarDevolve403ENaoAlteraNada() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-3e"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Tres E"), 300);
        String nomeOriginal = comunidade.name();

        AuthResponse intruso = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("intruso-3e"), PASSWORD_VALIDA);

        mockMvc.perform(put("/api/communities/" + comunidade.slug())
                        .header("Authorization", "Bearer " + intruso.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                corpoEdicao("Nome Roubado", null, null, null, 999999))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(CommunityService.MENSAGEM_SEM_PERMISSAO));

        String nomeGravado = (String) communityTestSupport.lerColunaDaComunidade(comunidade.slug(), "name");
        Integer precoGravado = (Integer) communityTestSupport.lerColunaDaComunidade(
                comunidade.slug(), "price_monthly_cents");
        assertThat(nomeGravado).isEqualTo(nomeOriginal);
        assertThat(precoGravado).isEqualTo(300);
    }

    @Test
    void editarSemTokenDevolve401() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-3f"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Tres F"));

        mockMvc.perform(put("/api/communities/" + comunidade.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                corpoEdicao(comunidade.name(), null, null, null, 0))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void editarComunidadeInexistenteDevolve404() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-3g"), PASSWORD_VALIDA);

        mockMvc.perform(put("/api/communities/slug-que-nao-existe-de-todo")
                        .header("Authorization", "Bearer " + dono.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                corpoEdicao("Nome Qualquer", null, null, null, 0))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Comunidade não encontrada."));
    }

    @Test
    void renomearNaoAlteraOSlug() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-3h"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Tres H"));
        String slugOriginal = comunidade.slug();

        String novoNome = communityTestSupport.nomeUnico("Nome Completamente Diferente");

        mockMvc.perform(put("/api/communities/" + slugOriginal)
                        .header("Authorization", "Bearer " + dono.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                corpoEdicao(novoNome, null, null, null, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(slugOriginal))
                .andExpect(jsonPath("$.name").value(novoNome));
    }

    /**
     * 4a — asserções obrigatórias da §3 do plano de F02: a fotografia da
     * membership do membro tem de ficar byte a byte intacta depois de
     * alterar o preço, incluindo a {@code version} (prova de que nenhum
     * UPDATE lhe tocou).
     */
    @Test
    void alterarOPrecoNaoMexeEmNenhumaMembershipJaAtiva() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-4a"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Quatro A"), 500);

        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-4a"), PASSWORD_VALIDA);
        Instant expiraEm = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        communityTestSupport.inserirMembership(comunidade.id(), membro.user().id(), "MEMBER", "ACTIVE", expiraEm);

        // 2. Fotografia antes da ação.
        Map<String, Object> antes = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());
        long contagemAntes = communityTestSupport.contarMemberships(comunidade.id());

        // 3. Ação: o dono altera o preço.
        mockMvc.perform(put("/api/communities/" + comunidade.slug())
                        .header("Authorization", "Bearer " + dono.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                corpoEdicao(comunidade.name(), null, null, null, 5000))))
                .andExpect(status().isOk());

        // 4. Prova de que a ação não foi vazia.
        Integer precoGravado = (Integer) communityTestSupport.lerColunaDaComunidade(
                comunidade.slug(), "price_monthly_cents");
        assertThat(precoGravado).isEqualTo(5000);

        // 5. Asserção central: a linha de membership fica byte a byte intacta, incluindo a version.
        Map<String, Object> depois = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());
        assertThat(depois).isEqualTo(antes);

        // 6. Asserção de contagem: nada foi criado nem apagado (dono + membro).
        long contagemDepois = communityTestSupport.contarMemberships(comunidade.id());
        assertThat(contagemDepois).isEqualTo(contagemAntes).isEqualTo(2);

        // 7. Asserção de acesso: o membro continua a receber 200.
        mockMvc.perform(get("/api/communities/" + comunidade.slug())
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceMonthlyCents").value(5000));
    }

    @Test
    void descricaoAcimaDoLimiteDevolve400() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-x5"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade X5"));
        String descricaoDemasiadoLonga = "a".repeat(2001);

        mockMvc.perform(put("/api/communities/" + comunidade.slug())
                        .header("Authorization", "Bearer " + dono.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                corpoEdicao(comunidade.name(), descricaoDemasiadoLonga, null, null, 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void avatarOuBannerQueNaoSejaUrlHttpDevolve400() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-x6"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade X6"));

        mockMvc.perform(put("/api/communities/" + comunidade.slug())
                        .header("Authorization", "Bearer " + dono.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                corpoEdicao(comunidade.name(), null, "ftp://exemplo.pt/avatar.png", null, 0))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/communities/" + comunidade.slug())
                        .header("Authorization", "Bearer " + dono.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                corpoEdicao(comunidade.name(), null, null, "javascript:alert(1)", 0))))
                .andExpect(status().isBadRequest());
    }
}
