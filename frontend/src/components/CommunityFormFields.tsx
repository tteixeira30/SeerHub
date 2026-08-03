import { Field, TextAreaField } from "@/components/ui/Field";

/** Os cinco campos que descrevem uma comunidade, tal como o backend os aceita. */
export interface ValoresComunidade {
  name: string;
  description: string;
  avatarUrl: string;
  bannerUrl: string;
  precoEuros: string;
}

/**
 * Campos partilhados por {@code /comunidades/nova} e
 * {@code /comunidades/:slug/definicoes} — os dois formulários escrevem o
 * mesmo objeto, por isso vivem num sítio só. As páginas continuam donas do
 * estado, da submissão e das mensagens de erro.
 */
export function CommunityFormFields({
  valores,
  aoMudar,
  desativado = false,
}: {
  valores: ValoresComunidade;
  aoMudar: (campo: keyof ValoresComunidade, valor: string) => void;
  desativado?: boolean;
}) {
  return (
    <div className="space-y-5">
      <Field
        id="name"
        label="Nome"
        type="text"
        placeholder="Tips do Zé"
        value={valores.name}
        onChange={(evento) => aoMudar("name", evento.target.value)}
        minLength={3}
        maxLength={60}
        disabled={desativado}
        required
      />

      <TextAreaField
        id="description"
        label="Descrição"
        placeholder="Em duas linhas: que tipo de tips partilhas e com que frequência."
        hint="Até 2000 caracteres."
        value={valores.description}
        onChange={(evento) => aoMudar("description", evento.target.value)}
        maxLength={2000}
        disabled={desativado}
      />

      <div className="grid gap-5 sm:grid-cols-2">
        <Field
          id="avatarUrl"
          label="URL do avatar"
          type="text"
          placeholder="https://..."
          value={valores.avatarUrl}
          onChange={(evento) => aoMudar("avatarUrl", evento.target.value)}
          disabled={desativado}
        />

        <Field
          id="bannerUrl"
          label="URL do banner"
          type="text"
          placeholder="https://..."
          value={valores.bannerUrl}
          onChange={(evento) => aoMudar("bannerUrl", evento.target.value)}
          disabled={desativado}
        />
      </div>

      <Field
        id="precoEuros"
        label="Preço mensal (€)"
        type="text"
        inputMode="decimal"
        prefix="€"
        hint="A zero, a comunidade fica gratuita."
        className="sm:max-w-[20rem]"
        value={valores.precoEuros}
        onChange={(evento) => aoMudar("precoEuros", evento.target.value)}
        disabled={desativado}
      />
    </div>
  );
}
