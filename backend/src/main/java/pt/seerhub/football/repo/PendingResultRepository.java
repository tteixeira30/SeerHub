package pt.seerhub.football.repo;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * "Há algo que valha a pena resolver?" — pergunta só de base de dados
 * (§2.4-E do plano de F05). SQL nativo, sem entidade JPA para
 * {@code tip_selections} (D-4 do plano: essa tabela é de F06/F07; F05 lê
 * três colunas e mais nada, nunca escreve nela).
 */
@Repository
public class PendingResultRepository {

    private static final String SQL_GRUPOS_POR_RESOLVER = """
            SELECT l.id AS league_id, l.provider_id AS league_provider_id, l.season AS season,
                   CAST(f.kickoff_at AT TIME ZONE 'UTC' AS date) AS dia,
                   COUNT(DISTINCT f.id) AS jogos
              FROM tip_selections s
              JOIN fixtures f ON f.id = s.fixture_id
              JOIN leagues  l ON l.id = f.league_id
             WHERE s.status = 'PENDING'
               AND f.status IN ('SCHEDULED','LIVE')
               AND f.kickoff_at <= ?
               AND f.kickoff_at >= ?
               AND (f.last_synced_at IS NULL OR f.last_synced_at <= ?)
             GROUP BY l.id, l.provider_id, l.season, 4
             ORDER BY 4 DESC, l.id
             LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PendingResultRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @param limiteDeArranque  jogos com {@code kickoff_at <=} este instante (agora − result-delay)
     * @param limiteInferior    jogos com {@code kickoff_at >=} este instante (agora − result-window)
     * @param limiteDeRepeticao só considera jogos com {@code last_synced_at <=} este instante, ou nulo (agora − min-result-interval)
     * @param maximo            número máximo de grupos devolvidos (max-groups-per-run)
     */
    public List<PendingResultGroup> gruposPorResolver(
            Instant limiteDeArranque, Instant limiteInferior, Instant limiteDeRepeticao, int maximo) {
        return jdbcTemplate.query(SQL_GRUPOS_POR_RESOLVER,
                (rs, rowNum) -> new PendingResultGroup(
                        rs.getLong("league_id"),
                        rs.getLong("league_provider_id"),
                        rs.getInt("season"),
                        rs.getDate("dia").toLocalDate(),
                        rs.getInt("jogos")),
                Timestamp.from(limiteDeArranque), Timestamp.from(limiteInferior),
                Timestamp.from(limiteDeRepeticao), maximo);
    }
}
