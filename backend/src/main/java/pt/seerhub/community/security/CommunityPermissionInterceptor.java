package pt.seerhub.community.security;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import pt.seerhub.community.domain.Community;
import pt.seerhub.community.service.CommunityPermissionService;
import pt.seerhub.community.service.CommunityService;
import pt.seerhub.user.security.AuthenticatedUser;

/**
 * A fronteira HTTP de {@link RequiresCommunityPermission} (R4, contrato C2
 * do plano de F04): resolve a comunidade pelo slug do caminho, resolve o
 * principal do {@link SecurityContextHolder}, e chama a mesma porta de
 * serviço que qualquer outro chamador — {@code 404} antes de {@code 403},
 * sempre (D-6 do plano).
 *
 * <p>Não é decorativa: um endpoint anotado sem a variável de caminho
 * declarada é erro de programação (rebenta ruidosamente com
 * {@link IllegalStateException}), não falha em silêncio.
 */
@Component
public class CommunityPermissionInterceptor implements HandlerInterceptor {

    private final CommunityService communityService;
    private final CommunityPermissionService communityPermissionService;

    public CommunityPermissionInterceptor(
            CommunityService communityService, CommunityPermissionService communityPermissionService) {
        this.communityService = communityService;
        this.communityPermissionService = communityPermissionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequiresCommunityPermission anotacao = handlerMethod.getMethodAnnotation(RequiresCommunityPermission.class);
        if (anotacao == null) {
            return true;
        }

        @SuppressWarnings("unchecked")
        Map<String, String> variaveisDeCaminho =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variaveisDeCaminho == null || !variaveisDeCaminho.containsKey(anotacao.slugVariable())) {
            throw new IllegalStateException(
                    "@RequiresCommunityPermission em " + handlerMethod
                            + " exige a variável de caminho '" + anotacao.slugVariable()
                            + "', que não existe neste pedido.");
        }
        String slug = variaveisDeCaminho.get(anotacao.slugVariable());

        Long userIdOuNull = resolverUserIdAutenticado();

        Community comunidade = communityService.obterEntidadeParaLeitura(slug, userIdOuNull); // 404
        communityPermissionService.exigir(comunidade, userIdOuNull, anotacao.value()); // 401 / 403

        return true;
    }

    private Long resolverUserIdAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser autenticado)) {
            return null;
        }
        return autenticado.id();
    }
}
