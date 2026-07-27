package pt.seerhub.community.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import pt.seerhub.community.domain.Community;
import pt.seerhub.community.service.CommunityAccess;
import pt.seerhub.community.service.CommunityAccessService;
import pt.seerhub.community.service.CommunityAuthorization;
import pt.seerhub.community.service.CommunityPermissionService;
import pt.seerhub.community.service.CommunityService;
import pt.seerhub.user.security.AuthenticatedUser;

/**
 * D-10/D-11 do plano de F03: o endpoint de estado ({@code /access}, nunca
 * {@code 403}) e a porta dura ({@code /member-area}, o primeiro conteúdo
 * premium do SeerHub). Ambos resolvem a comunidade com a mesma regra de
 * visibilidade de F02 ({@code CommunityService.obterEntidadeParaLeitura}) —
 * uma comunidade suspensa continua {@code 404} para quem não é membro, antes
 * mesmo de se chegar à porta premium.
 */
@RestController
public class CommunityAccessController {

    private final CommunityService communityService;
    private final CommunityAccessService communityAccessService;
    private final CommunityPermissionService communityPermissionService;

    public CommunityAccessController(
            CommunityService communityService,
            CommunityAccessService communityAccessService,
            CommunityPermissionService communityPermissionService) {
        this.communityService = communityService;
        this.communityAccessService = communityAccessService;
        this.communityPermissionService = communityPermissionService;
    }

    @GetMapping("/api/communities/{slug}/access")
    public CommunityAccessResponse acesso(
            @PathVariable String slug,
            @AuthenticationPrincipal AuthenticatedUser autenticado) {
        Long viewerId = autenticado == null ? null : autenticado.id();
        Community community = communityService.obterEntidadeParaLeitura(slug, viewerId);
        CommunityAccess acesso = communityAccessService.acessoDe(community, viewerId);
        CommunityAuthorization autorizacao = communityPermissionService.autorizacaoDe(community, viewerId);
        return CommunityAccessResponse.de(acesso, community, autorizacao);
    }

    @GetMapping("/api/communities/{slug}/member-area")
    public MemberAreaResponse areaDeMembro(
            @PathVariable String slug,
            @AuthenticationPrincipal AuthenticatedUser autenticado) {
        Long viewerId = autenticado == null ? null : autenticado.id();
        Community community = communityService.obterEntidadeParaLeitura(slug, viewerId);
        CommunityAccess acesso = communityAccessService.exigirAcessoPremium(community, viewerId);
        return MemberAreaResponse.de(community, acesso);
    }
}
