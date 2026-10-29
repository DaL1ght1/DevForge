"use client";

import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { WorkspaceResponse } from "@/types/api";
import { workspacesApi } from "@/lib/api/workspaces";
import { useAuth } from "@/context/AuthContext";

const STORAGE_KEY = "devforge:active-workspace";

interface WorkspaceContextType {
  workspaces: WorkspaceResponse[];
  activeWorkspace: WorkspaceResponse | null;
  isLoading: boolean;
  setActiveWorkspaceId: (id: string) => void;
  refreshWorkspaces: () => Promise<WorkspaceResponse[]>;
}

const WorkspaceContext = createContext<WorkspaceContextType | undefined>(
  undefined,
);

// The remembered workspace is a per-browser convenience; storage may be unavailable.
function readStoredId(): string | null {
  try {
    return window.localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function storeId(id: string) {
  try {
    window.localStorage.setItem(STORAGE_KEY, id);
  } catch {
    // Ignore: the selection just won't survive a reload.
  }
}

export function WorkspaceProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const [workspaces, setWorkspaces] = useState<WorkspaceResponse[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const applyWorkspaces = useCallback((list: WorkspaceResponse[]) => {
    setWorkspaces(list);
    setActiveId((current) => {
      const preferred = current ?? readStoredId();
      if (preferred && list.some((w) => w.id === preferred)) return preferred;
      return (list.find((w) => w.type === "PERSONAL") ?? list[0])?.id ?? null;
    });
  }, []);

  const refreshWorkspaces = useCallback(async () => {
    const list = await workspacesApi.list();
    applyWorkspaces(list);
    return list;
  }, [applyWorkspaces]);

  useEffect(() => {
    if (!user) {
      queueMicrotask(() => {
        setWorkspaces([]);
        setActiveId(null);
        setIsLoading(false);
      });
      return;
    }
    let cancelled = false;
    queueMicrotask(() => setIsLoading(true));
    workspacesApi
      .list()
      .then((list) => {
        if (!cancelled) applyWorkspaces(list);
      })
      .catch((err) => {
        if (!cancelled) console.error("Failed to load workspaces", err);
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [user, applyWorkspaces]);

  const setActiveWorkspaceId = useCallback((id: string) => {
    setActiveId(id);
    storeId(id);
  }, []);

  const activeWorkspace = useMemo(
    () => workspaces.find((w) => w.id === activeId) ?? null,
    [workspaces, activeId],
  );

  return (
    <WorkspaceContext.Provider
      value={{
        workspaces,
        activeWorkspace,
        isLoading,
        setActiveWorkspaceId,
        refreshWorkspaces,
      }}
    >
      {children}
    </WorkspaceContext.Provider>
  );
}

export function useWorkspace() {
  const context = useContext(WorkspaceContext);
  if (!context) {
    throw new Error("useWorkspace must be used within a WorkspaceProvider");
  }
  return context;
}
