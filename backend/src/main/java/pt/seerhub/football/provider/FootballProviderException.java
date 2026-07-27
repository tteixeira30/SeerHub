package pt.seerhub.football.provider;

/**
 * Erro do fornecedor de dados de futebol — nunca propagado para fora do
 * serviço de sincronização (caso de fronteira 8a: registado e engolido). O
 * método {@link #quotaExcedida()} distingue o sinal mais autoritativo (429,
 * ou {@code x-ratelimit-requests-remaining: 0}) das restantes falhas
 * (rede, resposta inválida) — só o primeiro esgota o dia inteiro
 * imediatamente (§2.4-B do plano de F05).
 */
public class FootballProviderException extends RuntimeException {

    private final boolean quotaExcedida;

    private FootballProviderException(String mensagem, boolean quotaExcedida, Throwable causa) {
        super(mensagem, causa);
        this.quotaExcedida = quotaExcedida;
    }

    public boolean quotaExcedida() {
        return quotaExcedida;
    }

    public static FootballProviderException falhaDeRede(Throwable causa) {
        return new FootballProviderException("Falha de rede ao contactar o fornecedor de dados de futebol.", false, causa);
    }

    public static FootballProviderException respostaInvalida(String detalhe) {
        return new FootballProviderException(
                "Resposta inválida do fornecedor de dados de futebol: " + detalhe, false, null);
    }

    public static FootballProviderException quotaEsgotada() {
        return new FootballProviderException("Quota diária do fornecedor de dados de futebol esgotada.", true, null);
    }

    public static FootballProviderException semChave() {
        return new FootballProviderException("Chave da API-Football por configurar.", false, null);
    }
}
