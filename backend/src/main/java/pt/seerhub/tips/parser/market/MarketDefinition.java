package pt.seerhub.tips.parser.market;

import java.util.List;
import java.util.Optional;

/**
 * Um mercado reconhecível pela gramática. {@code tokenDeMercado} pode ser {@code null} (forma
 * de um só token, ex.: {@code "O2.5"}, {@code "1X"}, {@code "GG"}).
 *
 * <p>{@link #reconheceTokenDeMercado(String)} e {@link #selecoesAceites()} existem só para
 * diagnóstico ({@code FieldDiagnostics}, §4.5 do plano) — para poder distinguir um {@code
 * MERCADO_DESCONHECIDO} (nenhum mercado reconhece o token) de um {@code
 * SELECAO_INVALIDA_PARA_O_MERCADO} (o mercado é conhecido, a seleção escrita não).
 */
public interface MarketDefinition {

    Optional<MarketMatch> reconhecer(String tokenDeMercado, String tokenDeSelecao);

    String nome();

    /** Exemplos de escrita aceites por este mercado, para mensagens de erro. */
    List<String> exemplos();

    /** Só para diagnóstico: este mercado é dono deste token de mercado (em forma separada)? */
    boolean reconheceTokenDeMercado(String token);

    /** Só para diagnóstico: as formas de seleção aceites por este mercado. */
    List<String> selecoesAceites();
}
