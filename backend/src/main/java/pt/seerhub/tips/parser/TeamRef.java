package pt.seerhub.tips.parser;

/**
 * O texto cru de uma equipa tal como apareceu no bloco colado, com a sua posição na linha
 * original — nada mais. {@code texto} é exatamente o que o tipster escreveu, sem trim
 * interno e sem normalização: {@code "Sporting"} e {@code "Sporting Lisbon"} são igualmente
 * válidos aqui, e nenhum código de F06a os distingue (§2.5 do plano — essa distinção é de
 * F06b). {@code linha} e {@code coluna} são 1-based, sobre o texto original tal como colado.
 */
public record TeamRef(String texto, int linha, int coluna) {
}
