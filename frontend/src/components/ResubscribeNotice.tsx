import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";

import { ApiError } from "@/lib/api";
import { centimosParaEuros } from "@/lib/communities";
import { subscrever } from "@/lib/subscriptions";

interface ResubscribeNoticeProps {
  slug: string;
  communityName: string;
  priceMonthlyCents: number;
  currency: string;
}

/**
 * O ecrã de re-subscrição (R3, critério 6): aparece quando um pedido real ao
 * conteúdo premium (`/member-area`) devolve `403`. O botão volta a chamar
 * `subscrever(slug)` — o mesmo endpoint que reativa/renova (D-7) — e revalida
 * as queries de {@code /comunidades/:slug} para o conteúdo premium reaparecer
 * sem recarregar a página.
 */
export function ResubscribeNotice({ slug, communityName, priceMonthlyCents, currency }: ResubscribeNoticeProps) {
  const queryClient = useQueryClient();
  const [aSubscrever, setASubscrever] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function resubscrever() {
    setErro(null);
    setASubscrever(true);
    try {
      await subscrever(slug);
      await queryClient.invalidateQueries({ queryKey: ["communities", slug] });
    } catch (excecao) {
      setErro(excecao instanceof ApiError ? excecao.detail : "Não foi possível subscrever novamente.");
    } finally {
      setASubscrever(false);
    }
  }

  return (
    <div role="alert">
      <h3>{communityName}</h3>
      <p>{centimosParaEuros(priceMonthlyCents)} {currency} / mês</p>
      <p>A sua subscrição expirou.</p>
      {erro && <p>{erro}</p>}
      <button type="button" onClick={resubscrever} disabled={aSubscrever}>
        Subscrever de novo
      </button>
    </div>
  );
}
