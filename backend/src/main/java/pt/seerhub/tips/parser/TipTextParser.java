package pt.seerhub.tips.parser;

/**
 * A interface que o R6 exige: {@code TipTextParser(rawText, catalog) → ParseResult}.
 *
 * <p>Qualquer implementação futura entra por aqui sem tocar no ecrã de revisão nem no modelo
 * de dados — incluindo um eventual passo de modelo de linguagem sobre as linhas rejeitadas
 * pela gramática (§9 da spec, "Porquê não um modelo de linguagem": a porta fica aberta, só
 * não é usada em F06a por custo, latência e testabilidade). F06b usa esta mesma interface
 * para decorar {@link GrammarTipTextParser} com resolução de equipas, sem editar uma única
 * linha da gramática (§9.1/D1 do plano de F06a).
 */
public interface TipTextParser {

    ParseResult parse(String rawText, TipCatalog catalog);
}
