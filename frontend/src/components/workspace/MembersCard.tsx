"use client";

import { FormEvent, useCallback, useEffect, useId, useState } from "react";
import { UserMinus, UserPlus, Users } from "lucide-react";
import { WorkspaceMemberResponse, WorkspaceResponse } from "@/types/api";
import { workspacesApi } from "@/lib/api/workspaces";
import { hasRole } from "@/lib/workspaceRoles";
import { useAuth } from "@/context/AuthContext";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/Card";
import { Dialog } from "@/components/ui/Dialog";
import { Input } from "@/components/ui/Input";
import { ErrorAlert } from "@/components/common/ErrorAlert";

interface MembersCardProps {
  workspace: WorkspaceResponse;
  /** Called after the current user leaves the workspace. */
  onLeft: () => void;
}

export function MembersCard({ workspace, onLeft }: MembersCardProps) {
  const { user } = useAuth();
  const formId = useId();
  const isOrganization = workspace.type === "ORGANIZATION";
  const isAdmin = hasRole(workspace, "ADMIN");
  const isOwner = workspace.role === "OWNER";
  const [members, setMembers] = useState<WorkspaceMemberResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [adding, setAdding] = useState(false);
  const [removing, setRemoving] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [identifier, setIdentifier] = useState("");
  const [role, setRole] = useState<"MEMBER" | "ADMIN">("MEMBER");
  const [removeTarget, setRemoveTarget] =
    useState<WorkspaceMemberResponse | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setMembers(await workspacesApi.listMembers(workspace.id));
    } catch (err) {
      setError(
        err instanceof Error ? err : new Error("Failed to load members"),
      );
    } finally {
      setLoading(false);
    }
  }, [workspace.id]);

  useEffect(() => {
    queueMicrotask(() => void load());
  }, [load]);

  const handleAdd = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setAdding(true);
    setError(null);
    setStatus(null);
    try {
      const added = await workspacesApi.addMember(workspace.id, {
        usernameOrEmail: identifier.trim(),
        role,
      });
      setStatus(`Added ${added.username} as ${added.role.toLowerCase()}.`);
      setIdentifier("");
      setRole("MEMBER");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error("Could not add member"));
    } finally {
      setAdding(false);
    }
  };

  const handleRemove = async () => {
    if (!removeTarget) return;
    const leaving = removeTarget.userId === user?.id;
    setRemoving(true);
    setError(null);
    setStatus(null);
    try {
      await workspacesApi.removeMember(workspace.id, removeTarget.userId);
      setRemoveTarget(null);
      if (leaving) {
        onLeft();
        return;
      }
      setStatus(`Removed ${removeTarget.username}.`);
      await load();
    } catch (err) {
      setError(
        err instanceof Error ? err : new Error("Could not remove member"),
      );
      setRemoveTarget(null);
    } finally {
      setRemoving(false);
    }
  };

  // Mirrors the backend: owners remove anyone, admins remove members, everyone may leave.
  const canRemove = (member: WorkspaceMemberResponse) =>
    isOrganization &&
    (member.userId === user?.id ||
      isOwner ||
      (isAdmin && member.role === "MEMBER"));

  const leavingSelf = removeTarget?.userId === user?.id;

  return (
    <Card className="border-zinc-800 bg-zinc-900/60">
      <CardHeader className="pb-3 border-b border-zinc-800/80">
        <CardTitle className="text-sm text-zinc-100 flex items-center gap-2">
          <Users className="h-4 w-4 text-sky-400" />
          Members
        </CardTitle>
        <CardDescription>
          {isOrganization
            ? "Owners and admins create services and manage the GitHub connection; members can view."
            : "A personal workspace belongs to you alone."}
        </CardDescription>
      </CardHeader>
      <CardContent className="p-5 space-y-4 text-xs">
        <div role="status" aria-live="polite">
          {status && (
            <p className="rounded-md border border-emerald-900/60 bg-emerald-950/30 p-3 text-emerald-200">
              ✓ {status}
            </p>
          )}
        </div>
        <ErrorAlert error={error} />

        {loading ? (
          <p className="text-zinc-500">Loading members…</p>
        ) : (
          <ul className="divide-y divide-zinc-800/80 rounded-lg border border-zinc-800 bg-zinc-950/60">
            {members.map((member) => (
              <li
                key={member.userId}
                className="flex items-center justify-between gap-3 p-3"
              >
                <div className="min-w-0">
                  <div className="font-mono text-zinc-100 truncate">
                    {member.username}
                    {member.userId === user?.id && (
                      <span className="ml-1.5 text-zinc-500">(you)</span>
                    )}
                  </div>
                  <div className="text-[11px] text-zinc-500 truncate">
                    {member.email}
                  </div>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  <Badge
                    variant={member.role === "MEMBER" ? "outline" : "info"}
                    size="sm"
                  >
                    {member.role}
                  </Badge>
                  {canRemove(member) && (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => setRemoveTarget(member)}
                      aria-label={
                        member.userId === user?.id
                          ? "Leave workspace"
                          : `Remove ${member.username}`
                      }
                    >
                      <UserMinus className="h-3.5 w-3.5" />
                    </Button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}

        {isOrganization && isAdmin && (
          <form
            onSubmit={handleAdd}
            className="space-y-3 border-t border-zinc-800/80 pt-4"
            aria-labelledby={`${formId}-title`}
          >
            <h4
              id={`${formId}-title`}
              className="text-[10px] font-mono uppercase tracking-wider text-zinc-500"
            >
              Add a member
            </h4>
            <Input
              id={`${formId}-identifier`}
              name="usernameOrEmail"
              label="Username or email"
              hint="They must already have a DevForge account."
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              autoComplete="off"
              autoCorrect="off"
              autoCapitalize="off"
              spellCheck={false}
              maxLength={100}
              required
            />
            <fieldset className="space-y-1.5">
              <legend className="text-xs font-medium text-zinc-300">
                Role
              </legend>
              <div className="flex gap-4">
                <label className="flex items-center gap-2 py-1 text-zinc-200">
                  <input
                    type="radio"
                    name="role"
                    value="MEMBER"
                    checked={role === "MEMBER"}
                    onChange={() => setRole("MEMBER")}
                    className="accent-sky-500"
                  />
                  Member
                </label>
                {isOwner && (
                  <label className="flex items-center gap-2 py-1 text-zinc-200">
                    <input
                      type="radio"
                      name="role"
                      value="ADMIN"
                      checked={role === "ADMIN"}
                      onChange={() => setRole("ADMIN")}
                      className="accent-sky-500"
                    />
                    Admin
                  </label>
                )}
              </div>
            </fieldset>
            <Button
              type="submit"
              variant="secondary"
              size="sm"
              isLoading={adding}
            >
              <UserPlus className="h-3.5 w-3.5" />
              Add member
            </Button>
          </form>
        )}
      </CardContent>

      <Dialog
        isOpen={removeTarget !== null}
        onClose={() => setRemoveTarget(null)}
        title={leavingSelf ? "Leave workspace" : "Remove member"}
        description={
          leavingSelf
            ? "You will lose access to this workspace and its services."
            : "They will lose access to this workspace and its services."
        }
      >
        <div className="flex justify-end gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setRemoveTarget(null)}
          >
            Cancel
          </Button>
          <Button
            variant="danger"
            size="sm"
            isLoading={removing}
            onClick={() => void handleRemove()}
          >
            {leavingSelf ? "Leave" : `Remove ${removeTarget?.username ?? ""}`}
          </Button>
        </div>
      </Dialog>
    </Card>
  );
}
