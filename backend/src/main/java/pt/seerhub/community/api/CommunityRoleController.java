package pt.seerhub.community.api;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pt.seerhub.community.service.CommunityPermissionService;
import pt.seerhub.user.security.AuthenticatedUser;

/**
 * {@code GET /api/me/community-roles} (R4, secção 5 da spec): a acumulação
 * de papéis de um utilizador — dono de A, moderador de B, membro de C.
 * Não vive sob {@code /api/communities/{slug}}, por isso não entra na
 * matriz papel × endpoint (não há comunidade concreta a autorizar aqui,
 * só o próprio utilizador).
 */
@RestController
public class CommunityRoleController {

    private final CommunityPermissionService communityPermissionService;

    public CommunityRoleController(CommunityPermissionService communityPermissionService) {
        this.communityPermissionService = communityPermissionService;
    }

    @GetMapping("/api/me/community-roles")
    public List<CommunityRoleResponse> osMeusPapeis(@AuthenticationPrincipal AuthenticatedUser autenticado) {
        return communityPermissionService.listarPapeisDoUtilizador(autenticado.id());
    }
}
