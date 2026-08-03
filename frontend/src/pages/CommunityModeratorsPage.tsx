import { useState } from "react";
import { useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";

import { Alert } from "@/components/ui/Alert";
import { Avatar } from "@/components/ui/Avatar";
import { StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { PageHeader } from "@/components/ui/PageHeader";
import { Skeleton } from "@/components/ui/Skeleton";
import { ApiError } from "@/lib/api";
import { formatarData } from "@/lib/format";
import { listarMembros, nomearModerador, removerModerador } from "@/lib/permissions";

/**
 * {@code /comunidades/:slug/moderadores} (R4, critério 1): o dono vê os
 * membros e nomeia/remove moderadores. O servidor já decidiu quem pode ver
 * esta página (o próprio pedido a {@code /members} devolve {@code 403} a
 * quem não tem {@code MANAGE_MODERATORS}) — este componente desenha só o
 * que a resposta trouxer, nunca decide autorização no cliente.
 */
export function CommunityModeratorsPage() {
  const { slug } = useParams<{ slug: string }>();
  const queryClient = useQueryClient();

  const { data, isLoading, error } = useQuery({
    queryKey: ["communities", slug, "members"],
    queryFn: () => listarMembros(slug!),
    enabled: Boolean(slug),
    retry: false,
  });

  const [aProcessar, setAProcessar] = useState<number | null>(null);
  const [erroAcao, setErroAcao] = useState<string | null>(null);

  async function revalidar() {
    await queryClient.invalidateQueries({ queryKey: ["communities", slug, "members"] });
  }

  async function nomear(userId: number) {
    if (!slug) {
      return;
    }
    setErroAcao(null);
    setAProcessar(userId);
    try {
      await nomearModerador(slug, userId);
      await revalidar();
    } catch (excecao) {
      setErroAcao(excecao instanceof ApiError ? excecao.detail : "Não foi possível nomear o moderador.");
    } finally {
      setAProcessar(null);
    }
  }

  async function remover(userId: number) {
    if (!slug) {
      return;
    }
    setErroAcao(null);
    setAProcessar(userId);
    try {
      await removerModerador(slug, userId);
      await revalidar();
    } catch (excecao) {
      setErroAcao(excecao instanceof ApiError ? excecao.detail : "Não foi possível remover o moderador.");
    } finally {
      setAProcessar(null);
    }
  }

  if (isLoading) {
    return (
      <div className="mx-auto max-w-3xl space-y-6">
        <Skeleton className="h-9 w-56" />
        <Skeleton className="h-72 w-full rounded-2xl" />
      </div>
    );
  }

  if (error) {
    const mensagem = error instanceof ApiError ? error.detail : "Não foi possível carregar os membros.";
    return (
      <div className="mx-auto max-w-3xl">
        <Alert>{mensagem}</Alert>
      </div>
    );
  }

  if (!data) {
    return null;
  }

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        eyebrow="Equipa"
        title="Gerir moderadores"
        description="Os moderadores partilham tips e ajudam a manter a comunidade. Podes nomear e remover a qualquer momento."
      />

      {erroAcao && <Alert className="mb-6">{erroAcao}</Alert>}

      <Card className="overflow-hidden">
        <ul>
          {data.map((membro) => (
            <li
              key={membro.userId}
              className="flex flex-wrap items-center gap-4 border-b border-white/[0.05] px-5 py-4 transition last:border-b-0 hover:bg-white/[0.02]"
            >
              <Avatar name={membro.displayName} size="sm" />

              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-ink-50">{membro.displayName}</p>
                <p className="truncate text-xs text-ink-500">
                  @{membro.username}
                  {membro.joinedAt && ` · desde ${formatarData(membro.joinedAt)}`}
                </p>
              </div>

              {/* Larguras fixas: os papéis e os botões alinham em coluna, linha a linha. */}
              <div className="flex justify-end sm:w-32">
                <StatusBadge value={membro.role} testId={`papel-${membro.userId}`} />
              </div>

              <div className="flex w-full justify-end sm:w-48">
                {membro.role === "MEMBER" && (
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    onClick={() => nomear(membro.userId)}
                    loading={aProcessar === membro.userId}
                  >
                    Nomear moderador
                  </Button>
                )}
                {membro.role === "MODERATOR" && (
                  <Button
                    type="button"
                    variant="danger"
                    size="sm"
                    onClick={() => remover(membro.userId)}
                    loading={aProcessar === membro.userId}
                  >
                    Remover moderador
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
