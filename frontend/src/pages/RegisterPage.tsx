import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";

import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";

/** Formulário de {@code /registo}: email, password e nome a mostrar opcional. */
export function RegisterPage() {
  const { registar } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [aEnviar, setAEnviar] = useState(false);

  async function submeter(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErro(null);
    setAEnviar(true);
    try {
      await registar(email, password, displayName.trim() || undefined);
      navigate("/conta", { replace: true });
    } catch (excecao) {
      setErro(excecao instanceof ApiError ? excecao.detail : "Não foi possível concluir o registo.");
    } finally {
      setAEnviar(false);
    }
  }

  return (
    <div>
      <div className="mb-7">
        <h1 className="text-xl font-semibold tracking-tight text-white">Criar conta</h1>
        <p className="mt-1.5 text-sm text-ink-400">
          Segue tipsters ou cria a tua própria comunidade.
        </p>
      </div>

      <form onSubmit={submeter} className="space-y-5">
        <Field
          id="email"
          label="Email"
          type="email"
          autoComplete="email"
          placeholder="tu@exemplo.pt"
          value={email}
          onChange={(evento) => setEmail(evento.target.value)}
          required
        />

        <Field
          id="password"
          label="Password"
          type="password"
          autoComplete="new-password"
          placeholder="••••••••••"
          hint="Pelo menos 10 caracteres."
          value={password}
          onChange={(evento) => setPassword(evento.target.value)}
          minLength={10}
          required
        />

        <Field
          id="displayName"
          label="Nome a mostrar (opcional)"
          type="text"
          autoComplete="nickname"
          placeholder="Como queres aparecer nas comunidades"
          value={displayName}
          onChange={(evento) => setDisplayName(evento.target.value)}
        />

        {erro && <Alert>{erro}</Alert>}

        <Button type="submit" fullWidth size="lg" loading={aEnviar}>
          Criar conta
        </Button>
      </form>

      <p className="mt-7 text-center text-sm text-ink-400">
        Já tens conta?{" "}
        <Link
          to="/entrar"
          className="rounded font-medium text-brand-400 underline-offset-4 transition hover:text-brand-300 hover:underline"
        >
          Entrar
        </Link>
      </p>
    </div>
  );
}
