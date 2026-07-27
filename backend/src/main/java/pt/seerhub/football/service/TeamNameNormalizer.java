package pt.seerhub.football.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Autoridade única sobre normalização de nomes de equipas (R5, critério 6; e
 * contrato antecipado para F06b, §2.5 do plano de F05).
 *
 * <p><b>Regra dura para quem consumir esta classe (F06b incluído):</b> o
 * termo de pesquisa escrito pelo tipster tem de passar por
 * {@link #normalizar(String)} antes de ser comparado com
 * {@code teams.normalized_name} ou {@code team_aliases.normalized_alias}. Uma
 * segunda normalização diferente desta quebra a correspondência exata e
 * degrada a difusa.
 *
 * <p>Passos, por ordem (§4.2 do plano):
 * <ol>
 *   <li>{@code null} ou em branco → {@code ""}.</li>
 *   <li>Normalização Unicode NFD e remoção das marcas combinatórias.</li>
 *   <li>Minúsculas com {@link Locale#ROOT}.</li>
 *   <li>Todo o carácter fora de {@code [a-z0-9]} passa a espaço.</li>
 *   <li>Remoção de tokens de clube ({@link #TOKENS_DE_CLUBE}) quando aparecem
 *       como palavra inteira, no início ou no fim — nunca no meio.</li>
 *   <li>Se isso esvaziar a cadeia, desfaz-se (fica o resultado do passo 4).</li>
 *   <li>Colapso de espaços múltiplos e {@code trim}.</li>
 *   <li>Truncatura a 120 caracteres (limite de {@code teams.normalized_name}).</li>
 * </ol>
 */
public final class TeamNameNormalizer {

    /** Tokens de clube removidos quando aparecem no início ou no fim do nome, nunca no meio. */
    public static final Set<String> TOKENS_DE_CLUBE = Set.of(
            "fc", "cf", "sc", "cd", "ca", "ac", "ad", "as", "us", "sl", "sd", "ud", "rc", "rcd",
            "cfc", "afc", "bsc", "ssc", "sv", "tsv", "vfb", "vfl", "fsv", "rb", "ogc", "sco",
            "fk", "nk", "if", "ff", "bk", "cp", "sad");

    private static final int TAMANHO_MAXIMO = 120;

    private TeamNameNormalizer() {
    }

    public static String normalizar(String nome) {
        if (nome == null || nome.isBlank()) {
            return "";
        }

        String semAcentos = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("\\p{Mn}", "");
        String minusculas = semAcentos.toLowerCase(Locale.ROOT);
        String semPontuacao = minusculas.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");

        if (semPontuacao.isEmpty()) {
            return "";
        }

        List<String> tokens = new ArrayList<>(Arrays.asList(semPontuacao.split(" ")));
        while (tokens.size() > 1 && TOKENS_DE_CLUBE.contains(tokens.get(0))) {
            tokens.remove(0);
        }
        while (tokens.size() > 1 && TOKENS_DE_CLUBE.contains(tokens.get(tokens.size() - 1))) {
            tokens.remove(tokens.size() - 1);
        }

        String resultado = String.join(" ", tokens).trim();
        if (resultado.isEmpty()) {
            // Passo 6: nunca fica vazio — desfaz-se e fica o resultado do passo 4.
            resultado = semPontuacao;
        }

        if (resultado.length() > TAMANHO_MAXIMO) {
            resultado = resultado.substring(0, TAMANHO_MAXIMO);
        }
        return resultado;
    }
}
