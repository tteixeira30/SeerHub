import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";

import { Alert } from "@/components/ui/Alert";
import { Avatar } from "@/components/ui/Avatar";
import { StatusBadge } from "@/components/ui/Badge";
import { ButtonLink } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { IconCommunities, IconPlus, IconSettings } from "@/components/ui/Icons";
import { PageHeader } from "@/components/ui/PageHeader";
import { Price } from "@/components/ui/Price";
import { SkeletonCards } from "@/components/ui/Skeleton";
import { listarMinhasComunidades } from "@/lib/communities";

/** {@code /comunidades}: as comunidades de que o utilizador autenticado é dono. */
export function MyCommunitiesPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["communities", "mine"],
    queryFn: listarMinhasComunidades,
  });

  // Com a lista vazia, o convite a criar vive no centro do ecrã — no
  // cabeçalho seria o mesmo botão duas vezes.
  const temComunidades = Boolean(data && data.length > 0);

  const cabecalho = (
    <PageHeader
      eyebrow="Gestão"
      title="As minhas comunidades"
      description="As comunidades que criaste. Cada uma tem o seu preço, os seus membros e os seus moderadores."
      actions={
        temComunidades ? (
          <ButtonLink to="/comunidades/nova">
            <IconPlus className="h-4 w-4" />
            Criar comunidade
          </ButtonLink>
        ) : undefined
      }
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
        <Alert>Não foi possível carregar as suas comunidades.</Alert>
      </div>
    );
  }

  return (
    <div>
      {cabecalho}

      {data.length === 0 ? (
        <EmptyState
          icon={<IconCommunities className="h-6 w-6" />}
          title="Ainda não tem nenhuma comunidade"
          description="Que tal criar a primeira? Defines o nome, o preço mensal e começas a partilhar tips."
          action={
            <ButtonLink to="/comunidades/nova">
              <IconPlus className="h-4 w-4" />
              Criar comunidade
            </ButtonLink>
          }
        />
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2">
          {data.map((comunidade) => (
            <li key={comunidade.id}>
              <Card interactive className="flex h-full flex-col p-5">
                <div className="flex items-start gap-3.5">
                  <Avatar name={comunidade.name} url={comunidade.avatarUrl} />
                  <div className="min-w-0 flex-1">
                    <h2 className="truncate text-[0.95rem] font-semibold tracking-tight text-ink-50">
                      <Link
                        to={`/comunidades/${comunidade.slug}`}
                        className="rounded transition hover:text-brand-300"
                      >
                        {comunidade.name}
                      </Link>
                    </h2>
                    <p className="truncate text-xs text-ink-500">/{comunidade.slug}</p>
                  </div>
                  <StatusBadge value={comunidade.status} />
                </div>

                <div className="mt-6 flex items-center justify-between border-t border-white/[0.06] pt-4">
                  <Price cents={comunidade.priceMonthlyCents} currency={comunidade.currency} />
                  <Link
                    to={`/comunidades/${comunidade.slug}/definicoes`}
                    className="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm text-ink-300 transition hover:bg-white/5 hover:text-ink-50"
                  >
                    <IconSettings className="h-4 w-4" />
                    Definições
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
