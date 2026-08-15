import { get, patch, post } from "@/lib/api-client";
import { endpoints } from "@/lib/endpoints";
import type { AuthResponse, PlatformUserResponse } from "@/types/api";
import type {
  ChangePasswordRequest,
  LoginRequest,
  LogoutRequest,
  RefreshTokenRequest,
  RegisterRequest,
  UpdateProfileRequest,
} from "@/types/requests";

/**
 * Platform auth endpoints. Login/register/refresh/logout are public (auth is
 * established by their response); /me and the profile endpoints require the
 * platform session.
 */
export const authApi = {
  register: (body: RegisterRequest) => post<AuthResponse>(endpoints.platformAuth.register, body),
  login: (body: LoginRequest) => post<AuthResponse>(endpoints.platformAuth.login, body),
  refresh: (body: RefreshTokenRequest) => post<AuthResponse>(endpoints.platformAuth.refresh, body),
  logout: (body: LogoutRequest) => post<void>(endpoints.platformAuth.logout, body),
  me: () => get<PlatformUserResponse>(endpoints.platformAuth.me, "platform"),
  updateProfile: (body: UpdateProfileRequest) =>
    patch<PlatformUserResponse>(endpoints.platformAuth.me, body, "platform"),
  changePassword: (body: ChangePasswordRequest) =>
    post<void>(endpoints.platformAuth.changePassword, body, "platform"),
};
