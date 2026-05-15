import { api, clearToken, setToken } from './api';

export interface LoginResponse {
  token: string;
  expiresIn: number;
}

export const authService = {
  login: async (
    username: string,
    password: string
  ): Promise<LoginResponse> => {
    const { data } = await api.post<LoginResponse>('/auth/login', {
      username,
      password,
    });
    setToken(data.token);
    return data;
  },

  logout: (): void => {
    clearToken();
    window.location.href = '/login';
  },
};