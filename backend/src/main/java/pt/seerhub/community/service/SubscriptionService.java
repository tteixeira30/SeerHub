package pt.seerhub.community.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.seerhub.common.error.ApiException;
import pt.seerhub.community.api.SubscriptionResponse;
import pt.seerhub.community.domain.Community;
import pt.seerhub.community.domain.CommunityMembership;
import pt.seerhub.community.domain.MembershipRole;
import pt.seerhub.community.domain.MembershipStatus;
import pt.seerhub.community.repo.CommunityMembershipRepository;
import pt.seerhub.community.repo.CommunityRepository;
import pt.seerhub.user.domain.User;
import pt.seerhub.user.repo.UserRepository;
import pt.seerhub.user.security.AuthenticatedUser;

/**
 * Subscrever, cancelar, listar e expirar (R3, critérios 1–4). Toda a
 * transição de estado de {@code community_memberships} fora da linha
 * {@code OWNER} de F02 pertence a este serviço (§2.1 do plano de F03).
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    public static final String MENSAGEM_COMUNIDADE_NAO_ENCONTRADA = "Comunidade não encontrada.";
    public static final String MENSAGEM_JA_TEM_ACESSO_COMO_GESTOR =
            "Já tem acesso a esta comunidade como dono ou moderador.";
    public static final String MENSAGEM_JA_SUBSCREVEU = "Já subscreveu esta comunidade.";
    public static final String MENSAGEM_SUBSCRICAO_NAO_ENCONTRADA = "Não tem uma subscrição nesta comunidade.";
    public static final String MENSAGEM_SEM_SUBSCRICAO_PARA_CANCELAR = "Não tem uma subscrição para cancelar.";
    public static final String MENSAGEM_SUBSCRICAO_JA_EXPIRADA = "Esta subscrição já expirou.";
    private static final String MENSAGEM_AUTENTICACAO_NECESSARIA = "Autenticação necessária.";

    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository communityMembershipRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public SubscriptionService(
            CommunityRepository communityRepository,
            CommunityMembershipRepository communityMembershipRepository,
            UserRepository userRepository,
            Clock clock) {
        this.communityRepository = communityRepository;
        this.communityMembershipRepository = communityMembershipRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** Distingue {@code 201} (linha nova) de {@code 200} (linha reaproveitada) para o controlador (D-7). */
    public record Resultado(boolean criada, SubscriptionResponse subscricao) {
    }

    /**
     * D-7: máquina de estados sobre a linha existente, nunca um
     * {@code INSERT} cego. Ordem obrigatória: resolver a comunidade → exigir
     * que aceita novas subscrições (409 se suspensa) → decidir consoante o
     * que já existe para o par (comunidade, utilizador).
     */
    @Transactional
    public Resultado subscrever(String slug, AuthenticatedUser autenticado) {
        User user = resolverUtilizadorAutenticado(autenticado);
        Community community = communityRepository.findBySlug(slug).orElseThrow(this::comunidadeNaoEncontrada);

        CommunityAccessRules.exigirQueAceitaNovasSubscricoes(community);

        Instant agora = Instant.now(clock);
        Optional<CommunityMembership> existenteOpt =
                communityMembershipRepository.findByCommunityIdAndUserId(community.getId(), user.getId());

        if (existenteOpt.isEmpty()) {
            return criarNovaSubscricao(community, user, agora);
        }

        return reaproveitarSubscricaoExistente(existenteOpt.get(), agora);
    }

    private Resultado criarNovaSubscricao(Community community, User user, Instant agora) {
        CommunityMembership nova = CommunityMembership.deSubscritor(
                community, user, MembershipAccessRules.proximaExpiracao(agora), clock);
        try {
            communityMembershipRepository.saveAndFlush(nova);
        } catch (DataIntegrityViolationException ex) {
            // D-8: rede de segurança contra dois pedidos concorrentes com o mesmo par (comunidade, utilizador).
            throw new ApiException(HttpStatus.CONFLICT, MENSAGEM_JA_SUBSCREVEU);
        }
        return new Resultado(true, SubscriptionResponse.de(nova, true));
    }

    private Resultado reaproveitarSubscricaoExistente(CommunityMembership existente, Instant agora) {
        if (MembershipAccessRules.eGestor(existente.getRole())) {
            throw new ApiException(HttpStatus.CONFLICT, MENSAGEM_JA_TEM_ACESSO_COMO_GESTOR);
        }

        boolean ativaDentroDoPrazo = existente.getStatus() == MembershipStatus.ACTIVE
                && existente.getExpiresAt() != null && existente.getExpiresAt().isAfter(agora);
        if (ativaDentroDoPrazo) {
            throw new ApiException(HttpStatus.CONFLICT, MENSAGEM_JA_SUBSCREVEU);
        }

        boolean canceladaDentroDoPrazo = existente.getStatus() == MembershipStatus.CANCELLED
                && existente.getExpiresAt() != null && existente.getExpiresAt().isAfter(agora);
        if (canceladaDentroDoPrazo) {
            // D-7: reativar não oferece 30 dias novos — expiresAt fica intacta.
            existente.reativar();
        } else {
            // EXPIRED, ou data já no passado: renovação normal, 30 dias a partir de agora.
            existente.renovar(MembershipAccessRules.proximaExpiracao(agora));
        }

        return new Resultado(false, SubscriptionResponse.de(existente, true));
    }

    /**
     * D-9: só sabe cancelar a subscrição de quem pede — não há id de
     * utilizador no caminho nem no corpo. Estados: sem linha → 404;
     * gestor → 409 (não tem subscrição para cancelar); já EXPIRED → 409;
     * CANCELLED → 200 idempotente; ACTIVE → 200, cancela sem tocar em
     * {@code expiresAt}.
     */
    @Transactional
    public SubscriptionResponse cancelar(String slug, AuthenticatedUser autenticado) {
        Community community = communityRepository.findBySlug(slug).orElseThrow(this::comunidadeNaoEncontrada);
        CommunityMembership existente = communityMembershipRepository
                .findByCommunityIdAndUserId(community.getId(), autenticado.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MENSAGEM_SUBSCRICAO_NAO_ENCONTRADA));

        if (MembershipAccessRules.eGestor(existente.getRole())) {
            throw new ApiException(HttpStatus.CONFLICT, MENSAGEM_SEM_SUBSCRICAO_PARA_CANCELAR);
        }
        if (existente.getStatus() == MembershipStatus.EXPIRED) {
            throw new ApiException(HttpStatus.CONFLICT, MENSAGEM_SUBSCRICAO_JA_EXPIRADA);
        }
        if (existente.getStatus() == MembershipStatus.ACTIVE) {
            existente.cancelar();
        }
        // Já CANCELLED: idempotente (2h) — nada muda além do que já tinha mudado da primeira vez.

        boolean acessoAgora = MembershipAccessRules.concedeAcessoPremium(
                existente.getRole(), existente.getStatus(), existente.getExpiresAt(), Instant.now(clock));
        return SubscriptionResponse.de(existente, acessoAgora);
    }

    /** {@code GET /api/me/subscriptions} (D-12): só as linhas MEMBER, mais recentes primeiro. */
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> listarDoUtilizador(Long userId) {
        Instant agora = Instant.now(clock);
        return communityMembershipRepository.findByUserIdAndRoleOrderByJoinedAtDesc(userId, MembershipRole.MEMBER)
                .stream()
                .map(membership -> SubscriptionResponse.de(membership, MembershipAccessRules.concedeAcessoPremium(
                        membership.getRole(), membership.getStatus(), membership.getExpiresAt(), agora)))
                .toList();
    }

    /**
     * D-13: {@code UPDATE} em bloco, o mesmo método que
     * {@link MembershipExpiryTask} invoca via {@code @Scheduled}. Devolve o
     * número de linhas afetadas — testável diretamente, sem esperar pelo
     * relógio.
     */
    @Transactional
    public int expirarMembershipsVencidas() {
        Instant agora = Instant.now(clock);
        int linhas = communityMembershipRepository.expirarVencidas(
                MembershipStatus.ACTIVE, MembershipStatus.CANCELLED, MembershipStatus.EXPIRED, agora);
        log.info("Tarefa de expiração de memberships afetou {} linha(s).", linhas);
        return linhas;
    }

    private User resolverUtilizadorAutenticado(AuthenticatedUser autenticado) {
        return userRepository.findById(autenticado.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, MENSAGEM_AUTENTICACAO_NECESSARIA));
    }

    private ApiException comunidadeNaoEncontrada() {
        return new ApiException(HttpStatus.NOT_FOUND, MENSAGEM_COMUNIDADE_NAO_ENCONTRADA);
    }
}
