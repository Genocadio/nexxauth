import { configureStore, type Middleware } from "@reduxjs/toolkit";
import { useDispatch, useSelector, type TypedUseSelectorHook } from "react-redux";
import authReducer, { type PlatformSession } from "@/store/authSlice";
import { STORAGE_KEYS } from "@/lib/constants";

// ---------------------------------------------------------------------------
// Persistence — the platform session survives reloads. Rehydration happens on
// the client only (SSR never sees localStorage). The org portal session is
// server-side (httpOnly cookies), so it is not persisted here.
// ---------------------------------------------------------------------------

function readStored<T>(key: string): T | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

function writeStored(key: string, value: unknown): void {
  if (typeof window === "undefined") return;
  try {
    if (value === null) window.localStorage.removeItem(key);
    else window.localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // storage full / private mode — the session just won't survive a reload
  }
}

const persistMiddleware: Middleware = (store) => (next) => (action) => {
  const result = next(action);
  const state = store.getState().auth;
  writeStored(STORAGE_KEYS.platformSession, state.platformSession);
  return result;
};

export const store = configureStore({
  reducer: { auth: authReducer },
  middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(persistMiddleware),
  preloadedState: {
    auth: {
      platformSession: readStored<PlatformSession>(STORAGE_KEYS.platformSession),
    },
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

export const useAppDispatch = () => useDispatch<AppDispatch>();
export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector;

/** Convenience selector for the platform session. */
export const selectPlatformSession = (state: RootState) => state.auth.platformSession;
