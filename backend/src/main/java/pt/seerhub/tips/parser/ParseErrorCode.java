package pt.seerhub.tips.parser;

/**
 * Lista fechada de códigos de erro da gramática (§4.4 do plano de F06a). O frontend e os
 * testes casam sempre sobre o código, nunca sobre a prosa de {@link ParseError#esperado()}.
 */
public enum ParseErrorCode {
    BLOCO_DEMASIADO_GRANDE,
    CAMPOS_A_MENOS,
    CAMPOS_A_MAIS,
    DATA_INVALIDA,
    PAR_DE_EQUIPAS_INVALIDO,
    PAR_DE_EQUIPAS_AMBIGUO,
    EQUIPA_VAZIA,
    MERCADO_DESCONHECIDO,
    SELECAO_INVALIDA_PARA_O_MERCADO,
    LINHA_DE_MERCADO_INVALIDA,
    ODD_INVALIDA,
    STAKE_INVALIDA,
    STAKE_SEM_UNIDADE,
    STAKE_FORA_DO_INTERVALO,
    MULT_SEM_STAKE,
    MULT_VAZIO,
    MULT_COM_UMA_SELECAO,
    MULT_COM_SELECAO_INVALIDA,
    STAKE_DENTRO_DE_MULT,
    ODD_TOTAL_EXCESSIVA
}
