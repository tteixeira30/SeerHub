package pt.seerhub.tips.parser.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import pt.seerhub.tips.parser.ParseError;
import pt.seerhub.tips.parser.ParseErrorCode;
import pt.seerhub.tips.parser.market.MarketCatalog;
import pt.seerhub.tips.parser.market.MarketDefinition;

/**
 * A peça que produz erros úteis (§4.5 do plano). Quando a contagem de segmentos não bate ou
 * um token de mercado não é reconhecido, olha para o que os tokens <em>parecem</em> e escolhe
 * a mensagem mais específica — em vez de "a linha não corresponde à gramática".
 */
public final class FieldDiagnostics {

    private static final Pattern PARECE_STAKE = Pattern.compile("^\\d{1,2}([.,]\\d{1,2})?[ \\t]*[uU]$");
    private static final Pattern PREFIXO_OVER_UNDER = Pattern.compile("(?i)^(O(VER)?|U(NDER)?)\\d");
    private static final Pattern PREFIXO_HANDICAP = Pattern.compile("(?i)^H[12]?[-+]");

    private FieldDiagnostics() {
    }

    /** Um token e o seu índice 0-based dentro do texto de onde foi extraído. */
    public record Token(String texto, int indice0based) {
    }

    /** Divide um texto em tokens separados por espaços/tabs, guardando o índice 0-based de cada um. */
    public static List<Token> tokenizar(String texto) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < texto.length()) {
            while (i < texto.length() && Character.isWhitespace(texto.charAt(i))) {
                i++;
            }
            if (i >= texto.length()) {
                break;
            }
            int inicio = i;
            while (i < texto.length() && !Character.isWhitespace(texto.charAt(i))) {
                i++;
            }
            tokens.add(new Token(texto.substring(inicio, i), inicio));
        }
        return tokens;
    }

    /** Diagnostica uma linha de tip simples (4 campos esperados) cuja contagem não bate certo. */
    public static ParseError diagnosticarContagemDeCamposSimples(List<Segment> segmentos, int linha) {
        int n = segmentos.size();
        if (n == 1) {
            return diagnosticoDeLinhaEstranha(segmentos.get(0), linha);
        }
        if (n == 2) {
            return campoAMenos(segmentos, linha, "Faltam a odd e o stake. Exemplo: \"| 1.85 | 2u\".");
        }
        if (n == 3) {
            Segment terceiro = segmentos.get(2);
            if (PARECE_STAKE.matcher(terceiro.texto()).matches()) {
                return new ParseError(linha, terceiro.colunaInicial(), terceiro.texto(), ParseErrorCode.CAMPOS_A_MENOS,
                        "Falta a odd antes da stake. Exemplo: \"| 1.85 | 2u\".");
            }
            return campoAMenos(segmentos, linha, "Falta a stake no fim da linha. Exemplo: \"| 2u\".");
        }
        Segment quinto = segmentos.get(4);
        return new ParseError(linha, quinto.colunaInicial(), quinto.texto(), ParseErrorCode.CAMPOS_A_MAIS,
                "Campos a mais nesta linha. O formato é \"[data] equipa - equipa | mercado | odd | stakeu\".");
    }

    /** Diagnostica uma linha filha de MULT (3 campos esperados: par, mercado, odd — sem stake). */
    public static ParseError diagnosticarContagemDeCamposMult(List<Segment> segmentos, int linha) {
        int n = segmentos.size();
        if (n == 1) {
            return diagnosticoDeLinhaEstranha(segmentos.get(0), linha);
        }
        if (n == 2) {
            Segment ultimo = segmentos.get(1);
            return new ParseError(linha, ultimo.colunaFinal(), ultimo.texto(), ParseErrorCode.CAMPOS_A_MENOS,
                    "Falta a odd desta seleção. Exemplo: \"| 1X2 1 | 1.85\".");
        }
        if (n == 4) {
            Segment quarto = segmentos.get(3);
            return new ParseError(linha, quarto.colunaInicial(), quarto.texto(), ParseErrorCode.STAKE_DENTRO_DE_MULT,
                    "O stake não se escreve nas linhas do bloco MULT — declare-o uma só vez na linha \"MULT | Nu\".");
        }
        Segment quinto = segmentos.get(4);
        return new ParseError(linha, quinto.colunaInicial(), quinto.texto(), ParseErrorCode.CAMPOS_A_MAIS,
                "Campos a mais nesta linha do bloco MULT.");
    }

    /** Diagnostica um segmento de mercado que nenhuma {@link MarketDefinition} reconheceu. */
    public static ParseError diagnosticarMercado(Segment segmentoMercado, int linha) {
        List<Token> tokens = tokenizar(segmentoMercado.texto());
        if (tokens.size() >= 3) {
            Token terceiro = tokens.get(2);
            int coluna = segmentoMercado.colunaInicial() + terceiro.indice0based();
            return erroMercadoDesconhecido(linha, coluna, terceiro.texto());
        }
        if (tokens.size() == 2) {
            String tokenMercado = tokens.get(0).texto();
            String tokenSelecao = tokens.get(1).texto();
            Optional<MarketDefinition> dono = MarketCatalog.definicaoQueReconheceOToken(tokenMercado);
            if (dono.isPresent()) {
                int coluna = segmentoMercado.colunaInicial() + tokens.get(1).indice0based();
                return new ParseError(linha, coluna, tokenSelecao, ParseErrorCode.SELECAO_INVALIDA_PARA_O_MERCADO,
                        "Seleção inválida para " + dono.get().nome() + ". Aceites: "
                                + String.join(", ", dono.get().selecoesAceites()) + ".");
            }
            return erroMercadoDesconhecido(linha, segmentoMercado.colunaInicial(), tokenMercado);
        }
        if (tokens.size() == 1) {
            String token = tokens.get(0).texto();
            if (PREFIXO_OVER_UNDER.matcher(token).find() || PREFIXO_HANDICAP.matcher(token).find()) {
                return new ParseError(linha, segmentoMercado.colunaInicial(), token, ParseErrorCode.LINHA_DE_MERCADO_INVALIDA,
                        "A linha deste mercado não é um número válido. Exemplo: \"O2.5\" ou \"H-1\".");
            }
            return erroMercadoDesconhecido(linha, segmentoMercado.colunaInicial(), token);
        }
        return erroMercadoDesconhecido(linha, segmentoMercado.colunaInicial(), segmentoMercado.texto());
    }

    private static ParseError campoAMenos(List<Segment> segmentos, int linha, String mensagem) {
        Segment ultimo = segmentos.get(segmentos.size() - 1);
        return new ParseError(linha, ultimo.colunaFinal(), ultimo.texto(), ParseErrorCode.CAMPOS_A_MENOS, mensagem);
    }

    private static ParseError diagnosticoDeLinhaEstranha(Segment unico, int linha) {
        String mensagem = pareceFormatoEstrangeiro(unico.texto())
                ? "Este texto não está no formato do SeerHub. Veja o exemplo do formato e tente de novo."
                : "Faltam o mercado, a odd e o stake. O formato é \"[data] equipa - equipa | mercado | odd | stakeu\".";
        return new ParseError(linha, unico.colunaInicial(), unico.texto(), ParseErrorCode.CAMPOS_A_MENOS, mensagem);
    }

    private static boolean pareceFormatoEstrangeiro(String texto) {
        if (texto.indexOf('@') >= 0) {
            return true;
        }
        return texto.codePoints().anyMatch(cp -> cp >= 0x1F300 || (cp >= 0x2600 && cp <= 0x27BF));
    }

    private static ParseError erroMercadoDesconhecido(int linha, int coluna, String trecho) {
        return new ParseError(linha, coluna, trecho, ParseErrorCode.MERCADO_DESCONHECIDO,
                "Mercado desconhecido. Formatos aceites: " + String.join(", ", MarketCatalog.exemplosDeTodosOsMercados()) + ".");
    }
}
