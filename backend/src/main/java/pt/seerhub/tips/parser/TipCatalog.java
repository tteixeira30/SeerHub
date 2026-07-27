package pt.seerhub.tips.parser;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import pt.seerhub.football.service.FixtureView;

/**
 * O parâmetro {@code catalog} da assinatura do R6: {@code TipTextParser(rawText, catalog) →
 * ParseResult}. F06a só lê {@link #hoje()} (inferência do ano da data) e nunca {@link
 * #jogos()} — essa leitura é de F06b, que precisa da janela sincronizada para ligar
 * {@code fixture_id}. A origem real deste catálogo em produção é {@code
 * FootballCatalogService.jogosNaJanela(de, ate)} (F05); F06a não a chama.
 */
public interface TipCatalog {

    ZoneId fuso();

    LocalDate hoje();

    List<FixtureView> jogos();
}
