import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api";

interface HealthResponse {
  status: string;
  components?: Record<string, { status: string }>;
}

/**
 * Página trivial que prova a cadeia toda: frontend → proxy → backend →
 * base de dados. Não é uma funcionalidade de negócio; é a prova de que o
 * esqueleto arranca e comunica.
 */
export function HealthPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["health"],
    queryFn: () => apiFetch<HealthResponse>("/actuator/health"),
  });

  if (isLoading) {
    return <p>A verificar o estado do serviço...</p>;
  }

  if (isError || !data) {
    return <p>DOWN</p>;
  }

  return (
    <div>
      <h1>SeerHub</h1>
      <p>Estado do serviço: {data.status}</p>
    </div>
  );
}
