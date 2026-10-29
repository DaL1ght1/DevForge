import type { WorkspaceResponse, WorkspaceRole } from "@/types/api";

const ROLE_RANK: Record<WorkspaceRole, number> = {
  OWNER: 0,
  ADMIN: 1,
  MEMBER: 2,
};

/**
 * Mirrors the backend rule (WorkspaceRole.atLeast): roles are ordered OWNER > ADMIN > MEMBER.
 * Only hides UI the user cannot use; the backend enforces every check.
 */
export function hasRole(
  workspace: Pick<WorkspaceResponse, "role"> | null | undefined,
  required: WorkspaceRole,
): boolean {
  return !!workspace && ROLE_RANK[workspace.role] <= ROLE_RANK[required];
}
