package pt.seerhub.community.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import pt.seerhub.community.domain.CommunityMembership;

/**
 * F02 só precisa de uma leitura de existência (porta de acesso de uma
 * comunidade suspensa — D-7 do plano). Nada de escrita além do
 * {@code save} herdado, usado exclusivamente para a linha {@code OWNER}
 * (ver a nota de fronteira em {@link CommunityMembership}). Qualquer
 * método novo aqui é uma decisão de F03/F04, não desta feature.
 */
public interface CommunityMembershipRepository extends JpaRepository<CommunityMembership, Long> {

    boolean existsByCommunityIdAndUserId(Long communityId, Long userId);
}
