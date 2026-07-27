import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";

import { centimosParaEuros } from "@/lib/communities";
import { listarMinhasSubscricoes } from "@/lib/subscriptions";

/** {@code /subscricoes}: todas as comunidades que o utilizador subscreve, sem limite (R3, critério 4). */
export function MySubscriptionsPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["subscriptions", "mine"],
    queryFn: listarMinhasSubscricoes,
  });

  if (isLoading) {
    return <p>A carregar as suas subscrições...</p>;
  }

  if (isError || !data) {
    return <p>Não foi possível carregar as suas subscrições.</p>;
  }

  return (
    <div>
      <h1>As minhas subscrições</h1>

      {data.length === 0 ? (
        <p>Ainda não subscreve nenhuma comunidade.</p>
      ) : (
        <ul>
          {data.map((subscricao) => (
            <li key={subscricao.communityId}>
              <span>{subscricao.communityName}</span>{" "}
              <span>{subscricao.status}</span>{" "}
              <span>{centimosParaEuros(subscricao.priceMonthlyCents)} {subscricao.currency}</span>{" "}
              {subscricao.expiresAt && <span>até {subscricao.expiresAt}</span>}{" "}
              <Link to={`/comunidades/${subscricao.slug}`}>Ver comunidade</Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
