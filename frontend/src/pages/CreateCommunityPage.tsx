import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";

import { CommunityFormFields, type ValoresComunidade } from "@/components/CommunityFormFields";
import { Alert } from "@/components/ui/Alert";
import { Button, ButtonLink } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { PageHeader } from "@/components/ui/PageHeader";
import { ApiError } from "@/lib/api";
import { criarComunidade, eurosParaCentimos } from "@/lib/communities";

const VALORES_INICIAIS: ValoresComunidade = {
  name: "",
  description: "",
  avatarUrl: "",
  bannerUrl: "",
  precoEuros: "0",
};

/** {@code /comunidades/nova}: formulário de criação (R2, critério 1). */
export function CreateCommunityPage() {
  const navigate = useNavigate();

  const [valores, setValores] = useState<ValoresComunidade>(VALORES_INICIAIS);
  const [erro, setErro] = useState<string | null>(null);
  const [aEnviar, setAEnviar] = useState(false);

  function aoMudar(campo: keyof ValoresComunidade, valor: string) {
    setValores((anteriores) => ({ ...anteriores, [campo]: valor }));
  }

  async function submeter(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErro(null);
    setAEnviar(true);
    try {
      const comunidade = await criarComunidade({
        name: valores.name,
        description: valores.description.trim() || undefined,
        avatarUrl: valores.avatarUrl.trim() || undefined,
        bannerUrl: valores.bannerUrl.trim() || undefined,
        priceMonthlyCents: eurosParaCentimos(valores.precoEuros),
      });
      navigate(`/comunidades/${comunidade.slug}/definicoes`, { replace: true });
    } catch (excecao) {
      setErro(excecao instanceof ApiError ? excecao.detail : "Não foi possível criar a comunidade.");
    } finally {
      setAEnviar(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader
        eyebrow="Nova comunidade"
        title="Criar comunidade"
        description="Podes mudar tudo isto mais tarde nas definições da comunidade."
      />

      <Card className="p-6 sm:p-8">
        <form onSubmit={submeter} className="space-y-6">
          <CommunityFormFields valores={valores} aoMudar={aoMudar} />

          {erro && <Alert>{erro}</Alert>}

          <div className="flex flex-col-reverse gap-3 border-t border-white/[0.06] pt-6 sm:flex-row sm:justify-end">
            <ButtonLink to="/comunidades" variant="ghost">
              Cancelar
            </ButtonLink>
            <Button type="submit" loading={aEnviar}>
              Criar comunidade
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}
