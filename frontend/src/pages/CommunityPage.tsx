import { useState } from "react";
import { useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";

import { ApiError } from "@/lib/api";
import { centimosParaEuros, obterComunidade } from "@/lib/communities";
import { cancelarSubscricao, obterAcesso, obterAreaDeMembro, subscrever } from "@/lib/subscriptions";
import { ResubscribeNotice } from "@/components/ResubscribeNotice";

/**
 * {@code /comunidades/:slug}: ficha da comunidade + estado da subscrição
 * (Subscrever / Cancelar) + área de membro (R3, critérios 1, 2 e 6). Faz
 * três pedidos deliberadamente separados — perfil, {@code /access} (estado,
 * nunca dá 403) e {@code /member-area} (a porta real, pode dar 403) — um
 * {@code 403} nesta última é o que faz aparecer {@link ResubscribeNotice}.
 */
export function CommunityPage() {
  const { slug } = useParams<{ slug: string }>();
  const queryClient = useQueryClient();

  const comunidadeQuery = useQuery({
    queryKey: ["communities", slug],
    queryFn: () => obterComunidade(slug!),
    enabled: Boolean(slug),
  });

  const acessoQuery = useQuery({
    queryKey: ["communities", slug, "access"],
    queryFn: () => obterAcesso(slug!),
    enabled: Boolean(slug),
  });

  const areaDeMembroQuery = useQuery({
    queryKey: ["communities", slug, "member-area"],
    queryFn: () => obterAreaDeMembro(slug!),
    enabled: Boolean(slug),
    retry: false,
  });

  const [aProcessar, setAProcessar] = useState(false);
  const [erroAcao, setErroAcao] = useState<string | null>(null);

  async function revalidarTudo() {
    await queryClient.invalidateQueries({ queryKey: ["communities", slug] });
  }

  async function subscreverAgora() {
    if (!slug) {
      return;
    }
    setErroAcao(null);
    setAProcessar(true);
    try {
      await subscrever(slug);
      await revalidarTudo();
    } catch (excecao) {
      setErroAcao(excecao instanceof ApiError ? excecao.detail : "Não foi possível subscrever.");
    } finally {
      setAProcessar(false);
    }
  }

  async function cancelarAgora() {
    if (!slug) {
      return;
    }
    setErroAcao(null);
    setAProcessar(true);
    try {
      await cancelarSubscricao(slug);
      await revalidarTudo();
    } catch (excecao) {
      setErroAcao(excecao instanceof ApiError ? excecao.detail : "Não foi possível cancelar.");
    } finally {
      setAProcessar(false);
    }
  }

  if (comunidadeQuery.isLoading) {
    return <p>A carregar comunidade...</p>;
  }

  if (comunidadeQuery.isError || !comunidadeQuery.data) {
    return <p>Não foi possível carregar esta comunidade.</p>;
  }

  const comunidade = comunidadeQuery.data;
  const acesso = acessoQuery.data;

  return (
    <div>
      <h1>{comunidade.name}</h1>
      <p>{centimosParaEuros(comunidade.priceMonthlyCents)} {comunidade.currency} / mês</p>

      {erroAcao && <p role="alert">{erroAcao}</p>}

      {acesso && acesso.manager && <p>É dono ou moderador desta comunidade.</p>}

      {acesso && !acesso.manager && acesso.premium && acesso.status === "ACTIVE" && (
        <div>
          <p data-testid="estado-subscricao">Subscrição ativa até {acesso.expiresAt}</p>
          <button type="button" onClick={cancelarAgora} disabled={aProcessar}>
            Cancelar subscrição
          </button>
        </div>
      )}

      {acesso && !acesso.manager && acesso.premium && acesso.status === "CANCELLED" && (
        <div>
          <p data-testid="estado-subscricao">Subscrição cancelada. Mantém acesso até {acesso.expiresAt}.</p>
        </div>
      )}

      {acesso && !acesso.manager && !acesso.premium && (
        <div>
          <button type="button" onClick={subscreverAgora} disabled={aProcessar}>
            Subscrever
          </button>
        </div>
      )}

      <section>
        <h2>Área de membro</h2>
        {areaDeMembroQuery.isLoading && <p>A carregar área de membro...</p>}
        {areaDeMembroQuery.isSuccess && <p>Bem-vindo à área de membro.</p>}
        {areaDeMembroQuery.isError && slug && (
          <ResubscribeNotice
            slug={slug}
            communityName={comunidade.name}
            priceMonthlyCents={comunidade.priceMonthlyCents}
            currency={comunidade.currency}
          />
        )}
      </section>
    </div>
  );
}
