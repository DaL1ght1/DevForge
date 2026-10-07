import { api, ApiClientError } from "./client";
import {
  AuthResponse,
  LoginRequest,
  RefreshTokenRequest,
  UserCreationDto,
  UserResponseDto,
} from "@/types/api";

export const authApi = {
  login(request: LoginRequest): Promise<AuthResponse> {
    return api.post<AuthResponse>("/auth/login", request, { skipAuth: true });
  },

  register(dto: UserCreationDto): Promise<UserResponseDto> {
    return api.post<UserResponseDto>("/users/register", dto, {
      skipAuth: true,
    });
  },

  async logout(request?: RefreshTokenRequest): Promise<void> {
    try {
      return await api.post<void>("/auth/logout", request);
    } catch (error) {
      if (
        error instanceof ApiClientError &&
        error.status === 401 &&
        request?.refreshToken
      ) {
        return api.post<void>("/auth/logout", request, { skipAuth: true });
      }
      throw error;
    }
  },
};
