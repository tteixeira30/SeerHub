import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";

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
      <h1>Criar conta</h1>
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
          minLength={10}
          required
        />

        <label htmlFor="displayName">Nome a mostrar (opcional)</label>
        <input
          id="displayName"
          type="text"
          value={displayName}
          onChange={(evento) => setDisplayName(evento.target.value)}
        />

        {erro && <p role="alert">{erro}</p>}

        <button type="submit" disabled={aEnviar}>
          Criar conta
        </button>
      </form>
    </div>
  );
}
