import { api } from "./client";
import { AppTemplateResponse, Page } from "@/types/api";

export const templatesApi = {
  list(page = 0, size = 10): Promise<Page<AppTemplateResponse>> {
    return api.get<Page<AppTemplateResponse>>(`/templates?page=${page}&size=${size}`);
  },

  getById(id: string): Promise<AppTemplateResponse> {
    return api.get<AppTemplateResponse>(`/templates/${id}`);
  },
};
