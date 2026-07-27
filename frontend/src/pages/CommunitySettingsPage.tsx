import { useEffect, useState, type FormEvent } from "react";
import { useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";

import { ApiError } from "@/lib/api";
import { centimosParaEuros, eurosParaCentimos, guardarComunidade, obterComunidade } from "@/lib/communities";

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

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [avatarUrl, setAvatarUrl] = useState("");
  const [bannerUrl, setBannerUrl] = useState("");
  const [precoEuros, setPrecoEuros] = useState("0");
  const [erro, setErro] = useState<string | null>(null);
  const [sucesso, setSucesso] = useState(false);
  const [aGuardar, setAGuardar] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  useEffect(() => {
    if (data) {
      setName(data.name);
      setDescription(data.description ?? "");
      setAvatarUrl(data.avatarUrl ?? "");
      setBannerUrl(data.bannerUrl ?? "");
      setPrecoEuros(centimosParaEuros(data.priceMonthlyCents));
      setStatus(data.status);
    }
  }, [data]);

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
        name,
        description: description.trim() || undefined,
        avatarUrl: avatarUrl.trim() || undefined,
        bannerUrl: bannerUrl.trim() || undefined,
        priceMonthlyCents: eurosParaCentimos(precoEuros),
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
    return <p>A carregar comunidade...</p>;
  }

  if (isError || !data) {
    return <p>Não foi possível carregar esta comunidade.</p>;
  }

  return (
    <div>
      <h1>Definições da comunidade</h1>

      {status === "SUSPENDED" && <p role="alert">Esta comunidade está suspensa.</p>}

      <form onSubmit={submeter}>
        <label htmlFor="name">Nome</label>
        <input
          id="name"
          type="text"
          value={name}
          onChange={(evento) => setName(evento.target.value)}
          minLength={3}
          maxLength={60}
          required
        />

        <label htmlFor="description">Descrição</label>
        <textarea
          id="description"
          value={description}
          onChange={(evento) => setDescription(evento.target.value)}
          maxLength={2000}
        />

        <label htmlFor="avatarUrl">URL do avatar</label>
        <input
          id="avatarUrl"
          type="text"
          value={avatarUrl}
          onChange={(evento) => setAvatarUrl(evento.target.value)}
        />

        <label htmlFor="bannerUrl">URL do banner</label>
        <input
          id="bannerUrl"
          type="text"
          value={bannerUrl}
          onChange={(evento) => setBannerUrl(evento.target.value)}
        />

        <label htmlFor="precoEuros">Preço mensal (€)</label>
        <input
          id="precoEuros"
          type="text"
          inputMode="decimal"
          value={precoEuros}
          onChange={(evento) => setPrecoEuros(evento.target.value)}
        />

        {erro && <p role="alert">{erro}</p>}
        {sucesso && <p>Alterações guardadas.</p>}

        <button type="submit" disabled={aGuardar}>
          Guardar
        </button>
      </form>
    </div>
  );
}
