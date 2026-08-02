import type { ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";

import { Avatar } from "@/components/ui/Avatar";
import { StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { IconLogout } from "@/components/ui/Icons";
import { PageHeader } from "@/components/ui/PageHeader";
import { Skeleton } from "@/components/ui/Skeleton";
import { Alert } from "@/components/ui/Alert";
import { apiFetch } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import type { Utilizador } from "@/lib/auth";

/** Linha de detalhe: rótulo à esquerda, valor à direita, em ecrãs largos. */
function Linha({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1 border-b border-white/[0.05] px-6 py-4 last:border-b-0 sm:flex-row sm:items-center sm:justify-between">
      <dt className="text-sm text-ink-400">{label}</dt>
      <dd className="text-sm font-medium text-ink-50">{children}</dd>
    </div>
  );
}

/** {@code /conta}, protegida por {@code RequireAuth}: mostra `GET /api/users/me`. */
export function AccountPage() {
  const { sair } = useAuth();
  const navigate = useNavigate();

  const { data, isLoading, isError } = useQuery({
    queryKey: ["users", "me"],
    queryFn: () => apiFetch<Utilizador>("/api/users/me"),
  });

  async function terminarSessao() {
    await sair();
    navigate("/entrar", { replace: true });
  }

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-9 w-56" />
        <Skeleton className="h-64 w-full rounded-2xl" />
      </div>
    );
  }

  if (isError || !data) {
    return <Alert>Não foi possível carregar a conta.</Alert>;
  }

  return (
    <div>
      <PageHeader eyebrow="Perfil" title="A minha conta" />

      <Card className="overflow-hidden">
        <div className="flex flex-col items-center gap-5 border-b border-white/[0.07] px-6 py-8 text-center sm:flex-row sm:text-left">
          <Avatar name={data.displayName} size="lg" />
          <div className="min-w-0">
            <p className="truncate text-lg font-semibold tracking-tight text-white">
              {data.displayName}
            </p>
            <p className="truncate text-sm text-ink-400">@{data.username}</p>
          </div>
          <div className="sm:ml-auto">
            <StatusBadge value={data.status} />
          </div>
        </div>

        <dl>
          <Linha label="Email">{data.email}</Linha>
          <Linha label="Utilizador">{data.username}</Linha>
          <Linha label="Nome a mostrar">{data.displayName}</Linha>
          <Linha label="Papel">
            <StatusBadge value={data.globalRole} />
          </Linha>
        </dl>
      </Card>

      <div className="mt-6 flex justify-end">
        <Button variant="secondary" onClick={() => void terminarSessao()}>
          <IconLogout className="h-4 w-4" />
          Terminar sessão
        </Button>
      </div>
    </div>
  );
}
