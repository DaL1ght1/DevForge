import { ApiErrorResponse } from "@/types/api";

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL ||
  process.env.NEXT_PUBLIC_BACKEND_URL ||
  process.env.BACKEND_URL ||
  "http://localhost:8080/api/v1";

export const AUTH_EXPIRED_EVENT = "devforge:auth-expired";

const CSRF_HEADER = { name: "X-Requested-With", value: "devforge" };

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

let refreshPromise: Promise<boolean> | null = null;

async function refreshSession(): Promise<boolean> {
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/auth/refresh`, {
        method: "POST",
        credentials: "include",
        headers: { [CSRF_HEADER.name]: CSRF_HEADER.value },
      });
      return res.ok;
    } catch {
      return false;
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
  headers.set(CSRF_HEADER.name, CSRF_HEADER.value);

  const init: RequestInit = { ...options, headers, credentials: "include" };

  let response = await fetch(url, init);

  const canRefresh =
    response.status === 401 &&
    !options.skipAuth &&
    !endpoint.startsWith("/auth/");

  if (canRefresh && (await refreshSession())) {
    response = await fetch(url, init);
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
