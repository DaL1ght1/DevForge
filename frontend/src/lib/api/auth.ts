import { api, API_BASE_URL } from "./client";
import {
  CompleteSignupRequest,
  LoginRequest,
  SessionResponse,
  SignupPendingResponse,
  SocialProvider,
  UserCreationDto,
  UserResponseDto,
} from "@/types/api";

export function socialLoginUrl(provider: SocialProvider): string {
  return `${API_BASE_URL}/auth/oauth2/authorize/${provider}`;
}
export const authApi = {
  login(request: LoginRequest): Promise<SessionResponse> {
    return api.post<SessionResponse>("/auth/login", request, {
      skipAuth: true,
    });
  },

  register(dto: UserCreationDto): Promise<UserResponseDto> {
    return api.post<UserResponseDto>("/users/register", dto, {
      skipAuth: true,
    });
  },

  logout(): Promise<void> {
    return api.post<void>("/auth/logout", undefined, { skipAuth: true });
  },

  startSocialLogin(provider: SocialProvider): void {
    window.location.assign(socialLoginUrl(provider));
  },

  exchangeTicket(ticket: string): Promise<SessionResponse> {
    return api.post<SessionResponse>(
      "/auth/oauth2/ticket",
      { ticket },
      { skipAuth: true },
    );
  },

  getPendingSignup(signupToken: string): Promise<SignupPendingResponse> {
    return api.post<SignupPendingResponse>(
      "/auth/oauth2/signup/pending",
      { ticket: signupToken },
      { skipAuth: true },
    );
  },

  completeSignup(req: CompleteSignupRequest): Promise<SessionResponse> {
    return api.post<SessionResponse>("/auth/oauth2/signup/complete", req, {
      skipAuth: true,
    });
  },
};
