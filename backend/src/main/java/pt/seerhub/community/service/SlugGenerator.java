package pt.seerhub.community.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Deriva um {@code slug} único a partir do nome de uma comunidade (R2,
 * critério 1). Lógica pura, espelho de {@code UsernameGenerator} (F01):
 * sem dependências, a existência de colisão é verificada através do
 * predicado injetado, para o teste unitário não precisar de base de dados.
 */
public final class SlugGenerator {

    private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}");
    private static final Pattern CARACTERES_INVALIDOS = Pattern.compile("[^a-z0-9]+");
    private static final int TAMANHO_MAXIMO_BASE = 60;
    private static final String OMISSAO = "comunidade";

    private SlugGenerator() {
    }

    /**
     * Gera a base do slug: NFD → remove marcas diacríticas → minúsculas →
     * troca sequências de caracteres não alfanuméricos por {@code -} →
     * corta {@code -} nas pontas → corta a 60 caracteres → se ficar vazio,
     * cai para {@link #OMISSAO}. Não verifica unicidade — usar
     * {@link #gerarUnico} para isso.
     */
    public static String base(String nome) {
        String normalizado = Normalizer.normalize(nome, Normalizer.Form.NFD);
        normalizado = MARCAS_DIACRITICAS.matcher(normalizado).replaceAll("");
        normalizado = normalizado.toLowerCase(Locale.ROOT);
        normalizado = CARACTERES_INVALIDOS.matcher(normalizado).replaceAll("-");
        normalizado = cortarTracos(normalizado);

        if (normalizado.length() > TAMANHO_MAXIMO_BASE) {
            normalizado = normalizado.substring(0, TAMANHO_MAXIMO_BASE);
            normalizado = cortarTracos(normalizado);
        }

        if (normalizado.isEmpty()) {
            normalizado = OMISSAO;
        }

        return normalizado;
    }

    /**
     * Gera um slug único, acrescentando um sufixo numérico ({@code -2},
     * {@code -3}, ...) enquanto {@code existe} devolver {@code true}.
     */
    public static String gerarUnico(String nome, Predicate<String> existe) {
        String base = base(nome);

        if (!existe.test(base)) {
            return base;
        }

        for (int sufixo = 2; sufixo < 1000; sufixo++) {
            String candidato = comSufixo(base, sufixo);
            if (!existe.test(candidato)) {
                return candidato;
            }
        }

        throw new IllegalStateException("Não foi possível gerar um slug único para o nome indicado.");
    }

    private static String comSufixo(String base, int sufixo) {
        String sufixoTexto = "-" + sufixo;
        int tamanhoMaximoBase = TAMANHO_MAXIMO_BASE - sufixoTexto.length();
        String baseCortada = base.length() > tamanhoMaximoBase ? base.substring(0, tamanhoMaximoBase) : base;
        return baseCortada + sufixoTexto;
    }

    private static String cortarTracos(String valor) {
        String resultado = valor;
        while (resultado.startsWith("-")) {
            resultado = resultado.substring(1);
        }
        while (resultado.endsWith("-")) {
            resultado = resultado.substring(0, resultado.length() - 1);
        }
        return resultado;
    }
}
