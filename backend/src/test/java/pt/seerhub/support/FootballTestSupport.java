package pt.seerhub.support;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Utilitário partilhado de futebol para os {@code *IT} de F05. Classe
 * simples, sem anotações Spring — construída explicitamente em
 * {@code @BeforeEach}, mesmo padrão de {@link CommunityTestSupport}.
 *
 * <p>Insere linhas cruas por JDBC em {@code leagues}/{@code teams}/
 * {@code fixtures} (catálogo próprio de F05) e em {@code tips}/
 * {@code tip_selections} (precedente explícito de
 * {@code CommunityTestSupport}: a API dona dessas tabelas — F06/F07 — ainda
 * não existe, e F05 nunca escreve nelas em código de produção, só lê).
 */
public class FootballTestSupport {

    private final JdbcTemplate jdbcTemplate;

    public FootballTestSupport(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // === Orçamento ===

    /** Limpa a linha do dia indicado (ou de todos os dias, se {@code dia} for {@code null}). */
    public void limparOrcamento() {
        jdbcTemplate.update("DELETE FROM api_call_budget");
    }

    public int consumoDoDia(LocalDate dia) {
        Integer valor = jdbcTemplate.query(
                "SELECT calls_used FROM api_call_budget WHERE day = ?",
                rs -> rs.next() ? rs.getInt("calls_used") : null,
                dia);
        return valor == null ? 0 : valor;
    }

    public int consumoDoDia() {
        return consumoDoDia(LocalDate.now(java.time.ZoneOffset.UTC));
    }

    /** Escreve diretamente {@code calls_used = valor} para o dia indicado (cria a linha se não existir). */
    public void definirConsumo(LocalDate dia, int valor) {
        jdbcTemplate.update(
                "INSERT INTO api_call_budget (day, calls_used, updated_at) VALUES (?, ?, now()) "
                        + "ON CONFLICT (day) DO UPDATE SET calls_used = ?, updated_at = now()",
                dia, valor, valor);
    }

    /** Esgota o orçamento do dia corrente (UTC) ao limite indicado — simula quota esgotada. */
    public void esgotarOrcamentoDoDia(int limite) {
        definirConsumo(LocalDate.now(java.time.ZoneOffset.UTC), limite);
    }

    public boolean existeLinhaParaODia(LocalDate dia) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM api_call_budget WHERE day = ?", Integer.class, dia);
        return total != null && total > 0;
    }

    // === Ligas / equipas / jogos ===

    // Todas as inserções abaixo são "upsert" (ON CONFLICT (provider_id) DO UPDATE), de propósito:
    // o contentor Postgres é partilhado por toda a suite, e mais do que uma classe de teste
    // usa deliberadamente os mesmos provider_id de exemplo da spec/plano (ex.: liga 39). Sem
    // idempotência aqui, a segunda classe a inserir a mesma liga violaria a UNIQUE(provider_id).

    public long inserirLiga(long providerId, String nome, int epoca) {
        return inserirLiga(providerId, nome, "Portugal", epoca, true);
    }

    public long inserirLiga(long providerId, String nome, String pais, int epoca, boolean ativa) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO leagues (provider_id, name, country, season, active) VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (provider_id) DO UPDATE SET name = EXCLUDED.name RETURNING id",
                Long.class, providerId, nome, pais, epoca, ativa);
    }

    public long inserirEquipa(long providerId, String nome, String nomeNormalizado) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO teams (provider_id, name, normalized_name, short_name, country) "
                        + "VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (provider_id) DO UPDATE SET name = EXCLUDED.name RETURNING id",
                Long.class, providerId, nome, nomeNormalizado, null, null);
    }

    /** Variante com {@code logo_url} definido — usada pelos testes de emblema (5g). */
    public long inserirEquipaComEmblema(long providerId, String nome, String nomeNormalizado, String logoUrl) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO teams (provider_id, name, normalized_name, short_name, country, logo_url) "
                        + "VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (provider_id) DO UPDATE SET name = EXCLUDED.name, logo_url = EXCLUDED.logo_url "
                        + "RETURNING id",
                Long.class, providerId, nome, nomeNormalizado, null, null, logoUrl);
    }

    public long inserirJogo(long providerId, long leagueId, long homeTeamId, long awayTeamId,
            Instant kickoffAt, String status) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO fixtures (provider_id, league_id, home_team_id, away_team_id, kickoff_at, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (provider_id) DO UPDATE SET league_id = EXCLUDED.league_id, "
                        + "home_team_id = EXCLUDED.home_team_id, away_team_id = EXCLUDED.away_team_id, "
                        + "kickoff_at = EXCLUDED.kickoff_at, status = EXCLUDED.status RETURNING id",
                Long.class, providerId, leagueId, homeTeamId, awayTeamId,
                java.sql.Timestamp.from(kickoffAt), status);
    }

    public Map<String, Object> lerJogo(long fixtureId) {
        return jdbcTemplate.queryForMap(
                "SELECT id, provider_id, league_id, home_team_id, away_team_id, kickoff_at, status, "
                        + "home_score, away_score, last_synced_at FROM fixtures WHERE id = ?", fixtureId);
    }

    public Object lerColunaDoJogo(long fixtureId, String coluna) {
        return jdbcTemplate.queryForObject(
                "SELECT " + coluna + " FROM fixtures WHERE id = ?", Object.class, fixtureId);
    }

    public Object lerColunaDaEquipa(long teamId, String coluna) {
        return jdbcTemplate.queryForObject(
                "SELECT " + coluna + " FROM teams WHERE id = ?", Object.class, teamId);
    }

    public Object lerColunaDaLiga(long leagueId, String coluna) {
        return jdbcTemplate.queryForObject(
                "SELECT " + coluna + " FROM leagues WHERE id = ?", Object.class, leagueId);
    }

    public long contarJogos() {
        Long total = jdbcTemplate.queryForObject("SELECT count(*) FROM fixtures", Long.class);
        return total == null ? 0 : total;
    }

    public long contarEquipas() {
        Long total = jdbcTemplate.queryForObject("SELECT count(*) FROM teams", Long.class);
        return total == null ? 0 : total;
    }

    // === Consultas por provider_id (seguras contra estado partilhado do contentor entre classes de teste) ===

    public boolean existeLigaComProviderId(long providerId) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM leagues WHERE provider_id = ?", Long.class, providerId);
        return total != null && total > 0;
    }

    public boolean existeJogoComProviderId(long providerId) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM fixtures WHERE provider_id = ?", Long.class, providerId);
        return total != null && total > 0;
    }

    public long contarJogosComProviderIdEm(List<Long> providerIds) {
        if (providerIds.isEmpty()) {
            return 0;
        }
        String marcadores = String.join(",", providerIds.stream().map(id -> "?").toList());
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM fixtures WHERE provider_id IN (" + marcadores + ")",
                Long.class, providerIds.toArray());
        return total == null ? 0 : total;
    }

    public Map<String, Object> lerLigaPorProviderId(long providerId) {
        return jdbcTemplate.queryForMap(
                "SELECT id, provider_id, name, country, logo_url, season, active "
                        + "FROM leagues WHERE provider_id = ?", providerId);
    }

    public Map<String, Object> lerEquipaPorProviderId(long providerId) {
        return jdbcTemplate.queryForMap(
                "SELECT id, provider_id, name, normalized_name, short_name, country, logo_url "
                        + "FROM teams WHERE provider_id = ?", providerId);
    }

    public Map<String, Object> lerJogoPorProviderId(long providerId) {
        return jdbcTemplate.queryForMap(
                "SELECT id, provider_id, league_id, home_team_id, away_team_id, kickoff_at, status, "
                        + "home_score, away_score, last_synced_at FROM fixtures WHERE provider_id = ?", providerId);
    }

    /** Nº de ligas com jogos gravados cujas equipas (casa e fora, em todos os jogos) já têm nome curto. */
    public long contarLigasTotalmenteBackfilled() {
        Long total = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM leagues l
                 WHERE EXISTS (SELECT 1 FROM fixtures f2 WHERE f2.league_id = l.id)
                   AND NOT EXISTS (
                       SELECT 1 FROM fixtures f
                       JOIN teams ht ON ht.id = f.home_team_id
                       JOIN teams at2 ON at2.id = f.away_team_id
                      WHERE f.league_id = l.id AND (ht.short_name IS NULL OR at2.short_name IS NULL)
                   )
                """, Long.class);
        return total == null ? 0 : total;
    }

    // === Tips / seleções (F05 só lê; a escrita aqui é só para preparar cenários de teste) ===

    /** Cria uma {@code tip} mínima e devolve o seu id. */
    public long inserirTip(long communityId, long authorId, String status) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO tips (community_id, author_id, stake_units, total_odds, status) "
                        + "VALUES (?, ?, 1, 1.5, ?) RETURNING id",
                Long.class, communityId, authorId, status);
    }

    /** Insere uma {@code tip_selection} ligada a um jogo, com o estado indicado. */
    public long inserirSelecao(long tipId, Long fixtureId, String status) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO tip_selections (tip_id, fixture_id, market, selection, odds, status) "
                        + "VALUES (?, ?, 'MATCH_RESULT', '1', 1.5, ?) RETURNING id",
                Long.class, tipId, fixtureId, status);
    }

    public long inserirSelecaoPendente(long tipId, long fixtureId) {
        return inserirSelecao(tipId, fixtureId, "PENDING");
    }

    public List<String> estadosDasSelecoes(long tipId) {
        return jdbcTemplate.queryForList(
                "SELECT status FROM tip_selections WHERE tip_id = ?", String.class, tipId);
    }

    public Object lerColunaDaTip(long tipId, String coluna) {
        return jdbcTemplate.queryForObject(
                "SELECT " + coluna + " FROM tips WHERE id = ?", Object.class, tipId);
    }

    /** Escreve diretamente {@code last_synced_at} de um jogo — simula um ciclo anterior recente (2i). */
    public void definirUltimaSincronizacao(long fixtureId, Instant instante) {
        jdbcTemplate.update("UPDATE fixtures SET last_synced_at = ? WHERE id = ?",
                java.sql.Timestamp.from(instante), fixtureId);
    }

    // === Limpeza (o contentor Postgres é partilhado por toda a suite — F00 — e a consulta de
    // procura de F05 é deliberadamente global, sem âmbito de comunidade; os testes de
    // resultados a pedido têm de apagar o que criam em @AfterEach para não contaminar
    // outras classes com "procura pendente" que nunca foi resolvida). ===

    /** Apaga a tip (em cascata apaga as suas tip_selections — uq FK ON DELETE CASCADE). */
    public void apagarTip(long tipId) {
        jdbcTemplate.update("DELETE FROM tips WHERE id = ?", tipId);
    }

    public void apagarJogo(long fixtureId) {
        jdbcTemplate.update("DELETE FROM fixtures WHERE id = ?", fixtureId);
    }

    public void apagarEquipa(long teamId) {
        jdbcTemplate.update("DELETE FROM teams WHERE id = ?", teamId);
    }

    public void apagarLiga(long leagueId) {
        jdbcTemplate.update("DELETE FROM leagues WHERE id = ?", leagueId);
    }
}
