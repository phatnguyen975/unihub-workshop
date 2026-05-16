// src/api/auth.ts
import { apiClient } from './client';
import type { ApiResponse, LoginRequest, LoginResponse } from '../types';

export const authApi = {
  login: (body: LoginRequest) =>
    apiClient.post<ApiResponse<LoginResponse>>('/auth/login', body),

  logout: (refreshToken: string) =>
    apiClient.post('/auth/logout', { refreshToken }),
};
