/**
 * Cliente HTTP fino para chamar o backend do SeerHub.
 *
 * Todo pedido envia um {@link X-Correlation-Id} próprio (gerado com
 * {@code crypto.randomUUID()}), para que o par cliente↔log seja
 * rastreável mesmo quando o backend gera o seu próprio id (o filtro do
 * backend só gera um novo quando o cabeçalho está ausente ou inválido).
 *
 * F01 acrescenta a sessão (D-9 do plano): o access token vive só em
 * memória (variável de módulo, esquece-se ao recarregar a página); o
 * refresh token persiste em `localStorage` para sobreviver a um reload.
 * Em `401` fora dos caminhos `/api/auth/*`, tenta renovar a sessão **uma
 * única vez** (promessa partilhada, para pedidos concorrentes não
 * dispararem várias renovações) e repete o pedido original; se a
 * renovação falhar, limpa a sessão e propaga o erro original sem tentar
 * de novo.
 */

export const CORRELATION_ID_HEADER = "X-Correlation-Id";
const CHAVE_REFRESH_TOKEN = "seerhub.refreshToken";

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

/** Corpo comum a registo, login e refresh (espelha `AuthResponse` do backend). */
export interface AuthResponseBody {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: unknown;
}

function baseUrl(): string {
  return import.meta.env.VITE_API_BASE_URL ?? "";
}

// Access token só em memória (D-9): nunca em localStorage/sessionStorage.
let accessTokenAtual: string | null = null;
// Promessa partilhada de renovação (single-flight): pedidos concorrentes que
// recebam 401 esperam pela mesma renovação em vez de disparar uma cada.
let promessaDeRenovacao: Promise<void> | null = null;

export function setSession(accessToken: string, refreshToken: string): void {
  accessTokenAtual = accessToken;
  localStorage.setItem(CHAVE_REFRESH_TOKEN, refreshToken);
}

export function clearSession(): void {
  accessTokenAtual = null;
  localStorage.removeItem(CHAVE_REFRESH_TOKEN);
}

export function getAccessToken(): string | null {
  return accessTokenAtual;
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(CHAVE_REFRESH_TOKEN);
}

function ehCaminhoDeAutenticacao(path: string): boolean {
  return path.startsWith("/api/auth/");
}

async function renovarSessao(): Promise<void> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    throw new Error("Sem refresh token para renovar a sessão.");
  }

  const resposta = await fetch(`${baseUrl()}/api/auth/refresh`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      [CORRELATION_ID_HEADER]: crypto.randomUUID(),
    },
    body: JSON.stringify({ refreshToken }),
  });

  if (!resposta.ok) {
    throw new Error("Não foi possível renovar a sessão.");
  }

  const corpo = (await resposta.json()) as AuthResponseBody;
  setSession(corpo.accessToken, corpo.refreshToken);
}

async function tratarRespostaComErro(response: Response): Promise<never> {
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

export async function apiFetch<T>(path: string, init?: RequestInit, _repetido = false): Promise<T> {
  const correlationId = crypto.randomUUID();

  const headers: Record<string, string> = {
    Accept: "application/json",
    [CORRELATION_ID_HEADER]: correlationId,
    ...((init?.headers as Record<string, string>) ?? {}),
  };

  if (accessTokenAtual) {
    headers.Authorization = `Bearer ${accessTokenAtual}`;
  }

  const response = await fetch(`${baseUrl()}${path}`, { ...init, headers });

  if (response.status === 401 && !_repetido && !ehCaminhoDeAutenticacao(path)) {
    try {
      if (!promessaDeRenovacao) {
        promessaDeRenovacao = renovarSessao().finally(() => {
          promessaDeRenovacao = null;
        });
      }
      await promessaDeRenovacao;
      return await apiFetch<T>(path, init, true);
    } catch {
      clearSession();
      return tratarRespostaComErro(response);
    }
  }

  if (!response.ok) {
    return tratarRespostaComErro(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}
