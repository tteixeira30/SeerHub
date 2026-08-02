import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";

import { Avatar } from "@/components/ui/Avatar";
import { Button } from "@/components/ui/Button";
import { IconLock } from "@/components/ui/Icons";
import { Price } from "@/components/ui/Price";
import { ApiError } from "@/lib/api";
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
    <div
      role="alert"
      className="animate-fade-up overflow-hidden rounded-2xl border border-white/[0.07] bg-white/[0.025] shadow-card"
    >
      {/* Conteúdo bloqueado: o cadeado é o que explica o porquê deste ecrã. */}
      <div className="relative flex h-28 items-center justify-center border-b border-white/[0.07] bg-gradient-to-b from-white/[0.04] to-transparent">
        <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-ink-900/80 text-ink-300 ring-1 ring-inset ring-white/10">
          <IconLock className="h-5 w-5" />
        </div>
      </div>

      <div className="p-6 text-center">
        <div className="flex items-center justify-center gap-3">
          <Avatar name={communityName} size="sm" />
          <h3 className="text-base font-semibold tracking-tight text-ink-50">{communityName}</h3>
        </div>

        <p className="mt-4 text-sm text-ink-300">A sua subscrição expirou.</p>
        <p className="mt-1 text-sm text-ink-500">
          Volta a subscrever para recuperares o acesso às tips desta comunidade.
        </p>

        <div className="mt-5 flex flex-col items-center gap-3">
          <Price cents={priceMonthlyCents} currency={currency} size="lg" />
          <Button type="button" onClick={resubscrever} loading={aSubscrever}>
            Subscrever de novo
          </Button>
        </div>

        {erro && <p className="mt-4 text-sm text-rose-300">{erro}</p>}
      </div>
    </div>
  );
}
