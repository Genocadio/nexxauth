import { get, patch, post } from "@/lib/api-client";
import { endpoints } from "@/lib/endpoints";
import type { PlatformResponse, PlatformUserResponse } from "@/types/api";
import type { AddPlatformUserRequest, UpdateUserRequest } from "@/types/requests";

export const platformApi = {
  get: (slug: string) => get<PlatformResponse>(endpoints.platform(slug).get, "platform"),
  users: (slug: string) => get<PlatformUserResponse[]>(endpoints.platform(slug).users, "platform"),
  addUser: (slug: string, body: AddPlatformUserRequest) =>
    post<PlatformUserResponse>(endpoints.platform(slug).users, body, "platform"),
  updateUser: (userId: number, body: UpdateUserRequest) =>
    patch<PlatformUserResponse>(endpoints.user(userId), body, "platform"),
};
