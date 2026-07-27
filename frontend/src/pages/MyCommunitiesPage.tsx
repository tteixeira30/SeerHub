import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";

import { centimosParaEuros, listarMinhasComunidades } from "@/lib/communities";

/** {@code /comunidades}: as comunidades de que o utilizador autenticado é dono. */
export function MyCommunitiesPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["communities", "mine"],
    queryFn: listarMinhasComunidades,
  });

  if (isLoading) {
    return <p>A carregar as suas comunidades...</p>;
  }

  if (isError || !data) {
    return <p>Não foi possível carregar as suas comunidades.</p>;
  }

  return (
    <div>
      <h1>As minhas comunidades</h1>
      <Link to="/comunidades/nova">Criar comunidade</Link>

      {data.length === 0 ? (
        <p>Ainda não tem nenhuma comunidade. Que tal criar a primeira?</p>
      ) : (
        <ul>
          {data.map((comunidade) => (
            <li key={comunidade.id}>
              <span>{comunidade.name}</span>{" "}
              <span>({comunidade.slug})</span>{" "}
              <span>{comunidade.status}</span>{" "}
              <span>{centimosParaEuros(comunidade.priceMonthlyCents)} €</span>{" "}
              <Link to={`/comunidades/${comunidade.slug}/definicoes`}>Definições</Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
