-- Orçamento diário de chamadas à API-Football (R5, critério 3).
-- Uma linha por dia UTC. A reserva é feita com um único INSERT ... ON CONFLICT
-- ... DO UPDATE ... WHERE, atómico: devolve 1 linha afetada quando reservou e 0
-- quando o teto do dia já foi atingido. Vive em base de dados, e não em memória,
-- porque o backend corre com "restart: unless-stopped" — um contador em memória
-- voltaria a zero a cada reinício e a garantia de nunca exceder a quota deixaria
-- de existir precisamente no cenário em que ela importa.
-- Retenção: as linhas são pequenas e permanentes (uma por dia); servem de
-- histórico de consumo. Nomes abaixo de 40 caracteres por causa de
-- ConfigurationConventionsTest.nenhumFicheiroVersionadoContemSegredoComAparenciaReal.

CREATE TABLE api_call_budget (
    day        DATE        NOT NULL PRIMARY KEY,
    calls_used INTEGER     NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_acb_calls CHECK (calls_used >= 0)
);
