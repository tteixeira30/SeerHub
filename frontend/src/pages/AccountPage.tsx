import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";

import { apiFetch } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import type { Utilizador } from "@/lib/auth";

/** {@code /conta}, protegida por {@code RequireAuth}: mostra `GET /api/users/me`. */
export function AccountPage() {
  const { sair } = useAuth();
  const navigate = useNavigate();

  const { data, isLoading, isError } = useQuery({
    queryKey: ["users", "me"],
    queryFn: () => apiFetch<Utilizador>("/api/users/me"),
  });

  async function terminarSessao() {
    await sair();
    navigate("/entrar", { replace: true });
  }

  if (isLoading) {
    return <p>A carregar conta...</p>;
  }

  if (isError || !data) {
    return <p>Não foi possível carregar a conta.</p>;
  }

  return (
    <div>
      <h1>A minha conta</h1>
      <p>Email: {data.email}</p>
      <p>Utilizador: {data.username}</p>
      <p>Nome a mostrar: {data.displayName}</p>
      <p>Papel: {data.globalRole}</p>
      <button onClick={() => void terminarSessao()}>Terminar sessão</button>
    </div>
  );
}
