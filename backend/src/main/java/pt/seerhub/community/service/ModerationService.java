package pt.seerhub.community.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.seerhub.common.error.ApiException;
import pt.seerhub.community.api.CommunityMemberResponse;
import pt.seerhub.community.domain.Community;
import pt.seerhub.community.domain.CommunityMembership;
import pt.seerhub.community.domain.CommunityPermission;
import pt.seerhub.community.domain.MembershipRole;
import pt.seerhub.community.repo.CommunityMembershipRepository;
import pt.seerhub.user.security.AuthenticatedUser;

/**
 * Nomeação e remoção de moderadores, e a lista de membros (R4, critério 1).
 * Todo o método segue o padrão do contrato C1 do handoff de F04: resolver a
 * comunidade ({@code 404}) → exigir {@code MANAGE_MODERATORS}
 * ({@code 401}/{@code 403}) → regra de negócio ({@code 409} nos casos de
 * estado inválido do alvo).
 *
 * <p><b>D-3:</b> promover/despromover altera só {@code role}; {@code status}
 * e {@code expiresAt} ficam intactos — despromover devolve o utilizador
 * exatamente à subscrição que tinha por baixo, podendo já ter vencido.
 *
 * <p><b>D-5:</b> todas as recusas de estado do alvo são {@code 409}, com
 * mensagens distintas; um {@code userId} inexistente devolve a mesma
 * mensagem e o mesmo código que «não é membro ativo» / «não é moderador»,
 * para não servir de oráculo de existência de contas.
 */
@Service
public class ModerationService {

    public static final String MENSAGEM_NAO_E_MEMBRO_ATIVO =
            "O utilizador não é um membro ativo desta comunidade.";
    public static final String MENSAGEM_JA_E_MODERADOR = "Este utilizador já é moderador desta comunidade.";
    public static final String MENSAGEM_E_O_DONO =
            "O dono já tem todas as permissões; não pode ser nomeado moderador.";
    public static final String MENSAGEM_NAO_E_MODERADOR = "Este utilizador não é moderador desta comunidade.";

    private final CommunityService communityService;
    private final CommunityPermissionService communityPermissionService;
    private final CommunityMembershipRepository communityMembershipRepository;
    private final Clock clock;

    public ModerationService(
            CommunityService communityService,
            CommunityPermissionService communityPermissionService,
            CommunityMembershipRepository communityMembershipRepository,
            Clock clock) {
        this.communityService = communityService;
        this.communityPermissionService = communityPermissionService;
        this.communityMembershipRepository = communityMembershipRepository;
        this.clock = clock;
    }

    /** {@code POST /api/communities/{slug}/moderators} — nomeia um membro ativo como moderador. */
    @Transactional
    public CommunityMemberResponse nomearModerador(String slug, Long alvoUserId, AuthenticatedUser autenticado) {
        Community community = communityService.obterEntidadeParaLeitura(slug, autenticado.id());
        communityPermissionService.exigir(community, autenticado.id(), CommunityPermission.MANAGE_MODERATORS);

        if (community.getOwner().getId().equals(alvoUserId)) {
            throw new ApiException(HttpStatus.CONFLICT, MENSAGEM_E_O_DONO);
        }

        CommunityMembership membership = communityMembershipRepository
                .findByCommunityIdAndUserId(community.getId(), alvoUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, MENSAGEM_NAO_E_MEMBRO_ATIVO));

        if (membership.getRole() == MembershipRole.MODERATOR) {
            throw new ApiException(HttpStatus.CONFLICT, MENSAGEM_JA_E_MODERADOR);
        }

        Instant agora = Instant.now(clock);
        boolean ativo = MembershipAccessRules.concedeAcessoPremium(
                membership.getRole(), membership.getStatus(), membership.getExpiresAt(), agora);
        if (!ativo) {
            throw new ApiException(HttpStatus.CONFLICT, MENSAGEM_NAO_E_MEMBRO_ATIVO);
        }

        membership.promoverAModerador();
        communityMembershipRepository.saveAndFlush(membership);

        return CommunityMemberResponse.de(membership, true);
    }

    /** {@code DELETE /api/communities/{slug}/moderators/{userId}} — devolve um moderador a {@code MEMBER}. */
    @Transactional
    public CommunityMemberResponse removerModerador(String slug, Long alvoUserId, AuthenticatedUser autenticado) {
        Community community = communityService.obterEntidadeParaLeitura(slug, autenticado.id());
        communityPermissionService.exigir(community, autenticado.id(), CommunityPermission.MANAGE_MODERATORS);

        CommunityMembership membership = communityMembershipRepository
                .findByCommunityIdAndUserId(community.getId(), alvoUserId)
                .filter(m -> m.getRole() == MembershipRole.MODERATOR)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, MENSAGEM_NAO_E_MODERADOR));

        membership.despromoverParaMembro();
        communityMembershipRepository.saveAndFlush(membership);

        Instant agora = Instant.now(clock);
        boolean ativo = MembershipAccessRules.concedeAcessoPremium(
                membership.getRole(), membership.getStatus(), membership.getExpiresAt(), agora);
        return CommunityMemberResponse.de(membership, ativo);
    }

    /** {@code GET /api/communities/{slug}/members} — inclui o dono mesmo sem linha de membership (1k). */
    @Transactional(readOnly = true)
    public List<CommunityMemberResponse> listarMembros(String slug, AuthenticatedUser autenticado) {
        Community community = communityService.obterEntidadeParaLeitura(slug, autenticado.id());
        communityPermissionService.exigir(community, autenticado.id(), CommunityPermission.MANAGE_MODERATORS);

        Instant agora = Instant.now(clock);
        List<CommunityMembership> memberships =
                communityMembershipRepository.findByCommunityIdOrderByJoinedAtAsc(community.getId());

        boolean donoTemLinhaPropria = memberships.stream()
                .anyMatch(m -> m.getUser().getId().equals(community.getOwner().getId()));

        List<CommunityMemberResponse> resultado = new ArrayList<>();
        if (!donoTemLinhaPropria) {
            resultado.add(CommunityMemberResponse.paraDonoSemMembership(community.getOwner()));
        }
        for (CommunityMembership membership : memberships) {
            boolean ativo = MembershipAccessRules.concedeAcessoPremium(
                    membership.getRole(), membership.getStatus(), membership.getExpiresAt(), agora);
            resultado.add(CommunityMemberResponse.de(membership, ativo));
        }
        return resultado;
    }
}
