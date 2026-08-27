"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { logsApi } from "@/api/logs";
import { usePlatformSlug } from "@/hooks/queries";
import { selectPlatformSession, useAppSelector } from "@/store/store";
import type { LogEntryResponse } from "@/types/api";

/**
 * Subscribes to the server-sent event stream for new log entries.
 * Returns the latest log entries in real time.
 */
export function useLogStream(organisationId?: number) {
  const platformSlug = usePlatformSlug() ?? "";
  const session = useAppSelector(selectPlatformSession);
  const [logs, setLogs] = useState<LogEntryResponse[]>([]);
  const [connected, setConnected] = useState(false);
  const eventSourceRef = useRef<EventSource | null>(null);

  const connect = useCallback(() => {
    if (!platformSlug || !session) return;

    const streamUrl = logsApi.stream(platformSlug, organisationId);
    // EventSource doesn't support custom headers, so we encode the token
    // in a query param that the backend ignores (auth is via cookie or
    // the existing JWT filter). For SSE the browser sends cookies
    // automatically, which is enough for the platform session.
    const es = new EventSource(streamUrl, { withCredentials: true });

    es.onopen = () => setConnected(true);

    es.addEventListener("log", (event) => {
      try {
        const entry = JSON.parse(event.data) as LogEntryResponse;
        setLogs((prev) => [entry, ...prev].slice(0, 200)); // keep last 200
      } catch {
        // malformed event — ignore
      }
    });

    es.onerror = () => {
      setConnected(false);
      es.close();
      // Reconnect after a short delay
      setTimeout(() => {
        if (eventSourceRef.current === es) {
          connect();
        }
      }, 3000);
    };

    eventSourceRef.current = es;
  }, [platformSlug, session, organisationId]);

  useEffect(() => {
    connect();
    return () => {
      eventSourceRef.current?.close();
      eventSourceRef.current = null;
      setConnected(false);
    };
  }, [connect]);

  const clearLogs = useCallback(() => setLogs([]), []);

  return { logs, connected, clearLogs };
}
