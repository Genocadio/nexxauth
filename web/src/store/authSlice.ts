import { createSlice, type PayloadAction } from "@reduxjs/toolkit";
import type { AuthResponse, PlatformUserResponse } from "@/types/api";

/**
 * Platform auth session (the console). The org portal session is not stored
 * here — it lives in httpOnly cookies managed by the Next proxy, the session
 * route handler and the org server actions.
 */
export interface PlatformSession extends AuthResponse {
  /** Epoch ms of when the session was established. */
  loginAt: number;
}

interface AuthState {
  platformSession: PlatformSession | null;
}

const initialState: AuthState = {
  platformSession: null,
};

const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    setPlatformSession(state, action: PayloadAction<PlatformSession>) {
      state.platformSession = action.payload;
    },
    setPlatformUser(state, action: PayloadAction<PlatformUserResponse>) {
      if (state.platformSession) state.platformSession.user = action.payload;
    },
    clearPlatformSession(state) {
      state.platformSession = null;
    },
  },
});

export const { setPlatformSession, setPlatformUser, clearPlatformSession } = authSlice.actions;

export default authSlice.reducer;

/** The platform the current platform session belongs to, if any. */
export function platformSlugOf(session: PlatformSession | null): string | undefined {
  return session?.user.platform.slug;
}
