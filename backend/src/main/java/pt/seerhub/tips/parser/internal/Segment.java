package pt.seerhub.tips.parser.internal;

/**
 * Um campo de uma linha lógica, delimitado por {@code |}. {@code texto} já vem sem espaços
 * das pontas; {@code colunaInicial}/{@code colunaFinal} são sempre 1-based e relativas à
 * linha original completa (nunca a uma versão reescrita) — é isto que faz as colunas serem
 * verdadeiras mesmo com tabulações à mistura (4j). Quando o campo é vazio (só espaços, ou
 * nada, entre dois {@code |}), {@code colunaInicial == colunaFinal} aponta para a posição
 * onde o campo começaria.
 */
public record Segment(String texto, int colunaInicial, int colunaFinal) {
}
