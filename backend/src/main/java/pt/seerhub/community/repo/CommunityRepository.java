package pt.seerhub.community.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pt.seerhub.community.domain.Community;
import pt.seerhub.community.domain.CommunityStatus;

public interface CommunityRepository extends JpaRepository<Community, Long> {

    Optional<Community> findBySlug(String slug);

    boolean existsBySlug(String slug);

    long countByOwnerIdAndStatus(Long ownerId, CommunityStatus status);

    /**
     * Listagem pública (D-6): só {@code ACTIVE}, as 100 mais recentes.
     * F10 substitui este método por um com paginação, ordenação e pesquisa
     * (risco em aberto 5 do plano de F02).
     */
    List<Community> findTop100ByStatusOrderByCreatedAtDesc(CommunityStatus status);

    List<Community> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
}
