import { api } from "./client";
import { Page, UserResponseDto, UserUpdateDto } from "@/types/api";

export const usersApi = {
  getCurrentUser(): Promise<UserResponseDto> {
    return api.post<UserResponseDto>("/users/current");
  },

  getById(id: string): Promise<UserResponseDto> {
    return api.get<UserResponseDto>(`/users/${id}`);
  },

  update(id: string, dto: UserUpdateDto): Promise<UserResponseDto> {
    return api.put<UserResponseDto>(`/users/${id}`, dto);
  },

  list(page = 0, size = 10): Promise<Page<UserResponseDto>> {
    return api.get<Page<UserResponseDto>>(`/users?page=${page}&size=${size}`);
  },

  delete(id: string): Promise<void> {
    return api.delete<void>(`/users/${id}`);
  },
};
