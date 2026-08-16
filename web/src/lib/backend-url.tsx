"use client";

import { createContext, useContext } from "react";

/**
 * Backend public origin (e.g. https://auth.example.com) surfaced to the console
 * dashboards so the copyable platform/project URL can be derived client-side.
 * Provided by the console layout from BACKEND_PUBLIC_URL at runtime.
 */
const BackendOriginContext = createContext<string>("");

export function BackendOriginProvider({ value, children }: { value: string; children: React.ReactNode }) {
  return <BackendOriginContext.Provider value={value}>{children}</BackendOriginContext.Provider>;
}

/** Backend public origin configured for the web app ("" when unset). */
export function useBackendOrigin(): string {
  return useContext(BackendOriginContext);
}