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

/**
 * Critério 3, parte de integração — R3. Nenhum destes testes espera pelo
 * relógio: inserem linhas com {@code expires_at} já no passado e invocam
 * {@link SubscriptionService#expirarMembershipsVencidas()} diretamente — o
 * mesmo método que o {@code @Scheduled} de {@code MembershipExpiryTask}
 * chama.
 */
class SubscriptionExpiryIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SeerHubProperties properties;
    @Autowired
    private SubscriptionService subscriptionService;

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
    void tarefaExpiraMembershipAtivaComDataNoPassado() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-3a"), PASSWORD_VALIDA);
        CommunityResponse comunidade = comunidadeComDono(dono, "Comunidade Tres A");
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-3a"), PASSWORD_VALIDA);
        communityTestSupport.inserirMembership(
                comunidade.id(), membro.user().id(), "MEMBER", "ACTIVE", Instant.now().minus(1, ChronoUnit.DAYS));

        // Nota: o contentor Postgres é partilhado e nunca limpo entre classes
        // (herdado de F00/F02) — outras classes deixam propositadamente
        // linhas ACTIVE/CANCELLED já vencidas por trás (ex.: PremiumAccessIT
        // 6b, SubscriptionCancellationIT 2d), por isso "afetadas" só pode ser
        // comparado com >=1, nunca com um valor exato. A prova determinística
        // é sempre ao nível da linha própria (comunidade, utilizador).
        int afetadas = subscriptionService.expirarMembershipsVencidas();

        assertThat(afetadas).isGreaterThanOrEqualTo(1);
        Map<String, Object> membership = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());
        assertThat(membership.get("status")).isEqualTo("EXPIRED");
        assertThat(((Number) membership.get("version")).longValue()).isEqualTo(1);
    }

    @Test
    void tarefaExpiraMembershipCanceladaComDataNoPassado() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-3b"), PASSWORD_VALIDA);
        CommunityResponse comunidade = comunidadeComDono(dono, "Comunidade Tres B");
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-3b"), PASSWORD_VALIDA);
        communityTestSupport.inserirMembership(
                comunidade.id(), membro.user().id(), "MEMBER", "CANCELLED", Instant.now().minus(1, ChronoUnit.DAYS));

        int afetadas = subscriptionService.expirarMembershipsVencidas();

        assertThat(afetadas).isGreaterThanOrEqualTo(1);
        Map<String, Object> membership = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());
        assertThat(membership.get("status")).isEqualTo("EXPIRED");
    }

    @Test
    void tarefaNaoTocaEmMembershipComDataNoFuturo() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-3c"), PASSWORD_VALIDA);
        CommunityResponse comunidade = comunidadeComDono(dono, "Comunidade Tres C");
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-3c"), PASSWORD_VALIDA);
        Instant expiraNoFuturo = Instant.now().plus(30, ChronoUnit.DAYS);
        communityTestSupport.inserirMembership(comunidade.id(), membro.user().id(), "MEMBER", "ACTIVE", expiraNoFuturo);

        Map<String, Object> antes = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());

        subscriptionService.expirarMembershipsVencidas();

        Map<String, Object> depois = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());
        assertThat(depois).isEqualTo(antes);
    }

    @Test
    void tarefaNuncaExpiraALinhaDoDonoSemDataDeExpiracao() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-3d"), PASSWORD_VALIDA);
        CommunityResponse comunidade = comunidadeComDono(dono, "Comunidade Tres D");

        Map<String, Object> antes = communityTestSupport.lerMembership(comunidade.id(), dono.user().id());
        assertThat(antes.get("expires_at")).isNull();

        subscriptionService.expirarMembershipsVencidas();

        Map<String, Object> depois = communityTestSupport.lerMembership(comunidade.id(), dono.user().id());
        assertThat(depois.get("status")).isEqualTo("ACTIVE");
        assertThat(depois.get("expires_at")).isNull();
        assertThat(((Number) depois.get("version")).longValue()).isZero();
    }

    @Test
    void tarefaNaoReprocessaMembershipJaExpirada() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-3e"), PASSWORD_VALIDA);
        CommunityResponse comunidade = comunidadeComDono(dono, "Comunidade Tres E");
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-3e"), PASSWORD_VALIDA);
        communityTestSupport.inserirMembership(
                comunidade.id(), membro.user().id(), "MEMBER", "EXPIRED", Instant.now().minus(10, ChronoUnit.DAYS));

        // A prova determinística não é o total global "afetadas" (outras
        // classes podem ter deixado linhas vencidas por trás no mesmo
        // contentor partilhado) — é a própria linha não ter sido tocada.
        subscriptionService.expirarMembershipsVencidas();

        Map<String, Object> membership = communityTestSupport.lerMembership(comunidade.id(), membro.user().id());
        assertThat(membership.get("status")).isEqualTo("EXPIRED");
        assertThat(((Number) membership.get("version")).longValue()).isZero();
    }

    @Test
    void segundaExecucaoSeguidaNaoAlteraNada() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-3f"), PASSWORD_VALIDA);
        CommunityResponse comunidade = comunidadeComDono(dono, "Comunidade Tres F");
        AuthResponse membro = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("membro-3f"), PASSWORD_VALIDA);
        communityTestSupport.inserirMembership(
                comunidade.id(), membro.user().id(), "MEMBER", "ACTIVE", Instant.now().minus(1, ChronoUnit.DAYS));

        int primeiraExecucao = subscriptionService.expirarMembershipsVencidas();
        int segundaExecucao = subscriptionService.expirarMembershipsVencidas();

        assertThat(primeiraExecucao).isGreaterThanOrEqualTo(1);
        assertThat(segundaExecucao).isZero();
    }
}
