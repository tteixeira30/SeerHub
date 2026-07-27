package pt.seerhub.community.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pt.seerhub.community.domain.CommunityMembership;
import pt.seerhub.community.domain.MembershipRole;
import pt.seerhub.community.domain.MembershipStatus;

/**
 * F02 só precisava de uma leitura de existência; F03 (§2.1 do seu plano)
 * acrescenta os métodos que a subscrição, o cancelamento, a listagem e a
 * tarefa diária de expiração precisam. Nada do que F02 escreveu é removido.
 */
public interface CommunityMembershipRepository extends JpaRepository<CommunityMembership, Long> {

    boolean existsByCommunityIdAndUserId(Long communityId, Long userId);

    /** A linha de uma subscrição/membership concreta — base de toda a máquina de estados de F03 (D-7/D-9). */
    Optional<CommunityMembership> findByCommunityIdAndUserId(Long communityId, Long userId);

    /** Todas as linhas do utilizador, para {@code CommunityAccessService.comunidadesComAcessoPremium} (D-16). */
    List<CommunityMembership> findByUserId(Long userId);

    /** F04: a lista de membros de uma comunidade, para {@code ModerationService.listarMembros}, mais antigos primeiro. */
    List<CommunityMembership> findByCommunityIdOrderByJoinedAtAsc(Long communityId);

    /** {@code GET /api/me/subscriptions} (D-12): só as linhas MEMBER, mais recentes primeiro. */
    List<CommunityMembership> findByUserIdAndRoleOrderByJoinedAtDesc(Long userId, MembershipRole role);

    /**
     * A tarefa diária (D-13): {@code UPDATE} em bloco, nunca um ciclo de
     * entidades. Filtra {@code status IN (ativa, cancelada)} com
     * {@code expiresAt} não nulo e já chegado, e incrementa {@code version}
     * explicitamente — um {@code @Modifying} em JPQL não passa pelo
     * bloqueio otimista do Hibernate. Devolve o número de linhas afetadas.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CommunityMembership m SET m.status = :statusExpirada, m.version = m.version + 1 "
            + "WHERE m.status IN (:statusAtiva, :statusCancelada) "
            + "AND m.expiresAt IS NOT NULL AND m.expiresAt <= :agora")
    int expirarVencidas(
            @Param("statusAtiva") MembershipStatus statusAtiva,
            @Param("statusCancelada") MembershipStatus statusCancelada,
            @Param("statusExpirada") MembershipStatus statusExpirada,
            @Param("agora") Instant agora);
}
