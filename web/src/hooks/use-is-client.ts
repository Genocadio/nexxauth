"use client";

import { useSyncExternalStore } from "react";

const emptySubscribe = () => () => {};

/**
 * True only after the client has hydrated. Both the server and the first
 * client render agree on `false`, so it never causes hydration mismatches.
 * Replaces the classic `useEffect(() => setMounted(true), [])` pattern.
 */
export function useIsClient(): boolean {
  return useSyncExternalStore(
    emptySubscribe,
    () => true,
    () => false,
  );
}
