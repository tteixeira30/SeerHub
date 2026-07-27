package pt.seerhub.community;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import pt.seerhub.community.api.CommunityResponse;
import pt.seerhub.community.security.RequiresCommunityPermission;
import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.AuthTestSupport;
import pt.seerhub.support.CommunityTestSupport;
import pt.seerhub.user.api.AuthResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 4y, 4z, 4aa — o contrato mecânico do plano de F04 (§2.5, C2/C3): todo o
 * endpoint de {@code /api/communities/{slug}/...} declara a permissão que
 * exige (ou consta da lista justificada de exceções), e a recusa do
 * interceptor nunca chega ao código de negócio.
 */
class CommunityAuthorizationContractIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SeerHubProperties properties;
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private AuthTestSupport authTestSupport;
    private CommunityTestSupport communityTestSupport;

    private static final String PASSWORD_VALIDA = "password-longa-123";

    /**
     * Rotas de {@code /api/communities/{slug}/...} que legitimamente não
     * exigem {@code CommunityPermission} — decisão deliberada e revista,
     * não um esquecimento (contrato C3 do plano de F04).
     */
    private static final Set<String> ENDPOINTS_SEM_PERMISSAO_DE_COMUNIDADE = Set.of(
            "GET /api/communities/{slug}", // perfil público, teaser de R11
            "GET /api/communities/{slug}/access", // estado, nunca 403
            "GET /api/communities/{slug}/member-area", // porta premium de F03 (CommunityAccessService)
            "POST /api/communities/{slug}/subscription", // auto-serviço: age sobre a própria subscrição
            "DELETE /api/communities/{slug}/subscription" // idem
    );

    @BeforeEach
    void montarSuporte() {
        authTestSupport = new AuthTestSupport(mockMvc, objectMapper, jdbcTemplate, properties);
        communityTestSupport = new CommunityTestSupport(mockMvc, objectMapper, jdbcTemplate);
    }

    @Test
    void todoEndpointDeComunidadeDeclaraAPermissaoQueExige() {
        List<String> semPermissaoNemJustificacao = new ArrayList<>();

        handlerMapping.getHandlerMethods().forEach((info, handlerMethod) -> {
            boolean anotado = handlerMethod.getMethodAnnotation(RequiresCommunityPermission.class) != null;
            for (String rota : rotasDeComunidade(info)) {
                if (!anotado && !ENDPOINTS_SEM_PERMISSAO_DE_COMUNIDADE.contains(rota)) {
                    semPermissaoNemJustificacao.add(rota + " (" + handlerMethod + ")");
                }
            }
        });

        assertThat(semPermissaoNemJustificacao).isEmpty();
    }

    @Test
    void aListaDeExcecoesSoContemRotasQueAindaExistem() {
        Set<String> todasAsRotasDeComunidade = new LinkedHashSet<>();
        handlerMapping.getHandlerMethods().forEach(
                (info, handlerMethod) -> todasAsRotasDeComunidade.addAll(rotasDeComunidade(info)));

        assertThat(todasAsRotasDeComunidade).containsAll(ENDPOINTS_SEM_PERMISSAO_DE_COMUNIDADE);
    }

    @Test
    void aRecusaDoInterceptorNuncaChegaAoServico() throws Exception {
        AuthResponse dono = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("dono-4aa"), PASSWORD_VALIDA);
        CommunityResponse comunidade = communityTestSupport.criar(
                dono.accessToken(), communityTestSupport.nomeUnico("Comunidade 4AA"), 500);

        AuthResponse membro = authTestSupport.registarEAutenticar(authTestSupport.emailUnico("membro-4aa"), PASSWORD_VALIDA);
        communityTestSupport.inserirMembership(comunidade.id(), membro.user().id(), "MEMBER", "ACTIVE",
                Instant.now().plus(30, ChronoUnit.DAYS));

        Map<String, Object> corpoQueMudaria = Map.of(
                "name", communityTestSupport.nomeUnico("Nome Que Mudaria"),
                "priceMonthlyCents", 999999);

        mockMvc.perform(put("/api/communities/" + comunidade.slug())
                        .header("Authorization", "Bearer " + membro.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpoQueMudaria)))
                .andExpect(status().isForbidden());

        Object nomeGravado = communityTestSupport.lerColunaDaComunidade(comunidade.slug(), "name");
        Object precoGravado = communityTestSupport.lerColunaDaComunidade(comunidade.slug(), "price_monthly_cents");
        assertThat(nomeGravado).isEqualTo(comunidade.name());
        assertThat(precoGravado).isEqualTo(500);
    }

    private List<String> rotasDeComunidade(RequestMappingInfo info) {
        Set<String> caminhos = new LinkedHashSet<>();
        if (info.getPathPatternsCondition() != null) {
            info.getPathPatternsCondition().getPatterns()
                    .forEach(padrao -> caminhos.add(padrao.getPatternString()));
        } else if (info.getPatternsCondition() != null) {
            caminhos.addAll(info.getPatternsCondition().getPatterns());
        }

        Set<RequestMethod> metodos = info.getMethodsCondition().getMethods();

        List<String> rotas = new ArrayList<>();
        for (String caminho : caminhos) {
            if (!caminho.startsWith("/api/communities/{slug}")) {
                continue;
            }
            for (RequestMethod metodo : metodos) {
                rotas.add(metodo.name() + " " + caminho);
            }
        }
        return rotas;
    }
}
