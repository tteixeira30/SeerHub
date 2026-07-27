package pt.seerhub.football.provider;

import java.time.LocalDate;

/**
 * O registo de uma chamada de dados ao fornecedor — o que
 * {@code FixedFootballDataProvider} acumula e os testes de agrupamento
 * (critério 4 do R5) asseveram. {@code emblema(url)} nunca gera um
 * {@code ProviderCall}: não é uma chamada de dados (§2.4-C do plano).
 */
public record ProviderCall(Tipo tipo, Long ligaProviderId, Integer epoca, LocalDate de, LocalDate ate) {

    public enum Tipo {
        JOGOS_ENTRE_DATAS,
        JOGOS_DO_DIA,
        EQUIPAS_DA_LIGA
    }
}
