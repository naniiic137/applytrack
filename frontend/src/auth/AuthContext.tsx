import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { configureClient } from '../api/client';
import { api } from '../api/endpoints';
import type { AuthResponse, User } from '../api/types';

export const STORAGE_KEY = 'applytrack.session';

interface Session {
  token: string;
  expiresAt: string;
  user: User;
}

interface AuthContextValue {
  user: User | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function readSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as Session;
    if (new Date(session.expiresAt).getTime() <= Date.now()) {
      localStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [session, setSession] = useState<Session | null>(readSession);

  const logout = useCallback(() => {
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // storage unavailable - nothing to clear
    }
    setSession(null);
    queryClient.clear();
  }, [queryClient]);

  // Configure synchronously so the very first request already carries the token.
  configureClient({ getToken: () => session?.token ?? null, onUnauthorized: logout });

  // Keep tabs in sync: localStorage fires 'storage' in the *other* tabs of the same origin.
  // Logging out in one tab logs out all of them; a login in another tab (maybe as someone else) is adopted.
  const tokenRef = useRef(session?.token);
  tokenRef.current = session?.token;
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key !== null && event.key !== STORAGE_KEY) return; // key null = storage.clear()
      const next = readSession();
      if (next?.token === tokenRef.current) return;
      queryClient.clear(); // cached data belongs to the previous session
      setSession(next);
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [queryClient]);

  // Expire the session client-side at the same moment the JWT expires.
  useEffect(() => {
    if (!session) return;
    const ms = new Date(session.expiresAt).getTime() - Date.now();
    const timer = window.setTimeout(logout, Math.max(0, Math.min(ms, 2 ** 31 - 1)));
    return () => window.clearTimeout(timer);
  }, [session, logout]);

  const start = useCallback((response: AuthResponse) => {
    const next: Session = { token: response.token, expiresAt: response.expiresAt, user: response.user };
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    } catch {
      // private mode etc. - session still works for this tab
    }
    setSession(next);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user: session?.user ?? null,
      isAuthenticated: session !== null,
      login: async (email, password) => start(await api.login(email, password)),
      register: async (email, password, displayName) => start(await api.register(email, password, displayName)),
      logout,
    }),
    [session, start, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
