import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";

import { ApiError } from "@/lib/api";
import { criarComunidade, eurosParaCentimos } from "@/lib/communities";

/** {@code /comunidades/nova}: formulário de criação (R2, critério 1). */
export function CreateCommunityPage() {
  const navigate = useNavigate();

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [avatarUrl, setAvatarUrl] = useState("");
  const [bannerUrl, setBannerUrl] = useState("");
  const [precoEuros, setPrecoEuros] = useState("0");
  const [erro, setErro] = useState<string | null>(null);
  const [aEnviar, setAEnviar] = useState(false);

  async function submeter(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErro(null);
    setAEnviar(true);
    try {
      const comunidade = await criarComunidade({
        name,
        description: description.trim() || undefined,
        avatarUrl: avatarUrl.trim() || undefined,
        bannerUrl: bannerUrl.trim() || undefined,
        priceMonthlyCents: eurosParaCentimos(precoEuros),
      });
      navigate(`/comunidades/${comunidade.slug}/definicoes`, { replace: true });
    } catch (excecao) {
      setErro(excecao instanceof ApiError ? excecao.detail : "Não foi possível criar a comunidade.");
    } finally {
      setAEnviar(false);
    }
  }

  return (
    <div>
      <h1>Criar comunidade</h1>
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

        <button type="submit" disabled={aEnviar}>
          Criar comunidade
        </button>
      </form>
    </div>
  );
}
