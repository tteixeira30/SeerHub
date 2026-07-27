package pt.seerhub.user.repo;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pt.seerhub.user.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoga toda a família de tokens (D-3): usada quando se deteta a
     * reutilização de um token já rodado (sinal de roubo ou de pedido
     * duplicado). Só marca {@code revoked_at} nos tokens ainda ativos, para
     * não sobrepor a data de revogação de tokens já revogados antes.
     */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :quando WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    void revogarFamilia(@Param("familyId") UUID familyId, @Param("quando") Instant quando);
}
