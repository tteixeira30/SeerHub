package pt.seerhub.community.service;

import java.time.Clock;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.seerhub.common.error.ApiException;
import pt.seerhub.community.api.CommunityResponse;
import pt.seerhub.community.api.CreateCommunityRequest;
import pt.seerhub.community.api.UpdateCommunityRequest;
import pt.seerhub.community.domain.Community;
import pt.seerhub.community.domain.CommunityMembership;
import pt.seerhub.community.domain.CommunityStatus;
import pt.seerhub.community.repo.CommunityMembershipRepository;
import pt.seerhub.community.repo.CommunityRepository;
import pt.seerhub.user.domain.User;
import pt.seerhub.user.repo.UserRepository;
import pt.seerhub.user.security.AuthenticatedUser;

/**
 * Regras de negócio de criação e gestão de comunidades (R2).
 *
 * <p>Sobre {@code community_memberships} (§2.1 do plano de F02): este
 * serviço só executa um {@code INSERT} nesta tabela — a linha
 * {@code OWNER} criada em {@link #criar}, na mesma transação que a
 * comunidade — e uma leitura de existência ({@link #obterParaLeitura}).
 * Nunca há {@code UPDATE} a uma linha de membership; qualquer transição de
 * estado ou uso de {@code expiresAt} é território de F03.
 */
@Service
public class CommunityService {

    public static final int LIMITE_COMUNIDADES_ATIVAS = 3;
    public static final String MENSAGEM_LIMITE_ATINGIDO =
            "Atingiu o limite de 3 comunidades ativas. Suspenda ou aguarde antes de criar outra.";
    public static final String MENSAGEM_COMUNIDADE_NAO_ENCONTRADA = "Comunidade não encontrada.";
    public static final String MENSAGEM_SEM_PERMISSAO = "Não tem permissão para editar esta comunidade.";
    public static final String MENSAGEM_SLUG_EM_CONFLITO =
            "Não foi possível criar a comunidade. Tente novamente.";
    private static final String MENSAGEM_AUTENTICACAO_NECESSARIA = "Autenticação necessária.";

    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository communityMembershipRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public CommunityService(
            CommunityRepository communityRepository,
            CommunityMembershipRepository communityMembershipRepository,
            UserRepository userRepository,
            Clock clock) {
        this.communityRepository = communityRepository;
        this.communityMembershipRepository = communityMembershipRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * Cria a comunidade e, na mesma transação, a membership {@code OWNER}
     * (R2, critérios 1 e 2). Ordem: verificar o limite de 3 → gerar o slug
     * único → gravar a comunidade → gravar a membership.
     */
    @Transactional
    public CommunityResponse criar(CreateCommunityRequest request, AuthenticatedUser autenticado) {
        User owner = resolverUtilizadorAutenticado(autenticado);

        long ativas = communityRepository.countByOwnerIdAndStatus(owner.getId(), CommunityStatus.ACTIVE);
        if (ativas >= LIMITE_COMUNIDADES_ATIVAS) {
            throw new ApiException(HttpStatus.CONFLICT, MENSAGEM_LIMITE_ATINGIDO);
        }

        String slug = SlugGenerator.gerarUnico(request.name(), communityRepository::existsBySlug);

        Community community = new Community(owner, request.name(), slug, clock);
        community.setDescription(request.description());
        community.setAvatarUrl(request.avatarUrl());
        community.setBannerUrl(request.bannerUrl());
        community.setPriceMonthlyCents(request.priceMonthlyCents());

        try {
            communityRepository.saveAndFlush(community);
        } catch (DataIntegrityViolationException ex) {
            // Corrida entre dois pedidos concorrentes com o mesmo slug (D-15):
            // a restrição UNIQUE da base de dados apanha o que o ciclo de
            // gerarUnico não apanhou. Sem retry na mesma transação — uma
            // violação de restrição marca-a como rollback-only.
            throw new ApiException(HttpStatus.CONFLICT, MENSAGEM_SLUG_EM_CONFLITO);
        }

        // Único INSERT que F02 faz em community_memberships: a linha OWNER,
        // ativa, sem expiração. Nenhuma outra escrita nesta tabela pertence
        // a esta feature (ver §2.1 do plano).
        CommunityMembership membership = CommunityMembership.deDono(community, owner, clock);
        communityMembershipRepository.saveAndFlush(membership);

        return CommunityResponse.de(community, owner.getId());
    }

    /**
     * Leitura pública de uma comunidade por slug (R2, critério 6 / D-7).
     * {@code viewerIdOuNull} é o id do utilizador autenticado, ou
     * {@code null} para um pedido anónimo.
     */
    @Transactional(readOnly = true)
    public CommunityResponse obterParaLeitura(String slug, Long viewerIdOuNull) {
        Community community = obterEntidadeParaLeitura(slug, viewerIdOuNull);
        return CommunityResponse.de(community, viewerIdOuNull);
    }

    /**
     * Extração de método usada por F03 (sem mudança de comportamento):
     * resolve a <b>entidade</b> com a mesma regra de visibilidade que
     * {@link #obterParaLeitura} já aplicava — {@code CommunityAccessController}
     * usa-a para o {@code member-area}, mantendo o {@code 404} de F02 num
     * sítio só.
     */
    @Transactional(readOnly = true)
    public Community obterEntidadeParaLeitura(String slug, Long viewerIdOuNull) {
        Community community = communityRepository.findBySlug(slug)
                .orElseThrow(this::comunidadeNaoEncontrada);

        boolean temMembership = viewerIdOuNull != null
                && communityMembershipRepository.existsByCommunityIdAndUserId(community.getId(), viewerIdOuNull);

        if (!CommunityAccessRules.podeSerLidaPor(community, temMembership)) {
            // Mesma resposta de um slug inexistente: uma comunidade suspensa
            // não pode servir de oráculo para quem não é membro (D-7).
            throw comunidadeNaoEncontrada();
        }

        return community;
    }

    /** Listagem pública (D-6): só {@code ACTIVE}. */
    @Transactional(readOnly = true)
    public List<CommunityResponse> listarAtivas() {
        return communityRepository.findTop100ByStatusOrderByCreatedAtDesc(CommunityStatus.ACTIVE).stream()
                .map(community -> CommunityResponse.de(community, null))
                .toList();
    }

    /** {@code GET /api/me/communities}: as comunidades de que o utilizador é dono (inclui suspensas). */
    @Transactional(readOnly = true)
    public List<CommunityResponse> listarDoDono(Long ownerId) {
        return communityRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(community -> CommunityResponse.de(community, ownerId))
                .toList();
    }

    /**
     * Edita nome, descrição, avatar, banner e preço (R2, critério 3).
     * Ordem obrigatória (D-10): procurar por slug → 404 se não existe →
     * exigir dono → 403 se não é → aplicar campos → gravar. O slug nunca
     * muda (D-5, critério 3h).
     */
    @Transactional
    public CommunityResponse editar(String slug, UpdateCommunityRequest request, AuthenticatedUser autenticado) {
        Community community = communityRepository.findBySlug(slug)
                .orElseThrow(this::comunidadeNaoEncontrada);

        exigirDono(community, autenticado.id());

        community.setName(request.name());
        community.setDescription(request.description());
        community.setAvatarUrl(request.avatarUrl());
        community.setBannerUrl(request.bannerUrl());
        community.setPriceMonthlyCents(request.priceMonthlyCents());

        communityRepository.save(community);

        return CommunityResponse.de(community, autenticado.id());
    }

    /** {@code 403} se {@code userId} não for o dono da comunidade. Nunca escreve em membership. */
    public void exigirDono(Community community, Long userId) {
        if (!community.getOwner().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, MENSAGEM_SEM_PERMISSAO);
        }
    }

    private User resolverUtilizadorAutenticado(AuthenticatedUser autenticado) {
        return userRepository.findById(autenticado.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, MENSAGEM_AUTENTICACAO_NECESSARIA));
    }

    private ApiException comunidadeNaoEncontrada() {
        return new ApiException(HttpStatus.NOT_FOUND, MENSAGEM_COMUNIDADE_NAO_ENCONTRADA);
    }
}
