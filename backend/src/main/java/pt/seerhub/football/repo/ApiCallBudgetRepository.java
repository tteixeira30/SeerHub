package pt.seerhub.football.repo;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Contador diário de chamadas à API-Football (R5, critério 3).
 *
 * <p><b>D-3 do plano de F05:</b> não é uma interface Spring Data — a tabela
 * {@code api_call_budget} não tem entidade JPA associada, só é tocada por
 * SQL nativo aqui, para não ter de conciliar um {@code @Entity} com um
 * {@code INSERT ... ON CONFLICT}.
 *
 * <p>{@link #tentarReservar(LocalDate, int)} é a única operação que pode
 * correr sob concorrência: o {@code INSERT ... ON CONFLICT DO UPDATE ...
 * WHERE calls_used < :limite} é atómico ao nível do Postgres, sem lock
 * aplicacional — devolve o número de linhas afetadas (0 ou 1) diretamente do
 * JDBC.
 */
@Repository
public class ApiCallBudgetRepository {

    private final JdbcTemplate jdbcTemplate;

    public ApiCallBudgetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Reserva atomicamente uma unidade de orçamento para o dia indicado.
     * Devolve {@code true} se a reserva foi concedida (o teto ainda não
     * tinha sido atingido), {@code false} caso contrário.
     */
    public boolean tentarReservar(LocalDate dia, int limite) {
        int linhasAfetadas = jdbcTemplate.update(
                "INSERT INTO api_call_budget (day, calls_used, updated_at) "
                        + "VALUES (?, 1, now()) "
                        + "ON CONFLICT (day) DO UPDATE "
                        + "   SET calls_used = api_call_budget.calls_used + 1, updated_at = now() "
                        + " WHERE api_call_budget.calls_used < ?",
                dia, limite);
        return linhasAfetadas > 0;
    }

    /** Consumo já registado no dia indicado; 0 se ainda não existir linha. */
    public int consumidasEm(LocalDate dia) {
        Integer valor = jdbcTemplate.query(
                "SELECT calls_used FROM api_call_budget WHERE day = ?",
                rs -> rs.next() ? rs.getInt("calls_used") : null,
                dia);
        return valor == null ? 0 : valor;
    }

    /**
     * Esgota imediatamente o dia (sinal autoritativo do próprio fornecedor:
     * {@code 429} ou {@code x-ratelimit-requests-remaining: 0}) — o dia
     * fica marcado como tendo consumido pelo menos {@code limite}, nunca
     * menos do que já estava contado.
     */
    public void esgotarDia(LocalDate dia, int limite) {
        jdbcTemplate.update(
                "INSERT INTO api_call_budget (day, calls_used, updated_at) "
                        + "VALUES (?, ?, now()) "
                        + "ON CONFLICT (day) DO UPDATE "
                        + "   SET calls_used = GREATEST(api_call_budget.calls_used, ?), updated_at = now()",
                dia, limite, limite);
    }

    /** Ajuda de teste: fotografia da linha inteira, ou {@code null} se não existir. */
    public Instant atualizadoEm(LocalDate dia) {
        return jdbcTemplate.query(
                "SELECT updated_at FROM api_call_budget WHERE day = ?",
                rs -> rs.next() ? rs.getTimestamp("updated_at").toInstant() : null,
                dia);
    }
}
