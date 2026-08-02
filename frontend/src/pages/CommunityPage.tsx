import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";

import { ResubscribeNotice } from "@/components/ResubscribeNotice";
import { Alert } from "@/components/ui/Alert";
import { Avatar } from "@/components/ui/Avatar";
import { StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { IconArrowRight, IconLock, IconShield, IconSparkles } from "@/components/ui/Icons";
import { Price } from "@/components/ui/Price";
import { Skeleton } from "@/components/ui/Skeleton";
import { Spinner } from "@/components/ui/Spinner";
import { ApiError } from "@/lib/api";
import { obterComunidade } from "@/lib/communities";
import { formatarData } from "@/lib/format";
import { pode } from "@/lib/permissions";
import { cancelarSubscricao, obterAcesso, obterAreaDeMembro, subscrever } from "@/lib/subscriptions";

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
    return (
      <div className="space-y-6">
        <Skeleton className="h-44 w-full rounded-3xl" />
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-40 w-full rounded-2xl" />
      </div>
    );
  }

  if (comunidadeQuery.isError || !comunidadeQuery.data) {
    return <Alert>Não foi possível carregar esta comunidade.</Alert>;
  }

  const comunidade = comunidadeQuery.data;
  const acesso = acessoQuery.data;
  // Subscritor a sério: nem dono nem moderador, e com acesso premium em vigor.
  const membroAtivo = acesso !== undefined && !acesso.manager && acesso.premium;

  return (
    <div className="space-y-8">
      {/* Banner + identidade da comunidade. */}
      <section>
        <div
          className="relative h-36 overflow-hidden rounded-3xl border border-white/[0.07] bg-gradient-to-br from-brand-500/25 via-ink-800 to-sky-500/15 sm:h-48"
          style={
            comunidade.bannerUrl
              ? {
                  backgroundImage: `url(${comunidade.bannerUrl})`,
                  backgroundSize: "cover",
                  backgroundPosition: "center",
                }
              : undefined
          }
        >
          <div className="absolute inset-0 bg-gradient-to-t from-ink-950 via-ink-950/30 to-transparent" />
        </div>

        {/* `relative z-10`: o banner é `relative`, logo pinta por cima de
            irmãos estáticos — sem isto o nome ficava escondido atrás dele. */}
        <div className="relative z-10 -mt-10 flex flex-col gap-4 px-1 sm:-mt-12 sm:flex-row sm:items-end sm:justify-between">
          <div className="flex items-end gap-4">
            <Avatar
              name={comunidade.name}
              url={comunidade.avatarUrl}
              size="xl"
              className="ring-4 ring-ink-950"
            />
            <div className="min-w-0 pb-1">
              <h1 className="truncate text-2xl font-semibold tracking-tight text-white sm:text-3xl">
                {comunidade.name}
              </h1>
              <p className="mt-1 truncate text-sm text-ink-400">
                por {comunidade.ownerDisplayName}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 pb-1">
            {comunidade.status === "SUSPENDED" && <StatusBadge value={comunidade.status} />}
            {/* O papel é a forma curta de dizer "és dono ou moderador desta comunidade". */}
            {acesso?.manager && acesso.role && <StatusBadge value={acesso.role} />}
          </div>
        </div>

        {comunidade.description && (
          <p className="mt-5 max-w-2xl text-sm leading-relaxed text-ink-300">
            {comunidade.description}
          </p>
        )}
      </section>

      {erroAcao && <Alert>{erroAcao}</Alert>}

      <div className="grid gap-6 lg:grid-cols-[1fr_19rem] lg:items-start">
        {/* Área de membro: o conteúdo pelo qual se paga. */}
        <section className="order-2 lg:order-1">
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-[0.12em] text-ink-400">
            Área de membro
          </h2>

          {areaDeMembroQuery.isLoading && (
            <Card className="flex items-center gap-3 p-6 text-sm text-ink-400">
              <Spinner className="h-4 w-4 text-brand-400" />
              A carregar área de membro...
            </Card>
          )}

          {areaDeMembroQuery.isSuccess && (
            <Card className="p-8 text-center">
              <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-400/10 text-brand-300 ring-1 ring-inset ring-brand-400/20">
                <IconSparkles className="h-5 w-5" />
              </div>
              <p className="mt-4 text-base font-medium text-ink-50">Bem-vindo à área de membro.</p>
              <p className="mx-auto mt-2 max-w-sm text-sm text-ink-400">
                As tips desta comunidade aparecem aqui assim que o tipster as publicar.
              </p>
            </Card>
          )}

          {areaDeMembroQuery.isError && slug && (
            <ResubscribeNotice
              slug={slug}
              communityName={comunidade.name}
              priceMonthlyCents={comunidade.priceMonthlyCents}
              currency={comunidade.currency}
            />
          )}
        </section>

        {/* Coluna de estado: preço, subscrição e gestão. */}
        <aside className="order-1 space-y-4 lg:sticky lg:top-8 lg:order-2">
          <Card className="p-5">
            <div className="flex items-baseline justify-between gap-3">
              <Price cents={comunidade.priceMonthlyCents} currency={comunidade.currency} size="lg" />
              {acesso?.status && <StatusBadge value={acesso.status} />}
            </div>

            {!acesso && <Skeleton className="mt-5 h-11 w-full rounded-xl" />}

            {acesso && membroAtivo && acesso.status === "ACTIVE" && (
              <div className="mt-5 space-y-3">
                <p data-testid="estado-subscricao" className="text-sm text-ink-300">
                  Subscrição ativa
                  {acesso.expiresAt && ` até ${formatarData(acesso.expiresAt)}`}
                </p>
                <Button
                  type="button"
                  variant="secondary"
                  fullWidth
                  onClick={cancelarAgora}
                  loading={aProcessar}
                >
                  Cancelar subscrição
                </Button>
              </div>
            )}

            {acesso && membroAtivo && acesso.status === "CANCELLED" && (
              <div className="mt-5">
                <p
                  data-testid="estado-subscricao"
                  className="rounded-xl border border-amber-400/25 bg-amber-400/[0.08] px-4 py-3 text-sm text-amber-200"
                >
                  {acesso.expiresAt
                    ? `Subscrição cancelada. Mantém acesso até ${formatarData(acesso.expiresAt)}.`
                    : "Subscrição cancelada."}
                </p>
              </div>
            )}

            {acesso && !acesso.manager && !acesso.premium && (
              <div className="mt-5 space-y-3">
                <Button type="button" fullWidth onClick={subscreverAgora} loading={aProcessar}>
                  Subscrever
                </Button>
                <p className="flex items-center justify-center gap-1.5 text-xs text-ink-500">
                  <IconLock className="h-3.5 w-3.5" />
                  Cancela quando quiseres.
                </p>
              </div>
            )}
          </Card>

          {acesso && slug && pode(acesso.permissions, "MANAGE_MODERATORS") && (
            <Card className="p-2">
              <Link
                to={`/comunidades/${slug}/moderadores`}
                className="flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-ink-300 transition hover:bg-white/5 hover:text-ink-50"
              >
                <IconShield className="h-4 w-4 text-ink-400" />
                <span className="flex-1">Gerir moderadores</span>
                <IconArrowRight className="h-4 w-4 text-ink-500" />
              </Link>
            </Card>
          )}
        </aside>
      </div>
    </div>
  );
}
