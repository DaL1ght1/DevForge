import { ApiErrorResponse, AuthResponse } from "@/types/api";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL ||
  process.env.NEXT_PUBLIC_BACKEND_URL ||
  process.env.BACKEND_URL ||
  "http://localhost:8080/api/v1";

const ACCESS_TOKEN_KEY = "devforge_access_token";
const REFRESH_TOKEN_KEY = "devforge_refresh_token";
export const AUTH_EXPIRED_EVENT = "devforge:auth-expired";

export class ApiClientError extends Error {
  public status: number;
  public error: string;
  public validationErrors?: Record<string, string>;
  public raw: ApiErrorResponse;

  constructor(res: ApiErrorResponse) {
    super(res.message || res.error || "An unexpected API error occurred");
    this.name = "ApiClientError";
    this.status = res.status;
    this.error = res.error;
    this.validationErrors = res.validationErrors;
    this.raw = res;
  }
}

export const tokenStorage = {
  getAccessToken(): string | null {
    if (typeof window === "undefined") return null;
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  },
  getRefreshToken(): string | null {
    if (typeof window === "undefined") return null;
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  },
  setTokens(accessToken: string, refreshToken?: string) {
    if (typeof window === "undefined") return;
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    if (refreshToken) {
      localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    }
  },
  clear() {
    if (typeof window === "undefined") return;
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  },
};

let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = tokenStorage.getRefreshToken();
  if (!refreshToken) return null;

  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });

      if (!res.ok) {
        tokenStorage.clear();
        return null;
      }

      const data: AuthResponse = await res.json();
      tokenStorage.setTokens(data.accessToken, data.refreshToken);
      return data.accessToken;
    } catch {
      tokenStorage.clear();
      return null;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

interface RequestOptions extends RequestInit {
  skipAuth?: boolean;
}

async function request<T>(
  endpoint: string,
  options: RequestOptions = {},
): Promise<T> {
  const url = `${API_BASE_URL}${endpoint}`;
  const headers = new Headers(options.headers || {});

  if (!headers.has("Content-Type") && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  if (!options.skipAuth) {
    const token = tokenStorage.getAccessToken();
    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }
  }

  let response = await fetch(url, {
    ...options,
    headers,
  });

  if (
    response.status === 401 &&
    !options.skipAuth &&
    !endpoint.startsWith("/auth/")
  ) {
    const newToken = await refreshAccessToken();
    if (newToken) {
      headers.set("Authorization", `Bearer ${newToken}`);
      response = await fetch(url, {
        ...options,
        headers,
      });
    }
  }

  if (response.status === 204) {
    return {} as T;
  }

  if (!response.ok) {
    if (
      response.status === 401 &&
      !options.skipAuth &&
      !endpoint.startsWith("/auth/")
    ) {
      tokenStorage.clear();
      if (typeof window !== "undefined") {
        window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
      }
    }
    let errorData: ApiErrorResponse;
    try {
      errorData = await response.json();
    } catch {
      errorData = {
        status: response.status,
        error: response.statusText,
        message: `HTTP error ${response.status}: ${response.statusText}`,
      };
    }
    throw new ApiClientError(errorData);
  }

  const text = await response.text();
  return text ? JSON.parse(text) : ({} as T);
}

export const api = {
  get<T>(endpoint: string, options?: RequestOptions): Promise<T> {
    return request<T>(endpoint, { ...options, method: "GET" });
  },

  post<T>(
    endpoint: string,
    body?: unknown,
    options?: RequestOptions,
  ): Promise<T> {
    return request<T>(endpoint, {
      ...options,
      method: "POST",
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  },

  put<T>(
    endpoint: string,
    body?: unknown,
    options?: RequestOptions,
  ): Promise<T> {
    return request<T>(endpoint, {
      ...options,
      method: "PUT",
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  },

  delete<T>(endpoint: string, options?: RequestOptions): Promise<T> {
    return request<T>(endpoint, { ...options, method: "DELETE" });
  },
};
