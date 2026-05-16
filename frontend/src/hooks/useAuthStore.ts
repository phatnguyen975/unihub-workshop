// src/hooks/useAuthStore.ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { LoginUserResponse } from '../types';

interface AuthState {
  user: LoginUserResponse | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  setAuth: (user: LoginUserResponse, token: string) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      accessToken: null,
      isAuthenticated: false,
      setAuth: (user, accessToken) =>
        set({ user, accessToken, isAuthenticated: true }),
      clearAuth: () =>
        set({ user: null, accessToken: null, isAuthenticated: false }),
    }),
    { name: 'unihub-auth' }
  )
);
