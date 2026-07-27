package pt.seerhub.football.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pt.seerhub.football.domain.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByProviderId(long providerId);

    List<Team> findByProviderIdIn(Collection<Long> providerIds);

    /**
     * Ids das ligas (via {@code fixtures}) que têm pelo menos uma equipa
     * ainda sem {@code short_name} — o alvo do backfill limitado (5c do
     * plano de F05, {@code /fixtures} não traz o {@code code} de 3 letras).
     */
    @Query(value = "SELECT DISTINCT f.league_id "
            + "  FROM fixtures f "
            + "  JOIN teams ht ON ht.id = f.home_team_id "
            + "  JOIN teams at2 ON at2.id = f.away_team_id "
            + " WHERE ht.short_name IS NULL OR at2.short_name IS NULL "
            + " ORDER BY f.league_id",
            nativeQuery = true)
    List<Long> idsDeLigasComEquipasSemNomeCurto();

    @Query(value = "SELECT t.id FROM teams t "
            + "JOIN fixtures f ON f.home_team_id = t.id OR f.away_team_id = t.id "
            + "WHERE f.league_id = :leagueId AND t.short_name IS NULL",
            nativeQuery = true)
    List<Long> idsDeEquipasSemNomeCurtoNaLiga(@Param("leagueId") long leagueId);
}
