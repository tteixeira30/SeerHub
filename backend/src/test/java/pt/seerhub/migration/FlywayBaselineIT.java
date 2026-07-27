package pt.seerhub.migration;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import pt.seerhub.support.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R15.2a: o esquema é gerido inteiramente por Flyway (as tabelas do §8
 * existem depois de {@code V1}/{@code V2} correrem).
 * E1: o harness de integração usa Postgres real, com {@code pg_trgm}
 * funcional, sem rede nem chave de API (o contentor já está em cache local).
 */
class FlywayBaselineIT extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    private static final List<String> TABELAS_ESPERADAS = List.of(
            "users", "communities", "community_memberships", "leagues", "teams",
            "team_aliases", "fixtures", "tip_imports", "tips", "tip_selections",
            "chat_messages", "notifications", "admin_audit_logs"
    );

    private static final List<String> INDICES_ESPERADOS = List.of(
            "ix_fixtures_kickoff", "ix_fixtures_status_kickoff", "ix_selections_fixture_status",
            "ix_tips_feed", "ix_membership_user_status", "ix_chat_history",
            "ix_teams_normalized_name_trgm", "ix_team_aliases_trgm"
    );

    @Test
    void migracoesAplicamEsquemaCompletoDoModeloDeDados() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        for (String tabela : TABELAS_ESPERADAS) {
            Integer contagem = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, tabela);
            assertThat(contagem)
                    .as("tabela '%s' deve existir", tabela)
                    .isEqualTo(1);
        }

        for (String indice : INDICES_ESPERADOS) {
            Integer contagem = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?",
                    Integer.class, indice);
            assertThat(contagem)
                    .as("índice '%s' deve existir", indice)
                    .isEqualTo(1);
        }
    }

    @Test
    void extensaoPgTrgmEIndicesDeTrigramasExistem() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer extensaoPresente = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'", Integer.class);
        assertThat(extensaoPresente).isEqualTo(1);

        // Prova funcional: não basta a extensão existir, similarity() tem de executar.
        Double similaridade = jdbc.queryForObject(
                "SELECT similarity('sporting', 'sporting cp')", Double.class);
        assertThat(similaridade).isNotNull().isGreaterThan(0.0);
    }
}
