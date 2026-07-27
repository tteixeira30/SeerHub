package pt.seerhub.football.repo;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import pt.seerhub.football.domain.Fixture;

public interface FixtureRepository extends JpaRepository<Fixture, Long> {

    Optional<Fixture> findByProviderId(long providerId);

    List<Fixture> findByProviderIdIn(Collection<Long> providerIds);

    List<Fixture> findByKickoffAtBetweenOrderByKickoffAtAsc(Instant de, Instant ate);

    /**
     * Atualização em bloco de {@code last_synced_at} para todos os jogos de
     * uma liga num dia — não só os que o fornecedor devolveu (§2.4-E do
     * plano de F05: sem isto, um jogo que o fornecedor deixou de devolver
     * mantinha {@code last_synced_at} antigo e re-disparava o grupo em
     * todos os ciclos).
     */
    @Modifying
    @Transactional
    @Query("UPDATE Fixture f SET f.lastSyncedAt = :quando "
            + "WHERE f.league.id = :ligaId AND f.kickoffAt >= :inicioDoDia AND f.kickoffAt < :fimDoDia")
    int marcarSincronizados(@Param("ligaId") long ligaId, @Param("inicioDoDia") Instant inicioDoDia,
            @Param("fimDoDia") Instant fimDoDia, @Param("quando") Instant quando);
}
