package pt.seerhub.tips.parser.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pt.seerhub.tips.parser.ParseError;
import pt.seerhub.tips.parser.ParseErrorCode;
import pt.seerhub.tips.parser.TipFormat;

/**
 * Lê uma stake em unidades de um {@link Segment} já isolado: número + sufixo {@code u}/{@code
 * U}, com ou sem espaço antes do sufixo, {@code .} ou {@code ,} como separador decimal,
 * escala 2, intervalo {@code [0.25, 10]} (§11 da spec). Falta o sufixo → {@code
 * STAKE_SEM_UNIDADE}, nunca {@code STAKE_INVALIDA} — é essa distinção que torna o erro útil
 * (§4.4 do plano).
 */
public final class StakeScanner {

    private static final Pattern COM_UNIDADE = Pattern.compile("^(\\d{1,2}([.,]\\d{1,2})?)[ \\t]*[uU]$");
    private static final Pattern SO_NUMERO = Pattern.compile("^\\d{1,2}([.,]\\d{1,2})?$");

    private StakeScanner() {
    }

    public record Resultado(BigDecimal stake, ParseError erro) {

        public boolean sucesso() {
            return erro == null;
        }
    }

    public static Resultado ler(Segment segmento, int linha) {
        String texto = segmento.texto();
        Matcher comUnidade = COM_UNIDADE.matcher(texto);
        if (comUnidade.matches()) {
            BigDecimal valor = new BigDecimal(comUnidade.group(1).replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
            if (valor.compareTo(TipFormat.STAKE_MINIMA) < 0 || valor.compareTo(TipFormat.STAKE_MAXIMA) > 0) {
                return new Resultado(null, new ParseError(linha, segmento.colunaInicial(), texto,
                        ParseErrorCode.STAKE_FORA_DO_INTERVALO,
                        "O stake tem de estar entre 0.25u e 10u."));
            }
            return new Resultado(valor, null);
        }
        if (SO_NUMERO.matcher(texto).matches()) {
            return new Resultado(null, new ParseError(linha, segmento.colunaFinal(), texto,
                    ParseErrorCode.STAKE_SEM_UNIDADE,
                    "Falta a unidade do stake. Exemplo: \"2u\"."));
        }
        return new Resultado(null, new ParseError(linha, segmento.colunaInicial(), texto,
                ParseErrorCode.STAKE_INVALIDA,
                "Stake inválido. Exemplo: \"2u\" ou \"1.5u\"."));
    }
}
