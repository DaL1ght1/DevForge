import { api } from "./client";
import {
  WorkspaceMemberAddDto,
  WorkspaceMemberResponse,
  WorkspaceResponse,
} from "@/types/api";

export const workspacesApi = {
  list(): Promise<WorkspaceResponse[]> {
    return api.get<WorkspaceResponse[]>("/workspaces");
  },

  getById(id: string): Promise<WorkspaceResponse> {
    return api.get<WorkspaceResponse>(`/workspaces/${id}`);
  },

  createOrganization(name: string): Promise<WorkspaceResponse> {
    return api.post<WorkspaceResponse>("/workspaces", { name });
  },

  listMembers(id: string): Promise<WorkspaceMemberResponse[]> {
    return api.get<WorkspaceMemberResponse[]>(`/workspaces/${id}/members`);
  },

  addMember(
    id: string,
    dto: WorkspaceMemberAddDto,
  ): Promise<WorkspaceMemberResponse> {
    return api.post<WorkspaceMemberResponse>(`/workspaces/${id}/members`, dto);
  },

  removeMember(id: string, userId: string): Promise<void> {
    return api.delete<void>(`/workspaces/${id}/members/${userId}`);
  },
};
