package pt.seerhub.football.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pt.seerhub.football.domain.League;

public interface LeagueRepository extends JpaRepository<League, Long> {

    Optional<League> findByProviderId(long providerId);

    List<League> findByActiveTrue();
}
