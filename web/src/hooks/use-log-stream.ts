"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { logsApi } from "@/api/logs";
import { usePlatformSlug } from "@/hooks/queries";
import { selectPlatformSession, useAppSelector } from "@/store/store";
import type { LogEntryResponse } from "@/types/api";

/**
 * Subscribes to the server-sent event stream for new log entries.
 * Returns the latest log entries in real time.
 *
 * Uses `fetch` + `ReadableStream` instead of `EventSource` because
 * `EventSource` cannot send custom headers and the backend requires
 * `Authorization: Bearer <token>` for authentication.
 */
export function useLogStream(organisationId?: number) {
  const platformSlug = usePlatformSlug() ?? "";
  const session = useAppSelector(selectPlatformSession);
  const [logs, setLogs] = useState<LogEntryResponse[]>([]);
  const [connected, setConnected] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const connectRef = useRef<() => void>(() => {});

  const connect = useCallback(() => {
    if (!platformSlug || !session) return;

    const streamUrl = logsApi.stream(platformSlug, organisationId);
    const token = session.accessToken;

    const abortController = new AbortController();
    abortRef.current = abortController;

    const headers: Record<string, string> = {};
    if (token) headers["Authorization"] = `Bearer ${token}`;

    fetch(streamUrl, {
      headers,
      signal: abortController.signal,
    })
      .then(async (res) => {
        if (!res.ok) {
          setConnected(false);
          // Schedule reconnect on auth / server errors
          setTimeout(() => {
            if (abortRef.current === abortController) {
              connectRef.current();
            }
          }, 3000);
          return;
        }

        setConnected(true);

        const reader = res.body?.getReader();
        if (!reader) return;

        const decoder = new TextDecoder();
        let buffer = "";

        // eslint-disable-next-line no-constant-condition
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });

          // SSE events are separated by double newlines
          const events = buffer.split("\n\n");
          // Keep the last (potentially incomplete) chunk in the buffer
          buffer = events.pop() ?? "";

          for (const raw of events) {
            let eventType = "";
            let data = "";

            for (const line of raw.split("\n")) {
              if (line.startsWith("event:")) {
                eventType = line.slice(6).trim();
              } else if (line.startsWith("data:")) {
                data = line.slice(5).trim();
              }
            }

            if (eventType === "log" && data) {
              try {
                const entry = JSON.parse(data) as LogEntryResponse;
                setLogs((prev) => [entry, ...prev].slice(0, 200)); // keep last 200
              } catch {
                // malformed event — ignore
              }
            }
          }
        }

        // Stream ended — attempt reconnect
        setConnected(false);
        setTimeout(() => {
          if (abortRef.current === abortController) {
            connectRef.current();
          }
        }, 3000);
      })
      .catch(() => {
        // Network error / abort — attempt reconnect unless intentionally closed
        if (!abortController.signal.aborted) {
          setConnected(false);
          setTimeout(() => {
            if (abortRef.current === abortController) {
              connectRef.current();
            }
          }, 3000);
        }
      });
  }, [platformSlug, session, organisationId]);

  // Keep the ref fresh so the reconnect callback always calls the latest connect
  useEffect(() => {
    connectRef.current = connect;
  }, [connect]);

  useEffect(() => {
    connect();
    return () => {
      abortRef.current?.abort();
      abortRef.current = null;
      setConnected(false);
    };
  }, [connect]);

  const clearLogs = useCallback(() => setLogs([]), []);

  return { logs, connected, clearLogs };
}
