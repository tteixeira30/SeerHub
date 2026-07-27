package pt.seerhub.football.provider;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import pt.seerhub.football.domain.FixtureStatus;

/**
 * Implementação de dados fixos de {@link FootballDataProvider} (R5, critério
 * 7). Vive deliberadamente em {@code main}, não em {@code test} (D-5 do
 * plano de F05): é o que o perfil {@code fake} usa em toda a suite, e é
 * também o que o utilizador poderia escolher em produção se algum dia
 * quisesse correr sem chave real.
 *
 * <p>O catálogo estático (§4.1 do plano) tem oito jogos das seis ligas de
 * arranque, colocados sempre em {@code hoje+1}/{@code +2}/{@code +3}
 * relativamente ao {@link Clock} injetado — nunca datas fixas, para caber
 * sempre na janela de 7 dias independentemente de quando a suite corre. Os
 * jogos 9401/9402 e 3901 caem deliberadamente no mesmo dia, para dar
 * material de agrupamento aos testes do critério 4.
 *
 * <p>Para os testes de sincronização de <b>resultados</b>, que trabalham
 * sobre jogos inseridos diretamente na base de dados por
 * {@code FootballTestSupport} (fora do catálogo estático), os métodos
 * {@link #definirResultado} / {@link #definirEstado} registam explicitamente
 * o que o fornecedor "sabe" sobre um jogo (liga, época, dia, id do
 * fornecedor) — é esse registo que {@link #jogosDaLigaNoDia} consulta,
 * juntamente com o catálogo estático.
 */
public class FixedFootballDataProvider implements FootballDataProvider {

    private static final byte[] EMBLEMA_FIXO = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,
            (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xCF,
            (byte) 0xC0, 0x00, 0x00, 0x00, 0x03, 0x00, 0x01, (byte) 0xB7, (byte) 0xB4, 0x35, (byte) 0xE0, 0x00, 0x00, 0x00,
            0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    private record CatalogFixture(long fixtureProviderId, long leagueProviderId, String leagueName,
            String leagueCountry, int dayOffset, LocalTime kickoffTime,
            long homeProviderId, String homeName, String homeShort,
            long awayProviderId, String awayName, String awayShort) {
    }

    private record RegisteredFixture(long ligaProviderId, int epoca, LocalDate dia, long fixtureProviderId,
            FixtureStatus status, Integer homeScore, Integer awayScore) {
    }

    private record RemarcacaoDeJogo(Integer novoDayOffset, LocalTime novaHora, FixtureStatus novoEstado) {
    }

    private static final List<CatalogFixture> CATALOGO = List.of(
            new CatalogFixture(9401, 94, "Primeira Liga", "Portugal", 1, LocalTime.of(19, 45),
                    940101, "SL Benfica", "BEN", 940102, "FC Porto", "POR"),
            new CatalogFixture(9402, 94, "Primeira Liga", "Portugal", 1, LocalTime.of(21, 15),
                    940103, "Sporting CP", "SCP", 940104, "SC Braga", "SCB"),
            new CatalogFixture(3901, 39, "Premier League", "England", 1, LocalTime.of(17, 30),
                    390101, "Arsenal", "ARS", 390102, "Chelsea", "CHE"),
            new CatalogFixture(3902, 39, "Premier League", "England", 2, LocalTime.of(16, 0),
                    390103, "Manchester United", "MUN", 390104, "Liverpool", "LIV"),
            new CatalogFixture(14001, 140, "La Liga", "Spain", 2, LocalTime.of(20, 0),
                    1400101, "Girona", "GIR", 1400102, "Real Madrid", "RMA"),
            new CatalogFixture(13501, 135, "Serie A", "Italy", 3, LocalTime.of(19, 45),
                    1350101, "Internazionale", "INT", 1350102, "Napoli", "NAP"),
            new CatalogFixture(7801, 78, "Bundesliga", "Germany", 2, LocalTime.of(18, 30),
                    780101, "Bayern München", "FCB", 780102, "RB Leipzig", "RBL"),
            new CatalogFixture(6101, 61, "Ligue 1", "France", 3, LocalTime.of(20, 0),
                    610101, "Paris Saint-Germain", "PSG", 610102, "Olympique Marseille", "OM"));

    private final Clock clock;
    private final List<ProviderCall> chamadas = new CopyOnWriteArrayList<>();
    private final Map<Long, RegisteredFixture> jogosRegistados = new LinkedHashMap<>();
    private final Set<Long> ligasEsvaziadas = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<Long, String> renomeacoesDeEquipa = new LinkedHashMap<>();
    private final Map<Long, RemarcacaoDeJogo> remarcacoesDeJogo = new LinkedHashMap<>();
    private final AtomicBoolean falharNaProxima = new AtomicBoolean(false);
    private final AtomicBoolean quotaEsgotadaNaProxima = new AtomicBoolean(false);

    public FixedFootballDataProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public List<ProviderFixture> jogosDaLigaEntre(long ligaProviderId, int epoca, LocalDate de, LocalDate ate) {
        registarChamada(new ProviderCall(ProviderCall.Tipo.JOGOS_ENTRE_DATAS, ligaProviderId, epoca, de, ate));
        lancarFalhaSeConfigurada();
        if (ligasEsvaziadas.contains(ligaProviderId)) {
            return List.of();
        }

        List<ProviderFixture> resultado = new ArrayList<>();
        LocalDate hoje = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        for (CatalogFixture cf : CATALOGO) {
            if (cf.leagueProviderId() != ligaProviderId) {
                continue;
            }
            LocalDate dia = hoje.plusDays(diaEfetivo(cf));
            if (dia.isBefore(de) || dia.isAfter(ate)) {
                continue;
            }
            resultado.add(paraProviderFixture(cf, dia, epoca));
        }
        for (RegisteredFixture rf : jogosRegistados.values()) {
            if (rf.ligaProviderId() != ligaProviderId || rf.dia().isBefore(de) || rf.dia().isAfter(ate)) {
                continue;
            }
            resultado.add(paraProviderFixture(rf));
        }
        return resultado;
    }

    @Override
    public List<ProviderFixture> jogosDaLigaNoDia(long ligaProviderId, int epoca, LocalDate dia) {
        registarChamada(new ProviderCall(ProviderCall.Tipo.JOGOS_DO_DIA, ligaProviderId, epoca, dia, dia));
        lancarFalhaSeConfigurada();
        if (ligasEsvaziadas.contains(ligaProviderId)) {
            return List.of();
        }

        List<ProviderFixture> resultado = new ArrayList<>();
        LocalDate hoje = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        for (CatalogFixture cf : CATALOGO) {
            if (cf.leagueProviderId() != ligaProviderId) {
                continue;
            }
            LocalDate diaDoJogo = hoje.plusDays(diaEfetivo(cf));
            if (diaDoJogo.equals(dia)) {
                resultado.add(paraProviderFixture(cf, diaDoJogo, epoca));
            }
        }
        for (RegisteredFixture rf : jogosRegistados.values()) {
            if (rf.ligaProviderId() == ligaProviderId && rf.dia().equals(dia)) {
                resultado.add(paraProviderFixture(rf));
            }
        }
        return resultado;
    }

    @Override
    public List<ProviderTeam> equipasDaLiga(long ligaProviderId, int epoca) {
        registarChamada(new ProviderCall(ProviderCall.Tipo.EQUIPAS_DA_LIGA, ligaProviderId, epoca, null, null));
        lancarFalhaSeConfigurada();
        if (ligasEsvaziadas.contains(ligaProviderId)) {
            return List.of();
        }

        Map<Long, ProviderTeam> equipas = new LinkedHashMap<>();
        for (CatalogFixture cf : CATALOGO) {
            if (cf.leagueProviderId() != ligaProviderId) {
                continue;
            }
            equipas.put(cf.homeProviderId(), new ProviderTeam(cf.homeProviderId(), nomeAtualDaEquipa(cf.homeProviderId(), cf.homeName()),
                    cf.homeShort(), cf.leagueCountry(), urlEmblemaEquipa(cf.homeProviderId())));
            equipas.put(cf.awayProviderId(), new ProviderTeam(cf.awayProviderId(), nomeAtualDaEquipa(cf.awayProviderId(), cf.awayName()),
                    cf.awayShort(), cf.leagueCountry(), urlEmblemaEquipa(cf.awayProviderId())));
        }
        return List.copyOf(equipas.values());
    }

    @Override
    public Optional<byte[]> emblema(String url) {
        // Não é chamada de dados (§2.4-C do plano): nunca gera ProviderCall nem consome orçamento.
        return Optional.of(EMBLEMA_FIXO.clone());
    }

    // === Métodos de apoio aos testes (§3.1 do plano) ===

    public List<ProviderCall> chamadas() {
        return List.copyOf(chamadas);
    }

    public int contarChamadas() {
        return chamadas.size();
    }

    public void limparRegisto() {
        chamadas.clear();
    }

    /** Regista (ou atualiza) um jogo conhecido do fornecedor para um grupo liga+época+dia. */
    public void definirResultado(long ligaProviderId, int epoca, LocalDate dia, long fixtureProviderId,
            FixtureStatus status, Integer homeScore, Integer awayScore) {
        jogosRegistados.put(fixtureProviderId,
                new RegisteredFixture(ligaProviderId, epoca, dia, fixtureProviderId, status, homeScore, awayScore));
    }

    /** Convenience: define só o estado, sem resultado (ex.: POSTPONED/CANCELLED). */
    public void definirEstado(long ligaProviderId, int epoca, LocalDate dia, long fixtureProviderId, FixtureStatus status) {
        definirResultado(ligaProviderId, epoca, dia, fixtureProviderId, status, null, null);
    }

    /** A próxima chamada de dados (qualquer uma) lança {@code FootballProviderException.falhaDeRede(...)}. */
    public void falharNaProximaChamada() {
        falharNaProxima.set(true);
    }

    /** A próxima chamada de dados (qualquer uma) lança {@code FootballProviderException.quotaEsgotada()}. */
    public void devolverQuotaEsgotada() {
        quotaEsgotadaNaProxima.set(true);
    }

    /** Simula "o fornecedor não tem os jogos desta liga" — devolve sempre lista vazia. */
    public void esvaziarLiga(long ligaProviderId) {
        ligasEsvaziadas.add(ligaProviderId);
    }

    public void reencherLiga(long ligaProviderId) {
        ligasEsvaziadas.remove(ligaProviderId);
    }

    /** Muda o nome devolvido para uma equipa do catálogo estático — critério 6f (regeneração do normalizado). */
    public void renomearEquipa(long teamProviderId, String novoNome) {
        renomeacoesDeEquipa.put(teamProviderId, novoNome);
    }

    /**
     * Simula uma remarcação do fornecedor sobre um jogo do catálogo estático
     * (critério 1h): novo desvio de dias relativamente a "hoje" (ou
     * {@code null} para manter), nova hora (ou {@code null} para manter) e
     * novo estado (ou {@code null} para manter).
     */
    public void remarcarJogoDoCatalogo(long fixtureProviderId, Integer novoDayOffset, LocalTime novaHora, FixtureStatus novoEstado) {
        remarcacoesDeJogo.put(fixtureProviderId, new RemarcacaoDeJogo(novoDayOffset, novaHora, novoEstado));
    }

    private void lancarFalhaSeConfigurada() {
        if (falharNaProxima.compareAndSet(true, false)) {
            throw FootballProviderException.falhaDeRede(new RuntimeException("Falha simulada de rede."));
        }
        if (quotaEsgotadaNaProxima.compareAndSet(true, false)) {
            throw FootballProviderException.quotaEsgotada();
        }
    }

    private void registarChamada(ProviderCall chamada) {
        chamadas.add(chamada);
    }

    private String nomeAtualDaEquipa(long providerId, String nomeOriginal) {
        return renomeacoesDeEquipa.getOrDefault(providerId, nomeOriginal);
    }

    private String urlEmblemaEquipa(long providerId) {
        return "https://fake-provider.test/logos/team-" + providerId + ".png";
    }

    private String urlEmblemaLiga(long providerId) {
        return "https://fake-provider.test/logos/league-" + providerId + ".png";
    }

    private ProviderFixture paraProviderFixture(CatalogFixture cf, LocalDate dia, int epoca) {
        ProviderLeague liga = new ProviderLeague(cf.leagueProviderId(), cf.leagueName(), cf.leagueCountry(),
                urlEmblemaLiga(cf.leagueProviderId()), epoca);
        // /fixtures não traz short_name/country da equipa — resolvido pelo backfill (5c).
        ProviderTeam casa = new ProviderTeam(cf.homeProviderId(), nomeAtualDaEquipa(cf.homeProviderId(), cf.homeName()),
                null, null, urlEmblemaEquipa(cf.homeProviderId()));
        ProviderTeam fora = new ProviderTeam(cf.awayProviderId(), nomeAtualDaEquipa(cf.awayProviderId(), cf.awayName()),
                null, null, urlEmblemaEquipa(cf.awayProviderId()));

        RemarcacaoDeJogo remarcacao = remarcacoesDeJogo.get(cf.fixtureProviderId());
        LocalTime hora = remarcacao != null && remarcacao.novaHora() != null ? remarcacao.novaHora() : cf.kickoffTime();
        FixtureStatus status = remarcacao != null && remarcacao.novoEstado() != null ? remarcacao.novoEstado() : FixtureStatus.SCHEDULED;

        java.time.Instant kickoffAt = dia.atTime(hora).toInstant(ZoneOffset.UTC);
        return new ProviderFixture(cf.fixtureProviderId(), liga, casa, fora, kickoffAt, status, null, null);
    }

    /** O desvio de dias efetivo de um jogo do catálogo, depois de aplicada uma eventual remarcação (1h). */
    private int diaEfetivo(CatalogFixture cf) {
        RemarcacaoDeJogo remarcacao = remarcacoesDeJogo.get(cf.fixtureProviderId());
        if (remarcacao != null && remarcacao.novoDayOffset() != null) {
            return remarcacao.novoDayOffset();
        }
        return cf.dayOffset();
    }

    private ProviderFixture paraProviderFixture(RegisteredFixture rf) {
        ProviderLeague liga = new ProviderLeague(rf.ligaProviderId(), "Liga " + rf.ligaProviderId(), null,
                urlEmblemaLiga(rf.ligaProviderId()), rf.epoca());
        long homeProviderId = rf.fixtureProviderId() * 10 + 1;
        long awayProviderId = rf.fixtureProviderId() * 10 + 2;
        ProviderTeam casa = new ProviderTeam(homeProviderId, nomeAtualDaEquipa(homeProviderId, "Casa " + rf.fixtureProviderId()),
                null, null, urlEmblemaEquipa(homeProviderId));
        ProviderTeam fora = new ProviderTeam(awayProviderId, nomeAtualDaEquipa(awayProviderId, "Fora " + rf.fixtureProviderId()),
                null, null, urlEmblemaEquipa(awayProviderId));
        // Hora arbitrária dentro do dia — só a data importa para a granularidade de resultados.
        java.time.Instant kickoffAt = rf.dia().atTime(12, 0).toInstant(ZoneOffset.UTC);
        return new ProviderFixture(rf.fixtureProviderId(), liga, casa, fora, kickoffAt, rf.status(),
                rf.homeScore(), rf.awayScore());
    }
}
