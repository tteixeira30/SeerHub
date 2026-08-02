import { useEffect, useState, type FormEvent } from "react";
import { useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";

import { CommunityFormFields, type ValoresComunidade } from "@/components/CommunityFormFields";
import { Alert } from "@/components/ui/Alert";
import { Button, ButtonLink } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { IconShield } from "@/components/ui/Icons";
import { PageHeader } from "@/components/ui/PageHeader";
import { Skeleton } from "@/components/ui/Skeleton";
import { ApiError } from "@/lib/api";
import { centimosParaEuros, eurosParaCentimos, guardarComunidade, obterComunidade } from "@/lib/communities";

const VALORES_INICIAIS: ValoresComunidade = {
  name: "",
  description: "",
  avatarUrl: "",
  bannerUrl: "",
  precoEuros: "0",
};

/**
 * {@code /comunidades/:slug/definicoes}: edição pelo dono (R2, critério 3).
 * Se a comunidade estiver suspensa, mostra o aviso mas não desativa o
 * formulário — a suspensão não confisca a gestão ao dono (D-7).
 */
export function CommunitySettingsPage() {
  const { slug } = useParams<{ slug: string }>();

  const { data, isLoading, isError } = useQuery({
    queryKey: ["communities", slug],
    queryFn: () => obterComunidade(slug!),
    enabled: Boolean(slug),
  });

  const [valores, setValores] = useState<ValoresComunidade>(VALORES_INICIAIS);
  const [erro, setErro] = useState<string | null>(null);
  const [sucesso, setSucesso] = useState(false);
  const [aGuardar, setAGuardar] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  useEffect(() => {
    if (data) {
      setValores({
        name: data.name,
        description: data.description ?? "",
        avatarUrl: data.avatarUrl ?? "",
        bannerUrl: data.bannerUrl ?? "",
        precoEuros: centimosParaEuros(data.priceMonthlyCents),
      });
      setStatus(data.status);
    }
  }, [data]);

  function aoMudar(campo: keyof ValoresComunidade, valor: string) {
    setValores((anteriores) => ({ ...anteriores, [campo]: valor }));
    setSucesso(false);
  }

  async function submeter(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (!slug) {
      return;
    }
    setErro(null);
    setSucesso(false);
    setAGuardar(true);
    try {
      const atualizada = await guardarComunidade(slug, {
        name: valores.name,
        description: valores.description.trim() || undefined,
        avatarUrl: valores.avatarUrl.trim() || undefined,
        bannerUrl: valores.bannerUrl.trim() || undefined,
        priceMonthlyCents: eurosParaCentimos(valores.precoEuros),
      });
      setStatus(atualizada.status);
      setSucesso(true);
    } catch (excecao) {
      setErro(excecao instanceof ApiError ? excecao.detail : "Não foi possível guardar as alterações.");
    } finally {
      setAGuardar(false);
    }
  }

  if (isLoading) {
    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <Skeleton className="h-9 w-64" />
        <Skeleton className="h-[28rem] w-full rounded-2xl" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="mx-auto max-w-2xl">
        <Alert>Não foi possível carregar esta comunidade.</Alert>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader
        eyebrow={data.name}
        title="Definições da comunidade"
        actions={
          <ButtonLink to={`/comunidades/${slug}/moderadores`} variant="secondary" size="sm">
            <IconShield className="h-4 w-4" />
            Moderadores
          </ButtonLink>
        }
      />

      {status === "SUSPENDED" && (
        <Alert tone="error" className="mb-6">
          Esta comunidade está suspensa.
        </Alert>
      )}

      <Card className="p-6 sm:p-8">
        <form onSubmit={submeter} className="space-y-6">
          <CommunityFormFields valores={valores} aoMudar={aoMudar} />

          {erro && <Alert>{erro}</Alert>}
          {sucesso && <Alert tone="success">Alterações guardadas.</Alert>}

          <div className="flex justify-end border-t border-white/[0.06] pt-6">
            <Button type="submit" loading={aGuardar}>
              Guardar
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}
