package pt.seerhub.tips.parser;

/**
 * Um erro de sintaxe numa linha do bloco colado — dado, nunca exceção (§4.4/H2 do plano).
 * {@code linha} e {@code coluna} são 1-based, sempre sobre o texto original tal como colado
 * (nunca sobre uma versão reescrita/normalizada). {@code trecho} é o excerto ofensivo;
 * {@code esperado} é a mensagem em português de Portugal, sempre com um exemplo concreto
 * (§4.5 do plano).
 */
public record ParseError(int linha, int coluna, String trecho, ParseErrorCode codigo, String esperado) {
}
