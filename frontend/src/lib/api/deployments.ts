import { api } from "./client";
import { AppDeploymentResponse, Page } from "@/types/api";

export const deploymentsApi = {
  getByService(
    serviceId: string,
    page = 0,
    size = 10,
  ): Promise<Page<AppDeploymentResponse>> {
    return api.get<Page<AppDeploymentResponse>>(
      `/deployments?serviceId=${serviceId}&page=${page}&size=${size}`,
    );
  },
};
