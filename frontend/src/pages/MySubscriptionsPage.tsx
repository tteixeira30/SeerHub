import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";

import { Alert } from "@/components/ui/Alert";
import { Avatar } from "@/components/ui/Avatar";
import { StatusBadge } from "@/components/ui/Badge";
import { ButtonLink } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { IconArrowRight, IconInbox } from "@/components/ui/Icons";
import { PageHeader } from "@/components/ui/PageHeader";
import { Price } from "@/components/ui/Price";
import { SkeletonCards } from "@/components/ui/Skeleton";
import { formatarData } from "@/lib/format";
import { listarMinhasSubscricoes } from "@/lib/subscriptions";

/** {@code /subscricoes}: todas as comunidades que o utilizador subscreve, sem limite (R3, critério 4). */
export function MySubscriptionsPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["subscriptions", "mine"],
    queryFn: listarMinhasSubscricoes,
  });

  const cabecalho = (
    <PageHeader
      eyebrow="Acompanhamento"
      title="As minhas subscrições"
      description="Todas as comunidades que segues, com o estado de cada subscrição."
    />
  );

  if (isLoading) {
    return (
      <div>
        {cabecalho}
        <SkeletonCards />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div>
        {cabecalho}
        <Alert>Não foi possível carregar as suas subscrições.</Alert>
      </div>
    );
  }

  return (
    <div>
      {cabecalho}

      {data.length === 0 ? (
        <EmptyState
          icon={<IconInbox className="h-6 w-6" />}
          title="Ainda não subscreve nenhuma comunidade"
          description="Quando entrares numa comunidade, ela aparece aqui com o estado e a data de renovação."
          action={<ButtonLink to="/comunidades">Ver as minhas comunidades</ButtonLink>}
        />
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2">
          {data.map((subscricao) => (
            <li key={subscricao.communityId}>
              <Card interactive className="flex h-full flex-col p-5">
                <div className="flex items-start gap-3.5">
                  <Avatar name={subscricao.communityName} />
                  <div className="min-w-0 flex-1">
                    <h2 className="truncate text-[0.95rem] font-semibold tracking-tight text-ink-50">
                      <Link
                        to={`/comunidades/${subscricao.slug}`}
                        className="rounded transition hover:text-brand-300"
                      >
                        {subscricao.communityName}
                      </Link>
                    </h2>
                    <p className="mt-0.5 text-xs text-ink-500">
                      {subscricao.expiresAt
                        ? `Válida até ${formatarData(subscricao.expiresAt)}`
                        : "Sem data de fim"}
                    </p>
                  </div>
                  <StatusBadge value={subscricao.status} />
                </div>

                <div className="mt-6 flex items-center justify-between border-t border-white/[0.06] pt-4">
                  <Price cents={subscricao.priceMonthlyCents} currency={subscricao.currency} />
                  <Link
                    to={`/comunidades/${subscricao.slug}`}
                    className="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm text-ink-300 transition hover:bg-white/5 hover:text-ink-50"
                  >
                    Ver comunidade
                    <IconArrowRight className="h-4 w-4" />
                  </Link>
                </div>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
