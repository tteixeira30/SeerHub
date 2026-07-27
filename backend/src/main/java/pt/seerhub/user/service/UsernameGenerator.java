package pt.seerhub.user.service;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Deriva um {@code username} a partir do local-part do email (D-6 do plano
 * de F01): o registo do R1 não pede username nem display name, mas a
 * tabela {@code users} (V2) exige ambos como {@code NOT NULL} e o username
 * como único. Lógica pura, sem dependências — a existência de colisão é
 * verificada através do predicado injetado, para o teste unitário não
 * precisar de base de dados nem de mocks pesados.
 */
public final class UsernameGenerator {

    private static final Pattern CARACTERES_INVALIDOS = Pattern.compile("[^a-z0-9._-]");
    private static final Pattern REPETICOES = Pattern.compile("[._-]{2,}");
    private static final int TAMANHO_MAXIMO_BASE = 30;
    private static final int TAMANHO_MAXIMO_TOTAL = 50;
    private static final String OMISSAO = "utilizador";

    private UsernameGenerator() {
    }

    /**
     * Gera o username base a partir do email (minúsculas, local-part,
     * caracteres inválidos trocados por {@code -}, repetições colapsadas,
     * cortado a 30 caracteres, com mínimo de 3 preenchido com "utilizador").
     * Não verifica unicidade — usar {@link #gerarUnico} para isso.
     */
    public static String derivar(String email) {
        String localPart = email.trim().toLowerCase(java.util.Locale.ROOT).split("@", 2)[0];

        String normalizado = CARACTERES_INVALIDOS.matcher(localPart).replaceAll("-");
        normalizado = REPETICOES.matcher(normalizado).replaceAll(m -> m.group().substring(0, 1));
        normalizado = trim(normalizado);

        if (normalizado.length() > TAMANHO_MAXIMO_BASE) {
            normalizado = normalizado.substring(0, TAMANHO_MAXIMO_BASE);
            normalizado = trim(normalizado);
        }

        if (normalizado.length() < 3) {
            normalizado = OMISSAO;
        }

        return normalizado;
    }

    /**
     * Gera um username único, acrescentando um sufixo numérico
     * (-2, -3, ...) enquanto {@code existe} devolver {@code true}, até ao
     * limite de 50 caracteres.
     */
    public static String gerarUnico(String email, Predicate<String> existe) {
        String base = derivar(email);

        if (!existe.test(base)) {
            return base;
        }

        for (int sufixo = 2; sufixo < 1000; sufixo++) {
            String candidato = comSufixo(base, sufixo);
            if (!existe.test(candidato)) {
                return candidato;
            }
        }

        throw new IllegalStateException("Não foi possível gerar um username único para o email indicado.");
    }

    private static String comSufixo(String base, int sufixo) {
        String sufixoTexto = "-" + sufixo;
        int tamanhoMaximoBase = TAMANHO_MAXIMO_TOTAL - sufixoTexto.length();
        String baseCortada = base.length() > tamanhoMaximoBase ? base.substring(0, tamanhoMaximoBase) : base;
        return baseCortada + sufixoTexto;
    }

    private static String trim(String valor) {
        String resultado = valor;
        while (resultado.startsWith("-") || resultado.startsWith(".") || resultado.startsWith("_")) {
            resultado = resultado.substring(1);
        }
        while (resultado.endsWith("-") || resultado.endsWith(".") || resultado.endsWith("_")) {
            resultado = resultado.substring(0, resultado.length() - 1);
        }
        return resultado;
    }
}
