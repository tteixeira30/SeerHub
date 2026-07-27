package pt.seerhub.tips.parser.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

import pt.seerhub.tips.parser.ParseError;
import pt.seerhub.tips.parser.ParseErrorCode;
import pt.seerhub.tips.parser.TipFormat;

/**
 * Lê uma odd decimal de um {@link Segment} já isolado (o próprio segmento é o token da odd,
 * sem mais nada à volta): {@code .} ou {@code ,} como separador decimal, escala 3,
 * intervalo {@code [1.01, 999.999]}. {@code 1.00} é rejeitada — uma odd de 1.00 é um erro de
 * escrita, não uma aposta (§4.4 do plano).
 */
public final class OddsScanner {

    private static final Pattern ODD = Pattern.compile("^\\d{1,3}([.,]\\d{1,3})?$");

    private OddsScanner() {
    }

    public record Resultado(BigDecimal odd, ParseError erro) {

        public boolean sucesso() {
            return erro == null;
        }
    }

    public static Resultado ler(Segment segmento, int linha) {
        String texto = segmento.texto();
        if (!ODD.matcher(texto).matches()) {
            return new Resultado(null, erro(segmento, linha,
                    "Odd inválida. Escreva uma odd decimal, ex.: \"1.85\" ou \"1,85\"."));
        }
        BigDecimal valor = new BigDecimal(texto.replace(',', '.')).setScale(3, RoundingMode.HALF_UP);
        if (valor.compareTo(TipFormat.ODD_MINIMA) < 0 || valor.compareTo(TipFormat.ODD_MAXIMA) > 0) {
            return new Resultado(null, erro(segmento, linha,
                    "A odd tem de estar entre 1.01 e 999.999 (uma odd de 1.00 não é uma aposta)."));
        }
        return new Resultado(valor, null);
    }

    private static ParseError erro(Segment segmento, int linha, String mensagem) {
        return new ParseError(linha, segmento.colunaInicial(), segmento.texto(), ParseErrorCode.ODD_INVALIDA, mensagem);
    }
}
