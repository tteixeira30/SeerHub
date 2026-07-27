package pt.seerhub.community.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.seerhub.common.error.ApiException;
import pt.seerhub.community.api.CommunityRoleResponse;
import pt.seerhub.community.domain.Community;
import pt.seerhub.community.domain.CommunityMembership;
import pt.seerhub.community.domain.CommunityPermission;
import pt.seerhub.community.domain.MembershipRole;
import pt.seerhub.community.repo.CommunityMembershipRepository;

/**
 * <b>A superfície de autorização que todas as features seguintes têm de
 * chamar</b> (R4, contrato C1 do plano de F04). Traduz «este utilizador,
 * nesta comunidade, pode esta ação?» em {@code 401}/{@code 403}.
 *
 * <p>Lê a base de dados através de {@link CommunityAccessService} (F03) —
 * nunca redefine o que é «acesso premium» — e aplica {@link CommunityPermissionRules}
 * (pura) para chegar ao conjunto de permissões. <b>Nunca resolve uma
 * comunidade por slug</b>: recebe sempre a entidade {@link Community} já
 * resolvida pelo chamador (normalmente
 * {@code CommunityService.obterEntidadeParaLeitura}), para o {@code 404}
 * continuar a viver num sítio só.
 */
@Service
public class CommunityPermissionService {

    /** Reutilizada de {@link CommunityAccessService} — não repetir a mensagem literal. */
    public static final String MENSAGEM_AUTENTICACAO_NECESSARIA = CommunityAccessService.MENSAGEM_AUTENTICACAO_NECESSARIA;

    private final CommunityAccessService communityAccessService;
    private final CommunityMembershipRepository communityMembershipRepository;
    private final Clock clock;

    public CommunityPermissionService(
            CommunityAccessService communityAccessService,
            CommunityMembershipRepository communityMembershipRepository,
            Clock clock) {
        this.communityAccessService = communityAccessService;
        this.communityMembershipRepository = communityMembershipRepository;
        this.clock = clock;
    }

    /** A autorização de {@code userIdOuNull} na comunidade — nunca lança. */
    @Transactional(readOnly = true)
    public CommunityAuthorization autorizacaoDe(Community comunidade, Long userIdOuNull) {
        CommunityAccess acesso = communityAccessService.acessoDe(comunidade, userIdOuNull);
        Set<CommunityPermission> permissoes = CommunityPermissionRules.permissoesDe(acesso.role(), acesso.premium());
        return new CommunityAuthorization(comunidade.getId(), userIdOuNull, acesso.role(), acesso.premium(), permissoes);
    }

    /**
     * A mesma pergunta para várias comunidades, numa única consulta
     * (D-16 de F03: nunca N+1) — é o que {@code CommunityController.listar}
     * e {@code /api/me/communities} usam para preencher o papel efetivo de
     * cada linha.
     */
    @Transactional(readOnly = true)
    public Map<Long, CommunityAuthorization> autorizacoesDe(Collection<Community> comunidades, Long userIdOuNull) {
        Map<Long, CommunityAuthorization> resultado = new LinkedHashMap<>();

        if (userIdOuNull == null) {
            for (Community comunidade : comunidades) {
                resultado.put(comunidade.getId(), CommunityAuthorization.semPapel(comunidade.getId(), null));
            }
            return resultado;
        }

        Map<Long, CommunityMembership> membershipsPorComunidade = new LinkedHashMap<>();
        for (CommunityMembership membership : communityMembershipRepository.findByUserId(userIdOuNull)) {
            membershipsPorComunidade.put(membership.getCommunity().getId(), membership);
        }

        Instant agora = Instant.now(clock);
        for (Community comunidade : comunidades) {
            CommunityMembership membership = membershipsPorComunidade.get(comunidade.getId());
            resultado.put(comunidade.getId(), autorizacaoDeMembership(comunidade, userIdOuNull, membership, agora));
        }
        return resultado;
    }

    /** Atalho booleano — {@code false} para um pedido anónimo, nunca lança. */
    @Transactional(readOnly = true)
    public boolean pode(Community comunidade, Long userIdOuNull, CommunityPermission permission) {
        if (userIdOuNull == null) {
            return false;
        }
        return autorizacaoDe(comunidade, userIdOuNull).pode(permission);
    }

    /** A porta dura: {@code 401} se anónimo, {@code 403} se autenticado mas sem a permissão. */
    @Transactional(readOnly = true)
    public CommunityAuthorization exigir(Community comunidade, Long userIdOuNull, CommunityPermission permission) {
        if (userIdOuNull == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, MENSAGEM_AUTENTICACAO_NECESSARIA);
        }
        CommunityAuthorization autorizacao = autorizacaoDe(comunidade, userIdOuNull);
        if (!autorizacao.pode(permission)) {
            throw new ApiException(HttpStatus.FORBIDDEN, permission.mensagemDeRecusa());
        }
        return autorizacao;
    }

    /**
     * {@code GET /api/me/community-roles}: todos os papéis do utilizador,
     * a acumulação da secção 5 da spec (dono de A, moderador de B, membro
     * de C).
     */
    @Transactional(readOnly = true)
    public List<CommunityRoleResponse> listarPapeisDoUtilizador(Long userId) {
        Instant agora = Instant.now(clock);
        return communityMembershipRepository.findByUserId(userId).stream()
                .map(membership -> {
                    Community comunidade = membership.getCommunity();
                    boolean premium = MembershipAccessRules.concedeAcessoPremium(
                            membership.getRole(), membership.getStatus(), membership.getExpiresAt(), agora);
                    Set<CommunityPermission> permissoes =
                            CommunityPermissionRules.permissoesDe(membership.getRole(), premium);
                    return new CommunityRoleResponse(
                            comunidade.getId(), comunidade.getSlug(), comunidade.getName(),
                            membership.getRole(), premium, permissoes);
                })
                .toList();
    }

    private CommunityAuthorization autorizacaoDeMembership(
            Community comunidade, Long userId, CommunityMembership membership, Instant agora) {
        if (membership != null) {
            boolean premium = MembershipAccessRules.concedeAcessoPremium(
                    membership.getRole(), membership.getStatus(), membership.getExpiresAt(), agora);
            Set<CommunityPermission> permissoes = CommunityPermissionRules.permissoesDe(membership.getRole(), premium);
            return new CommunityAuthorization(comunidade.getId(), userId, membership.getRole(), premium, permissoes);
        }
        if (comunidade.getOwner().getId().equals(userId)) {
            // D-5 de F03: o dono atravessa a porta mesmo sem nenhuma linha de membership.
            Set<CommunityPermission> permissoes = CommunityPermissionRules.permissoesDe(MembershipRole.OWNER, true);
            return new CommunityAuthorization(comunidade.getId(), userId, MembershipRole.OWNER, true, permissoes);
        }
        return CommunityAuthorization.semPapel(comunidade.getId(), userId);
    }
}
