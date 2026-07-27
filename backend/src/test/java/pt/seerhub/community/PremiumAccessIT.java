package pt.seerhub.community;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import pt.seerhub.community.api.CommunityResponse;
import pt.seerhub.community.service.CommunityAccessService;
import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.AuthTestSupport;
import pt.seerhub.support.CommunityTestSupport;
import pt.seerhub.user.api.AuthResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Critério 5 (dono/moderador atravessam sempre), critério 6 (403/401 na
 * porta premium e o endpoint de estado que nunca lança) e 4c
 * ({@code comunidadesComAcessoPremium}) — R3.
 *
 * <p>Todos os cenários são montados diretamente por
 * {@code CommunityTestSupport} (inserção/edição crua da linha de
 * membership), sem depender dos endpoints de subscrição de F03 (ainda por
 * escrever no momento em que esta classe nasceu — passo 3 da ordem de
 * implementação do plano).
 */
class PremiumAccessIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SeerHubProperties properties;
    @Autowired
    private CommunityAccessService communityAccessService;

    private AuthTestSupport authTestSupport;
    private CommunityTestSupport communityTestSupport;

    private static final String PASSWORD_VALIDA = "password-longa-123";

    @BeforeEach
    void montarSuporte() {
        authTestSupport = new AuthTestSupport(mockMvc, objectMapper, jdbcTemplate, properties);
        communityTestSupport = new CommunityTestSupport(mockMvc, objectMapper, jdbcTemplate);
    }

    @Test
    void donoTemAcessoPremiumSemSubscricao() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-5a"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Cinco A"));

        mockMvc.perform(get("/api/communities/" + comunidade.slug() + "/member-area")
                        .header("Authorization", "Bearer " + dono.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.slug").value(comunidade.slug()));
    }

    @Test
    void donoSemLinhaDeMembershipContinuaComAcessoPremium() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-5b"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Cinco B"));

        // Apaga a única linha de membership que existia (a OWNER criada por F02).
        communityTestSupport.apagarMembership(comunidade.id(), dono.user().id());
        assertThat(communityTestSupport.contarMemberships(comunidade.id())).isZero();

        mockMvc.perform(get("/api/communities/" + comunidade.slug() + "/member-area")
                        .header("Authorization", "Bearer " + dono.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void moderadorTemAcessoPremiumSemSubscricao() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-5c"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Cinco C"));

        AuthResponse moderador = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("moderador-5c"), PASSWORD_VALIDA);
        communityTestSupport.inserirMembership(comunidade.id(), moderador.user().id(), "MODERATOR", "ACTIVE", null);

        mockMvc.perform(get("/api/communities/" + comunidade.slug() + "/member-area")
                        .header("Authorization", "Bearer " + moderador.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MODERATOR"));
    }

    @Test
    void moderadorComLinhaExpiradaContinuaComAcessoPremium() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-5d"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Cinco D"));

        AuthResponse moderador = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("moderador-5d"), PASSWORD_VALIDA);
        Instant noPassado = Instant.now().minus(10, ChronoUnit.DAYS);
        communityTestSupport.inserirMembership(comunidade.id(), moderador.user().id(), "MODERATOR", "EXPIRED", noPassado);

        // O papel de gestão atravessa a porta independentemente do estado da subscrição (D-5).
        mockMvc.perform(get("/api/communities/" + comunidade.slug() + "/member-area")
                        .header("Authorization", "Bearer " + moderador.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MODERATOR"));
    }

    @Test
    void pedidoAConteudoPremiumComMembershipExpiradaDevolve403() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-6a"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Seis A"));

        AuthResponse exMembro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("ex-membro-6a"), PASSWORD_VALIDA);
        Instant noPassado = Instant.now().minus(1, ChronoUnit.DAYS);
        communityTestSupport.inserirMembership(comunidade.id(), exMembro.user().id(), "MEMBER", "EXPIRED", noPassado);

        mockMvc.perform(get("/api/communities/" + comunidade.slug() + "/member-area")
                        .header("Authorization", "Bearer " + exMembro.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail")
                        .value(pt.seerhub.community.service.CommunityAccessService.MENSAGEM_SEM_ACESSO_PREMIUM));

        // §10/§8 do plano: expirar nunca apaga nada — o perfil público continua a 200 ao ex-membro.
        mockMvc.perform(get("/api/communities/" + comunidade.slug())
                        .header("Authorization", "Bearer " + exMembro.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void membershipAtivaComDataNoPassadoJaNaoDaAcessoAntesDaTarefaCorrer() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-6b"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Seis B"));

        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-6b"), PASSWORD_VALIDA);
        Instant noPassado = Instant.now().minus(1, ChronoUnit.DAYS);
        // Ainda ACTIVE na coluna — a tarefa diária ainda não correu (D-3).
        communityTestSupport.inserirMembership(comunidade.id(), membro.user().id(), "MEMBER", "ACTIVE", noPassado);

        mockMvc.perform(get("/api/communities/" + comunidade.slug() + "/member-area")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void utilizadorSemMembershipDevolve403() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-6c"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Seis C"));

        AuthResponse estranho = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("estranho-6c"), PASSWORD_VALIDA);

        mockMvc.perform(get("/api/communities/" + comunidade.slug() + "/member-area")
                        .header("Authorization", "Bearer " + estranho.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void conteudoPremiumSemTokenDevolve401() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-6d"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Seis D"));

        mockMvc.perform(get("/api/communities/" + comunidade.slug() + "/member-area"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acessoExpostoAoClienteIdentificaSubscricaoExpirada() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-6e"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Seis E"));

        AuthResponse exMembro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("ex-membro-6e"), PASSWORD_VALIDA);
        Instant noPassado = Instant.now().minus(2, ChronoUnit.DAYS);
        communityTestSupport.inserirMembership(comunidade.id(), exMembro.user().id(), "MEMBER", "EXPIRED", noPassado);

        // /access nunca dá 403 — é o que o cliente usa para desenhar o ecrã de re-subscrição.
        mockMvc.perform(get("/api/communities/" + comunidade.slug() + "/access")
                        .header("Authorization", "Bearer " + exMembro.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.premium").value(false))
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void areaDeMembroDeComunidadeSuspensaDevolve404AQuemNaoEMembro() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-6f"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Seis F"));
        communityTestSupport.suspender(comunidade.slug());

        AuthResponse estranho = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("estranho-6f"), PASSWORD_VALIDA);

        mockMvc.perform(get("/api/communities/" + comunidade.slug() + "/member-area")
                        .header("Authorization", "Bearer " + estranho.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void membroDeComunidadeSuspensaMantemAcessoPremium() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-6g"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Seis G"));

        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-6g"), PASSWORD_VALIDA);
        Instant noFuturo = Instant.now().plus(20, ChronoUnit.DAYS);
        communityTestSupport.inserirMembership(comunidade.id(), membro.user().id(), "MEMBER", "ACTIVE", noFuturo);

        communityTestSupport.suspender(comunidade.slug());

        // A suspensão só recusa subscrições novas — quem já tem acesso mantém-no (D-6/R2).
        mockMvc.perform(get("/api/communities/" + comunidade.slug() + "/member-area")
                        .header("Authorization", "Bearer " + membro.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void comunidadesComAcessoPremiumDevolveTodasAsSubscricoesValidas() throws Exception {
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-4c"), PASSWORD_VALIDA);

        List<CommunityResponse> comunidadesValidas = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            AuthResponse dono = authTestSupport.registarEAutenticar(
                    authTestSupport.emailUnico("dono-4c-" + i), PASSWORD_VALIDA);
            CommunityResponse comunidade = communityTestSupport.criar(
                    dono.accessToken(), communityTestSupport.nomeUnico("Comunidade Quatro C " + i));
            communityTestSupport.inserirMembership(
                    comunidade.id(), membro.user().id(), "MEMBER", "ACTIVE", Instant.now().plus(30, ChronoUnit.DAYS));
            comunidadesValidas.add(comunidade);
        }

        // Uma quarta, expirada — não deve constar do resultado.
        AuthResponse outroDono = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("dono-4c-expirada"), PASSWORD_VALIDA);
        CommunityResponse comunidadeExpirada = communityTestSupport.criar(
                outroDono.accessToken(), communityTestSupport.nomeUnico("Comunidade Quatro C Expirada"));
        communityTestSupport.inserirMembership(
                comunidadeExpirada.id(), membro.user().id(), "MEMBER", "EXPIRED", Instant.now().minus(1, ChronoUnit.DAYS));

        List<Long> comAcesso = communityAccessService.comunidadesComAcessoPremium(membro.user().id());

        assertThat(comAcesso).containsExactlyInAnyOrder(
                comunidadesValidas.get(0).id(), comunidadesValidas.get(1).id(), comunidadesValidas.get(2).id());
        assertThat(comAcesso).doesNotContain(comunidadeExpirada.id());
    }
}
