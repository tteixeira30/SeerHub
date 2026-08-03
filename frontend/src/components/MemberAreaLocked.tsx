import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";

import { Avatar } from "@/components/ui/Avatar";
import { Button } from "@/components/ui/Button";
import { IconLock } from "@/components/ui/Icons";
import { Price } from "@/components/ui/Price";
import { ApiError } from "@/lib/api";
import { subscrever } from "@/lib/subscriptions";

/** O estado da subscrição segundo `/access`; `null` = nunca subscreveu. */
type EstadoSubscricao = "ACTIVE" | "CANCELLED" | "EXPIRED" | null;

interface MemberAreaLockedProps {
  slug: string;
  communityName: string;
  priceMonthlyCents: number;
  currency: string;
  estado: EstadoSubscricao;
}

/**
 * O que dizer a quem bate à porta da área de membro e leva `403`.
 *
 * Quem nunca subscreveu não teve subscrição nenhuma a expirar: recebe um
 * convite, sem `role="alert"` (não é um erro, é conteúdo por desbloquear) e
 * sem botão — o painel de subscrição ao lado é que trata disso, e dois
 * botões iguais no mesmo ecrã só dividem a atenção. Quem já foi membro
 * recebe o aviso do que aconteceu à sua subscrição e o botão para a
 * retomar ali mesmo.
 */
function textoPara(estado: EstadoSubscricao): { titulo: string; detalhe: string } {
  switch (estado) {
    case null:
      return {
        titulo: "Conteúdo reservado a membros.",
        detalhe: "Subscreve esta comunidade para veres as tips publicadas aqui.",
      };
    case "EXPIRED":
      return {
        titulo: "A sua subscrição expirou.",
        detalhe: "Volta a subscrever para recuperares o acesso às tips desta comunidade.",
      };
    case "CANCELLED":
      return {
        titulo: "A sua subscrição foi cancelada e o acesso terminou.",
        detalhe: "Volta a subscrever para recuperares o acesso às tips desta comunidade.",
      };
    default:
      return {
        titulo: "A sua subscrição já não dá acesso a esta área.",
        detalhe: "Volta a subscrever para recuperares o acesso às tips desta comunidade.",
      };
  }
}

/**
 * Aparece quando um pedido real ao conteúdo premium (`/member-area`)
 * devolve `403` (R3, critério 6). O botão chama `subscrever(slug)` — o
 * mesmo endpoint que reativa/renova (D-7) — e revalida as queries de
 * {@code /comunidades/:slug} para o conteúdo premium reaparecer sem
 * recarregar a página.
 */
export function MemberAreaLocked({
  slug,
  communityName,
  priceMonthlyCents,
  currency,
  estado,
}: MemberAreaLockedProps) {
  const queryClient = useQueryClient();
  const [aSubscrever, setASubscrever] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const nuncaSubscreveu = estado === null;
  const { titulo, detalhe } = textoPara(estado);

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
      role={nuncaSubscreveu ? undefined : "alert"}
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

        <p className="mt-4 text-sm text-ink-300">{titulo}</p>
        <p className="mt-1 text-sm text-ink-500">{detalhe}</p>

        {/* O preço acompanha o botão. Sem botão, quem o mostra é o painel
            lateral — repeti-lo aqui era dizer o mesmo preço duas vezes. */}
        {!nuncaSubscreveu && (
          <div className="mt-5 flex flex-col items-center gap-3">
            <Price cents={priceMonthlyCents} currency={currency} size="lg" />
            <Button type="button" onClick={resubscrever} loading={aSubscrever}>
              Subscrever de novo
            </Button>
          </div>
        )}

        {erro && <p className="mt-4 text-sm text-rose-300">{erro}</p>}
      </div>
    </div>
  );
}
