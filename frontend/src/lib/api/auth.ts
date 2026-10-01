import { api } from "./client";
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

  refresh(request: RefreshTokenRequest): Promise<AuthResponse> {
    return api.post<AuthResponse>("/auth/refresh", request, { skipAuth: true });
  },

  register(dto: UserCreationDto): Promise<UserResponseDto> {
    return api.post<UserResponseDto>("/users/register", dto, { skipAuth: true });
  },
};
