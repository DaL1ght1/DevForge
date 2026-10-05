import { api } from "./client";
import {
  AppTemplateResponse,
  Page,
  TemplateFilter,
  TemplateFramework,
  TemplateLanguage,
} from "@/types/api";

export const templatesApi = {
  list(page = 0, size = 50): Promise<Page<AppTemplateResponse>> {
    return api.get<Page<AppTemplateResponse>>(`/templates?page=${page}&size=${size}`);
  },
  getByLanguage(
    language: TemplateLanguage | string,
    page = 0,
    size = 50
  ): Promise<Page<AppTemplateResponse>> {
    return api.get<Page<AppTemplateResponse>>(
      `/templates/by-languages/${encodeURIComponent(language)}?page=${page}&size=${size}`
    );
  },

  getByFramework(
    framework: TemplateFramework | string,
    page = 0,
    size = 50
  ): Promise<Page<AppTemplateResponse>> {
    return api.get<Page<AppTemplateResponse>>(
      `/templates/by-framework/${encodeURIComponent(framework)}?page=${page}&size=${size}`
    );
  },

  async filter(
      filter: TemplateFilter,
      page = 0,
      size = 50
  ): Promise<Page<AppTemplateResponse>> {
    const params = new URLSearchParams();
    params.set("page", String(page));
    params.set("size", String(size));
    if (filter.framework) params.set("framework", filter.framework);
    if (filter.buildTool) params.set("buildTool", filter.buildTool);

    try {
      return await api
          .post<Page<AppTemplateResponse>>(`/templates/filter?${params.toString()}`, filter);
    } catch {
      return await api.get<Page<AppTemplateResponse>>(`/templates/filter?${params.toString()}`);
    }
  },
};
