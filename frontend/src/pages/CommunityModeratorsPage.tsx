import { useState } from "react";
import { useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";

import { ApiError } from "@/lib/api";
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
    return <p>A carregar membros...</p>;
  }

  if (error) {
    const mensagem = error instanceof ApiError ? error.detail : "Não foi possível carregar os membros.";
    return <p role="alert">{mensagem}</p>;
  }

  if (!data) {
    return null;
  }

  return (
    <div>
      <h1>Gerir moderadores</h1>

      {erroAcao && <p role="alert">{erroAcao}</p>}

      <ul>
        {data.map((membro) => (
          <li key={membro.userId}>
            <span>{membro.displayName}</span> <span data-testid={`papel-${membro.userId}`}>{membro.role}</span>{" "}
            {membro.role === "MEMBER" && (
              <button
                type="button"
                onClick={() => nomear(membro.userId)}
                disabled={aProcessar === membro.userId}
              >
                Nomear moderador
              </button>
            )}
            {membro.role === "MODERATOR" && (
              <button
                type="button"
                onClick={() => remover(membro.userId)}
                disabled={aProcessar === membro.userId}
              >
                Remover moderador
              </button>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
