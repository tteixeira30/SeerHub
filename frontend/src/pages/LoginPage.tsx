import { useState, type FormEvent } from "react";
import { useLocation, useNavigate } from "react-router-dom";

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
      <h1>Entrar</h1>
      <form onSubmit={submeter}>
        <label htmlFor="email">Email</label>
        <input
          id="email"
          type="email"
          value={email}
          onChange={(evento) => setEmail(evento.target.value)}
          required
        />

        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          value={password}
          onChange={(evento) => setPassword(evento.target.value)}
          required
        />

        {erro && <p role="alert">{erro}</p>}

        <button type="submit" disabled={aEnviar}>
          Entrar
        </button>
      </form>
    </div>
  );
}
