import { api } from "./client";
import {
  AppServiceCreationDto,
  AppServiceResponse,
  Page,
} from "@/types/api";

export const servicesApi = {
  list(page = 0, size = 10): Promise<Page<AppServiceResponse>> {
    return api.get<Page<AppServiceResponse>>(`/services?page=${page}&size=${size}`);
  },

  getById(id: string): Promise<AppServiceResponse> {
    return api.get<AppServiceResponse>(`/services/${id}`);
  },

  create(dto: AppServiceCreationDto): Promise<AppServiceResponse> {
    return api.post<AppServiceResponse>("/services", dto);
  },

  delete(id: string): Promise<void> {
    return api.delete<void>(`/services/${id}`);
  },
};
