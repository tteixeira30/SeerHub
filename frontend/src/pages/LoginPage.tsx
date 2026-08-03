import { useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";

import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";

interface EstadoLocalizacao {
  from?: { pathname: string };
}

/** Formulário de {@code /entrar}; mostra {@link ApiError.detail} em erro. */
export function LoginPage() {
  const { entrar } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [aEnviar, setAEnviar] = useState(false);

  async function submeter(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErro(null);
    setAEnviar(true);
    try {
      await entrar(email, password);
      const estado = location.state as EstadoLocalizacao | null;
      navigate(estado?.from?.pathname ?? "/conta", { replace: true });
    } catch (excecao) {
      setErro(excecao instanceof ApiError ? excecao.detail : "Não foi possível entrar.");
    } finally {
      setAEnviar(false);
    }
  }

  return (
    <div>
      <div className="mb-7">
        <h1 className="text-xl font-semibold tracking-tight text-white">Entrar</h1>
        <p className="mt-1.5 text-sm text-ink-400">Bem-vindo de volta ao teu hub.</p>
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
          autoComplete="current-password"
          placeholder="••••••••••"
          value={password}
          onChange={(evento) => setPassword(evento.target.value)}
          required
        />

        {erro && <Alert>{erro}</Alert>}

        <Button type="submit" fullWidth size="lg" loading={aEnviar}>
          Entrar
        </Button>
      </form>

      <p className="mt-7 text-center text-sm text-ink-400">
        Ainda não tens conta?{" "}
        <Link
          to="/registo"
          className="rounded font-medium text-brand-400 underline-offset-4 transition hover:text-brand-300 hover:underline"
        >
          Criar conta
        </Link>
      </p>
    </div>
  );
}
