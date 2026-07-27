package pt.seerhub.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propriedades tipadas da aplicação, sob o prefixo {@code seerhub}.
 *
 * <p>{@code security.jwtSecret} não tem valor por omissão de propósito: se
 * faltar, o arranque tem de falhar imediatamente (ver X2 no plano de F00),
 * em vez de a aplicação arrancar com um segredo vazio e falhar mais tarde,
 * silenciosamente, na autenticação.
 */
@ConfigurationProperties("seerhub")
@Validated
public record SeerHubProperties(
        @Valid Security security,
        @Valid Football football,
        @Valid Uploads uploads) {

    /** Segredos e tempos de vida dos tokens JWT (usados a partir de F01). */
    public record Security(
            @NotBlank String jwtSecret,
            Duration accessTtl,
            Duration refreshTtl) {
    }

    /**
     * Credenciais, endpoint e parâmetros de sincronização da API-Football
     * (F05). Nenhum componente novo tem {@code @NotNull}/{@code @NotBlank}:
     * o binder do Spring converte string vazia em {@code null} para tipos
     * wrapper (nunca para primitivos), e os acessores abaixo devolvem o
     * valor por omissão quando o componente vier {@code null} — é o que
     * garante que uma variável de ambiente vazia nunca impede o arranque
     * (R5, critério 3b).
     */
    public record Football(
            String apiKey,
            String baseUrl,
            String provider,
            Integer dailyBudget,
            Integer catalogReserve,
            Integer season,
            Duration catalogHorizon,
            Duration resultDelay,
            Duration resultWindow,
            Duration minResultInterval,
            Integer maxGroupsPerRun,
            Duration requestTimeout,
            Integer crestDownloadsPerRun,
            List<LeagueConfig> leagues) {

        private static final int OMISSAO_DAILY_BUDGET = 100;
        private static final int OMISSAO_CATALOG_RESERVE = 20;
        private static final int OMISSAO_MAX_GROUPS_PER_RUN = 4;
        private static final int OMISSAO_CREST_DOWNLOADS_PER_RUN = 50;
        private static final Duration OMISSAO_CATALOG_HORIZON = Duration.ofDays(7);
        private static final Duration OMISSAO_RESULT_DELAY = Duration.ofHours(2);
        private static final Duration OMISSAO_RESULT_WINDOW = Duration.ofDays(7);
        private static final Duration OMISSAO_MIN_RESULT_INTERVAL = Duration.ofMinutes(30);
        private static final Duration OMISSAO_REQUEST_TIMEOUT = Duration.ofSeconds(10);

        public String providerOuOmissao() {
            return provider != null ? provider : "api-football";
        }

        public int orcamentoDiario() {
            return dailyBudget != null ? dailyBudget : OMISSAO_DAILY_BUDGET;
        }

        public int reservaDeCatalogo() {
            return catalogReserve != null ? catalogReserve : OMISSAO_CATALOG_RESERVE;
        }

        public int maximoDeGruposPorExecucao() {
            return maxGroupsPerRun != null ? maxGroupsPerRun : OMISSAO_MAX_GROUPS_PER_RUN;
        }

        public int descarregamentosDeEmblemasPorExecucao() {
            return crestDownloadsPerRun != null ? crestDownloadsPerRun : OMISSAO_CREST_DOWNLOADS_PER_RUN;
        }

        public Duration horizonteDoCatalogo() {
            return catalogHorizon != null ? catalogHorizon : OMISSAO_CATALOG_HORIZON;
        }

        public Duration atrasoDeResultado() {
            return resultDelay != null ? resultDelay : OMISSAO_RESULT_DELAY;
        }

        public Duration janelaDeResultados() {
            return resultWindow != null ? resultWindow : OMISSAO_RESULT_WINDOW;
        }

        public Duration intervaloMinimoEntreResultados() {
            return minResultInterval != null ? minResultInterval : OMISSAO_MIN_RESULT_INTERVAL;
        }

        public Duration timeoutDoPedido() {
            return requestTimeout != null ? requestTimeout : OMISSAO_REQUEST_TIMEOUT;
        }

        public List<LeagueConfig> ligasOuOmissao() {
            return leagues != null ? leagues : List.of();
        }

        /** Uma liga de arranque configurada em {@code seerhub.football.leagues}. */
        public record LeagueConfig(Long providerId, String name, String country) {
        }
    }

    /** Diretório de uploads persistentes (usado a partir de F06/F07). */
    public record Uploads(
            Path dir) {
    }
}
