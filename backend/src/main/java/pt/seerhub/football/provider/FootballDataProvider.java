package pt.seerhub.football.provider;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * A porta para o fornecedor externo de dados de futebol (R5, critério 7).
 * Duas implementações: {@link ApiFootballDataProvider} (real) e
 * {@link FixedFootballDataProvider} (dados fixos, usada em toda a suite e
 * no perfil de teste).
 *
 * <p><b>Custo de orçamento (§2.4-C do plano de F05):</b> os três primeiros
 * métodos custam 1 unidade de orçamento cada chamada; {@link #emblema(String)}
 * custa 0 — vem de um CDN de ficheiros ({@code media.api-sports.io}), não da
 * API de dados. É por isso o único método que não passa pela reserva do
 * orçamento em {@code FootballSyncService}.
 *
 * <p><b>Apenas {@code FootballSyncService} depende desta interface</b>
 * (verificado mecanicamente por
 * {@code FootballConventionsTest.soOServicoDeSincronizacaoDependeDoFornecedor}).
 */
public interface FootballDataProvider {

    /** Custa 1 unidade. Os jogos da liga entre duas datas (inclusive), com liga e equipas embutidas. */
    List<ProviderFixture> jogosDaLigaEntre(long ligaProviderId, int epoca, LocalDate de, LocalDate ate);

    /** Custa 1 unidade. Os jogos da liga num único dia — usado pela sincronização de resultados. */
    List<ProviderFixture> jogosDaLigaNoDia(long ligaProviderId, int epoca, LocalDate dia);

    /** Custa 1 unidade. O backfill limitado de nome curto/país (5c do plano). */
    List<ProviderTeam> equipasDaLiga(long ligaProviderId, int epoca);

    /** Custa 0 unidades — nunca passa pela reserva de orçamento. */
    Optional<byte[]> emblema(String url);
}
