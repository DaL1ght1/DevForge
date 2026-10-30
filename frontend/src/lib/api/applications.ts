import { api } from "./client";
import {
  ApplicationCreationDto,
  ApplicationResponse,
  Page,
  PreviewFileResponse,
  ServicePreviewResponse,
} from "@/types/api";

export const applicationsApi = {
  list(
    page = 0,
    size = 20,
    workspaceId?: string,
  ): Promise<Page<ApplicationResponse>> {
    const workspace = workspaceId
      ? `&workspaceId=${encodeURIComponent(workspaceId)}`
      : "";
    return api.get<Page<ApplicationResponse>>(
      `/applications?page=${page}&size=${size}${workspace}`,
    );
  },

  getById(id: string): Promise<ApplicationResponse> {
    return api.get<ApplicationResponse>(
      `/applications/${encodeURIComponent(id)}`,
    );
  },

  create(dto: ApplicationCreationDto): Promise<ApplicationResponse> {
    return api.post<ApplicationResponse>("/applications", dto);
  },

  preview(
    dto: ApplicationCreationDto,
    signal?: AbortSignal,
  ): Promise<ServicePreviewResponse> {
    return api.post<ServicePreviewResponse>("/applications/preview", dto, {
      signal,
    });
  },

  previewFile(
    previewId: string,
    path: string,
    signal?: AbortSignal,
  ): Promise<PreviewFileResponse> {
    return api.get<PreviewFileResponse>(
      `/applications/preview/${encodeURIComponent(previewId)}/file?path=${encodeURIComponent(path)}`,
      { signal },
    );
  },
};
