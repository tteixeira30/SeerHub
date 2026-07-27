/**
 * Cliente de subscrições (F03). Espelha `SubscriptionResponse`,
 * `CommunityAccessResponse` e `MemberAreaResponse` do backend; todas as
 * chamadas passam por `apiFetch` (F01).
 */

import { apiFetch } from "@/lib/api";

type Papel = "OWNER" | "MODERATOR" | "MEMBER";
type EstadoSubscricao = "ACTIVE" | "CANCELLED" | "EXPIRED";

/** Espelha `SubscriptionResponse` (`pt.seerhub.community.api.SubscriptionResponse`). */
export interface Subscricao {
  communityId: number;
  slug: string;
  communityName: string;
  priceMonthlyCents: number;
  currency: string;
  role: Papel;
  status: EstadoSubscricao;
  joinedAt: string;
  expiresAt: string | null;
  active: boolean;
}

/** Espelha `CommunityAccessResponse` — nunca resulta de um `403` (D-11). */
export interface AcessoComunidade {
  communityId: number;
  slug: string;
  premium: boolean;
  manager: boolean;
  role: Papel | null;
  status: EstadoSubscricao | null;
  joinedAt: string | null;
  expiresAt: string | null;
  priceMonthlyCents: number;
  currency: string;
}

/** Espelha `MemberAreaResponse` — o conteúdo premium de F03 (D-10). */
export interface AreaDeMembro {
  communityId: number;
  slug: string;
  name: string;
  role: Papel;
  status: EstadoSubscricao;
  joinedAt: string;
  expiresAt: string | null;
}

export function subscrever(slug: string): Promise<Subscricao> {
  return apiFetch<Subscricao>(`/api/communities/${slug}/subscription`, { method: "POST" });
}

export function cancelarSubscricao(slug: string): Promise<Subscricao> {
  return apiFetch<Subscricao>(`/api/communities/${slug}/subscription`, { method: "DELETE" });
}

export function obterAcesso(slug: string): Promise<AcessoComunidade> {
  return apiFetch<AcessoComunidade>(`/api/communities/${slug}/access`);
}

export function obterAreaDeMembro(slug: string): Promise<AreaDeMembro> {
  return apiFetch<AreaDeMembro>(`/api/communities/${slug}/member-area`);
}

export function listarMinhasSubscricoes(): Promise<Subscricao[]> {
  return apiFetch<Subscricao[]>("/api/me/subscriptions");
}
