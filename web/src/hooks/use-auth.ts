"use client";

import { useMutation } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { authApi } from "@/api/auth";
import { clearPlatformSession, setPlatformSession, type PlatformSession } from "@/store/authSlice";
import { useAppDispatch, useAppSelector, selectPlatformSession } from "@/store/store";
import { getErrorMessage } from "@/types/errors";
import type { LoginRequest, RegisterRequest } from "@/types/requests";

// ---------------------------------------------------------------------------
// Platform auth (console). The org portal authenticates server-side instead —
// see app/org/.../actions.ts.
// ---------------------------------------------------------------------------

export function usePlatformLogin() {
  const dispatch = useAppDispatch();
  const router = useRouter();
  return useMutation({
    mutationFn: (body: LoginRequest) => authApi.login(body),
    onSuccess: (data) => {
      const session: PlatformSession = { ...data, loginAt: Date.now() };
      dispatch(setPlatformSession(session));
      toast.success(`Welcome back, ${data.user.firstName}`);
      router.replace("/console");
    },
    // Errors are shown inline by the login form — no duplicate toast.
  });
}

export function usePlatformRegister() {
  const dispatch = useAppDispatch();
  const router = useRouter();
  return useMutation({
    mutationFn: (body: RegisterRequest) => authApi.register(body),
    onSuccess: (data) => {
      const session: PlatformSession = { ...data, loginAt: Date.now() };
      dispatch(setPlatformSession(session));
      toast.success(`Platform "${data.user.platform.name}" created`);
      // A fresh platform starts with the onboarding wizard, which sets up the
      // first organisation (identifiers, fields, auth, sessions, client).
      router.replace(`/console/onboarding/${data.user.platform.slug}`);
    },
    // Errors are shown inline by the register form — no duplicate toast.
  });
}

export function usePlatformLogout() {
  const dispatch = useAppDispatch();
  const router = useRouter();
  const session = useAppSelector(selectPlatformSession);
  return useMutation({
    mutationFn: () => {
      const refreshToken = session?.refreshToken;
      return refreshToken
        ? authApi.logout({ refreshToken }).catch(() => undefined)
        : Promise.resolve();
    },
    onSuccess: () => {
      dispatch(clearPlatformSession());
      toast.success("Signed out");
      // The console auth guard also sends signed-out users to /login, so go
      // there directly instead of racing it to the landing page.
      router.replace("/login");
    },
    onError: (error) => toast.error(getErrorMessage(error)),
  });
}
