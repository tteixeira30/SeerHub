/**
 * Cliente HTTP fino para chamar o backend do SeerHub.
 *
 * Todo pedido envia um {@link X-Correlation-Id} próprio (gerado com
 * {@code crypto.randomUUID()}), para que o par cliente↔log seja
 * rastreável mesmo quando o backend gera o seu próprio id (o filtro do
 * backend só gera um novo quando o cabeçalho está ausente ou inválido).
 */

export const CORRELATION_ID_HEADER = "X-Correlation-Id";

/** Corresponde ao corpo RFC 7807 (ProblemDetail) devolvido pelo backend em erro. */
export interface ProblemDetailBody {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  correlationId?: string;
  [chave: string]: unknown;
}

export class ApiError extends Error {
  readonly status: number;
  readonly detail: string;
  readonly correlationId: string | undefined;

  constructor(status: number, detail: string, correlationId: string | undefined) {
    super(detail);
    this.name = "ApiError";
    this.status = status;
    this.detail = detail;
    this.correlationId = correlationId;
  }
}

function baseUrl(): string {
  return import.meta.env.VITE_API_BASE_URL ?? "";
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const correlationId = crypto.randomUUID();

  const response = await fetch(`${baseUrl()}${path}`, {
    ...init,
    headers: {
      Accept: "application/json",
      [CORRELATION_ID_HEADER]: correlationId,
      ...(init?.headers ?? {}),
    },
  });

  if (!response.ok) {
    let corpo: ProblemDetailBody = {};
    try {
      corpo = (await response.json()) as ProblemDetailBody;
    } catch {
      // corpo não é JSON (ou está vazio); segue com valores por omissão
    }
    throw new ApiError(
      response.status,
      corpo.detail ?? "Ocorreu um erro inesperado.",
      corpo.correlationId
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}
