/**
 * Cliente de papéis e permissões (F04). Espelha `CommunityMemberResponse`,
 * `CommunityRoleResponse` e o catálogo `CommunityPermission` do backend;
 * todas as chamadas passam por `apiFetch` (F01) — nunca `fetch` direto.
 */

import { apiFetch } from "@/lib/api";

export type PapelComunidade = "OWNER" | "MODERATOR" | "MEMBER";
type EstadoMembership = "ACTIVE" | "CANCELLED" | "EXPIRED";

/** Espelha o catálogo fechado `pt.seerhub.community.domain.CommunityPermission`. */
export type PermissaoComunidade =
  | "READ_TIPS"
  | "READ_CHAT"
  | "WRITE_CHAT"
  | "PUBLISH_TIPS"
  | "SETTLE_TIPS"
  | "DELETE_ANY_CHAT_MESSAGE"
  | "MANAGE_MODERATORS"
  | "EDIT_COMMUNITY"
  | "DELETE_COMMUNITY";

/** Espelha `CommunityMemberResponse` — nunca traz email (D-10 do plano de F04). */
export interface MembroComunidade {
  userId: number;
  username: string;
  displayName: string;
  role: PapelComunidade;
  status: EstadoMembership;
  joinedAt: string | null;
  expiresAt: string | null;
  active: boolean;
}

/** Espelha `CommunityRoleResponse` (`GET /api/me/community-roles`). */
export interface PapelEmComunidade {
  communityId: number;
  slug: string;
  name: string;
  role: PapelComunidade;
  premium: boolean;
  permissions: PermissaoComunidade[];
}

export function listarMembros(slug: string): Promise<MembroComunidade[]> {
  return apiFetch<MembroComunidade[]>(`/api/communities/${slug}/members`);
}

export function nomearModerador(slug: string, userId: number): Promise<MembroComunidade> {
  return apiFetch<MembroComunidade>(`/api/communities/${slug}/moderators`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userId }),
  });
}

export function removerModerador(slug: string, userId: number): Promise<MembroComunidade> {
  return apiFetch<MembroComunidade>(`/api/communities/${slug}/moderators/${userId}`, {
    method: "DELETE",
  });
}

export function listarOsMeusPapeis(): Promise<PapelEmComunidade[]> {
  return apiFetch<PapelEmComunidade[]>("/api/me/community-roles");
}

/** Helper puro: `permissoes` inclui `permissao`? Usado para decidir o que a UI desenha. */
export function pode(permissoes: PermissaoComunidade[] | undefined, permissao: PermissaoComunidade): boolean {
  return Boolean(permissoes?.includes(permissao));
}
