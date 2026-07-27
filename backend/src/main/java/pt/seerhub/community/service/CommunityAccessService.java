package pt.seerhub.community.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.seerhub.common.error.ApiException;
import pt.seerhub.community.domain.Community;
import pt.seerhub.community.domain.CommunityMembership;
import pt.seerhub.community.domain.MembershipRole;
import pt.seerhub.community.repo.CommunityMembershipRepository;
import pt.seerhub.community.repo.CommunityRepository;

/**
 * A ÚNICA porta de acesso a conteúdo premium de uma comunidade (D-2 do plano
 * de F03). F07 (tips), F10 (feed agregado), F11 (teaser) e F12 (chat) chamam
 * esta classe a partir dos seus próprios endpoints; nenhuma delas
 * reimplementa a verificação — é isso que torna imponível a garantia do R11
 * («a resposta a um não-subscritor não contém os campos ocultos»).
 *
 * <p>A lógica pura vive em {@link MembershipAccessRules}; esta classe só lê
 * a base de dados e traduz o resultado em {@link CommunityAccess}.
 */
@Service
public class CommunityAccessService {

    public static final String MENSAGEM_SEM_ACESSO_PREMIUM =
            "Precisa de uma subscrição ativa para aceder ao conteúdo desta comunidade.";
    public static final String MENSAGEM_AUTENTICACAO_NECESSARIA = "Autenticação necessária.";
    private static final String MENSAGEM_COMUNIDADE_NAO_ENCONTRADA = "Comunidade não encontrada.";

    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository communityMembershipRepository;
    private final Clock clock;

    public CommunityAccessService(
            CommunityRepository communityRepository,
            CommunityMembershipRepository communityMembershipRepository,
            Clock clock) {
        this.communityRepository = communityRepository;
        this.communityMembershipRepository = communityMembershipRepository;
        this.clock = clock;
    }

    /** Fotografia do acesso. Nunca lança por falta de acesso; devolve {@code premium=false}. */
    @Transactional(readOnly = true)
    public CommunityAccess acessoDe(Long communityId, Long userIdOuNull) {
        Community comunidade = communityRepository.findById(communityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MENSAGEM_COMUNIDADE_NAO_ENCONTRADA));
        return acessoDe(comunidade, userIdOuNull);
    }

    /** Igual, sem repetir a leitura da comunidade quando o chamador já a tem. */
    @Transactional(readOnly = true)
    public CommunityAccess acessoDe(Community comunidade, Long userIdOuNull) {
        if (userIdOuNull == null) {
            return CommunityAccess.semMembership(comunidade.getId(), null);
        }

        return communityMembershipRepository.findByCommunityIdAndUserId(comunidade.getId(), userIdOuNull)
                .map(membership -> paraMembership(comunidade, membership))
                .orElseGet(() -> paraUtilizadorSemMembership(comunidade, userIdOuNull));
    }

    /** Atalho booleano — é o que F11 usa para decidir que campos omitir do payload. */
    @Transactional(readOnly = true)
    public boolean temAcessoPremium(Long communityId, Long userIdOuNull) {
        return acessoDe(communityId, userIdOuNull).premium();
    }

    /** Porta dura — é o que F07/F12 usam: {@code 401} se anónimo, {@code 403} se sem acesso. */
    @Transactional(readOnly = true)
    public CommunityAccess exigirAcessoPremium(Community comunidade, Long userIdOuNull) {
        if (userIdOuNull == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, MENSAGEM_AUTENTICACAO_NECESSARIA);
        }
        CommunityAccess acesso = acessoDe(comunidade, userIdOuNull);
        if (!acesso.premium()) {
            throw new ApiException(HttpStatus.FORBIDDEN, MENSAGEM_SEM_ACESSO_PREMIUM);
        }
        return acesso;
    }

    /** Ids das comunidades a que o utilizador tem acesso agora — é o que F10 usa no feed (D-16). */
    @Transactional(readOnly = true)
    public List<Long> comunidadesComAcessoPremium(Long userId) {
        Instant agora = Instant.now(clock);
        return communityMembershipRepository.findByUserId(userId).stream()
                .filter(membership -> MembershipAccessRules.concedeAcessoPremium(
                        membership.getRole(), membership.getStatus(), membership.getExpiresAt(), agora))
                .map(membership -> membership.getCommunity().getId())
                .toList();
    }

    private CommunityAccess paraMembership(Community comunidade, CommunityMembership membership) {
        Instant agora = Instant.now(clock);
        boolean premium = MembershipAccessRules.concedeAcessoPremium(
                membership.getRole(), membership.getStatus(), membership.getExpiresAt(), agora);
        return new CommunityAccess(
                comunidade.getId(),
                membership.getUser().getId(),
                membership.getRole(),
                membership.getStatus(),
                membership.getJoinedAt(),
                membership.getExpiresAt(),
                premium);
    }

    private CommunityAccess paraUtilizadorSemMembership(Community comunidade, Long userId) {
        if (comunidade.getOwner().getId().equals(userId)) {
            // D-5: o dono atravessa a porta mesmo sem nenhuma linha de membership.
            return new CommunityAccess(comunidade.getId(), userId, MembershipRole.OWNER, null, null, null, true);
        }
        return CommunityAccess.semMembership(comunidade.getId(), userId);
    }
}
