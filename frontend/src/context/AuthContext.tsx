"use client";

import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";
import { useRouter, usePathname } from "next/navigation";
import {
  LoginRequest,
  SessionResponse,
  UserCreationDto,
  UserResponseDto,
} from "@/types/api";
import { authApi } from "@/lib/api/auth";
import { usersApi } from "@/lib/api/users";
import { AUTH_EXPIRED_EVENT } from "@/lib/api/client";

interface AuthContextType {
  user: UserResponseDto | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  completeOAuthLogin: (ticket: string) => Promise<void>;
  completeSocialSignup: (res: SessionResponse) => Promise<void>;
  login: (credentials: LoginRequest) => Promise<void>;
  register: (data: UserCreationDto) => Promise<void>;
  logout: () => Promise<void>;
  refreshProfile: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const PUBLIC_PATHS = [
  "/login",
  "/register",
  "/oauth2/callback",
  "/complete-signup",
];

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserResponseDto | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const router = useRouter();
  const pathname = usePathname();

  const refreshProfile = useCallback(async () => {
    try {
      setUser(await usersApi.getCurrentUser());
    } catch {
      setUser(null);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;

    usersApi
      .getCurrentUser()
      .then((u) => {
        if (!cancelled) setUser(u);
      })
      .catch(() => {})
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const handleAuthExpired = () => setUser(null);
    window.addEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
    return () =>
      window.removeEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
  }, []);

  useEffect(() => {
    if (isLoading) return;
    const isPublic = PUBLIC_PATHS.some((p) => pathname.startsWith(p));
    if (!user && !isPublic) {
      router.push(`/login?redirect=${encodeURIComponent(pathname)}`);
    } else if (user && isPublic) {
      router.push("/services");
    }
  }, [user, isLoading, pathname, router]);

  const applySession = useCallback(
    async (res: SessionResponse) => {
      if (res.user) {
        setUser(res.user);
      } else {
        await refreshProfile();
      }
      router.replace("/services");
    },
    [refreshProfile, router],
  );

  const login = useCallback(
    async (credentials: LoginRequest) => {
      setIsLoading(true);
      try {
        await applySession(await authApi.login(credentials));
      } finally {
        setIsLoading(false);
      }
    },
    [applySession],
  );

  const completeOAuthLogin = useCallback(
    async (ticket: string) => {
      await applySession(await authApi.exchangeTicket(ticket));
    },
    [applySession],
  );

  const register = useCallback(
    async (data: UserCreationDto) => {
      setIsLoading(true);
      try {
        await authApi.register(data);
        await login({ username: data.username, password: data.password });
      } finally {
        setIsLoading(false);
      }
    },
    [login],
  );

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch (error) {
      console.warn("Backend logout failed:", error);
    } finally {
      setUser(null);
      router.push("/login");
    }
  }, [router]);

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        login,
        register,
        logout,
        refreshProfile,
        completeOAuthLogin,
        completeSocialSignup: applySession,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
