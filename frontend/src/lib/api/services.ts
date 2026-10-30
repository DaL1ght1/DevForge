import { api } from "./client";
import {
  AppServiceCreationDto,
  AppServiceResponse,
  Page,
  PreviewFileResponse,
  ServicePreviewResponse,
} from "@/types/api";

export const servicesApi = {
  list(
    page = 0,
    size = 10,
    workspaceId?: string,
  ): Promise<Page<AppServiceResponse>> {
    const workspace = workspaceId
      ? `&workspaceId=${encodeURIComponent(workspaceId)}`
      : "";
    return api.get<Page<AppServiceResponse>>(
      `/services?page=${page}&size=${size}${workspace}`,
    );
  },

  getById(id: string): Promise<AppServiceResponse> {
    return api.get<AppServiceResponse>(`/services/${id}`);
  },

  create(dto: AppServiceCreationDto): Promise<AppServiceResponse> {
    return api.post<AppServiceResponse>("/services", dto);
  },

  preview(
    dto: AppServiceCreationDto,
    signal?: AbortSignal,
  ): Promise<ServicePreviewResponse> {
    return api.post<ServicePreviewResponse>("/services/preview", dto, {
      signal,
    });
  },

  previewFile(
    previewId: string,
    path: string,
    signal?: AbortSignal,
  ): Promise<PreviewFileResponse> {
    return api.get<PreviewFileResponse>(
      `/services/preview/${encodeURIComponent(previewId)}/file?path=${encodeURIComponent(path)}`,
      { signal },
    );
  },

  delete(id: string): Promise<void> {
    return api.delete<void>(`/services/${id}`);
  },
};
