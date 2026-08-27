import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import type { ReadableStreamDefaultController } from "node:stream/web";

// ---------------------------------------------------------------------------
// Mocks — must be declared before the import of the module under test
// ---------------------------------------------------------------------------

const mockUsePlatformSlug = vi.fn<() => string | undefined>();
vi.mock("@/hooks/queries", () => ({
  usePlatformSlug: () => mockUsePlatformSlug(),
}));

let mockedSession: {
  accessToken: string;
  refreshToken: string;
  user: { platform: { slug: string } };
} | null = {
  accessToken: "test-access-token-abc",
  refreshToken: "test-refresh-token",
  user: { platform: { slug: "cavgo" } },
};

vi.mock("@/store/store", () => ({
  useAppSelector: vi.fn((selector: (state: unknown) => unknown) =>
    selector({ auth: { platformSession: mockedSession } }),
  ),
  selectPlatformSession: (state: { auth: { platformSession: unknown } }) =>
    state.auth.platformSession,
}));

vi.mock("@/api/logs", () => ({
  logsApi: {
    stream: (slug: string, orgId?: number) =>
      `/api/v1/${slug}/logs/stream${orgId != null ? `?organisationId=${orgId}` : ""}`,
    list: vi.fn(),
  },
}));

// ---------------------------------------------------------------------------
// Fake SSE fetch — allows tests to push events into the stream
// ---------------------------------------------------------------------------

let fetchController: ReadableStreamDefaultController<Uint8Array> | null = null;
let lastFetchUrl: string | null = null;
let lastFetchInit: RequestInit | null = null;
let fakeFetchFn: ReturnType<typeof vi.fn> | null = null;

function createFakeFetch() {
  fakeFetchFn = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
    lastFetchUrl = typeof url === "string" ? url : url.toString();
    lastFetchInit = init ?? null;

    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        fetchController = controller;
      },
      cancel() {
        fetchController = null;
      },
    });

    return new Response(stream, {
      status: 200,
      headers: { "Content-Type": "text/event-stream" },
    });
  });
  return fakeFetchFn;
}

function pushEvent(eventType: string, data: string) {
  const encoder = new TextEncoder();
  const raw = `event: ${eventType}\ndata: ${data}\n\n`;
  fetchController?.enqueue(encoder.encode(raw));
}

function closeStream() {
  fetchController?.close();
  fetchController = null;
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("useLogStream", () => {
  let originalFetch: typeof globalThis.fetch;

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    originalFetch = globalThis.fetch;
    globalThis.fetch = createFakeFetch() as unknown as typeof globalThis.fetch;
    mockUsePlatformSlug.mockReturnValue("cavgo");
    mockedSession = {
      accessToken: "test-access-token-abc",
      refreshToken: "test-refresh-token",
      user: { platform: { slug: "cavgo" } },
    };
    lastFetchUrl = null;
    lastFetchInit = null;
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.useRealTimers();
    vi.restoreAllMocks();
    fetchController = null;
    lastFetchUrl = null;
    lastFetchInit = null;
    fakeFetchFn = null;
  });

  it("sends the Authorization header with the session access token", async () => {
    const { useLogStream } = await import("@/hooks/use-log-stream");

    renderHook(() => useLogStream());

    await waitFor(() => {
      expect(fakeFetchFn).toHaveBeenCalledOnce();
    });

    expect(lastFetchUrl).toBe("/api/v1/cavgo/logs/stream");
    expect(lastFetchInit?.headers).toEqual(
      expect.objectContaining({ Authorization: "Bearer test-access-token-abc" }),
    );

    closeStream();
  });

  it("parses log events and adds them to state", async () => {
    const { useLogStream } = await import("@/hooks/use-log-stream");

    const { result } = renderHook(() => useLogStream());

    await waitFor(() => {
      expect(fakeFetchFn).toHaveBeenCalledOnce();
    });

    expect(result.current.logs).toEqual([]);

    act(() => {
      pushEvent(
        "log",
        JSON.stringify({
          id: 1,
          level: "INFO",
          message: "User logged in",
          timestamp: "2026-08-27T10:00:00Z",
        }),
      );
    });

    await waitFor(() => {
      expect(result.current.logs).toHaveLength(1);
    });

    expect(result.current.logs[0]).toEqual(
      expect.objectContaining({ id: 1, level: "INFO", message: "User logged in" }),
    );

    act(() => {
      pushEvent(
        "log",
        JSON.stringify({
          id: 2,
          level: "ERROR",
          message: "Something failed",
          timestamp: "2026-08-27T10:00:01Z",
        }),
      );
    });

    await waitFor(() => {
      expect(result.current.logs).toHaveLength(2);
    });

    // Most recent first
    expect(result.current.logs[0]).toEqual(
      expect.objectContaining({ id: 2 }),
    );

    closeStream();
  });

  it("ignores malformed event data without crashing", async () => {
    const { useLogStream } = await import("@/hooks/use-log-stream");

    const { result } = renderHook(() => useLogStream());

    await waitFor(() => {
      expect(fakeFetchFn).toHaveBeenCalledOnce();
    });

    act(() => {
      pushEvent("log", "NOT_JSON");
    });

    await waitFor(() => {
      expect(result.current.logs).toHaveLength(0);
    });

    closeStream();
  });

  it("ignores non-log event types", async () => {
    const { useLogStream } = await import("@/hooks/use-log-stream");

    const { result } = renderHook(() => useLogStream());

    await waitFor(() => {
      expect(fakeFetchFn).toHaveBeenCalledOnce();
    });

    act(() => {
      pushEvent("heartbeat", "ok");
    });

    await waitFor(() => {
      expect(result.current.logs).toHaveLength(0);
    });

    closeStream();
  });

  it("caps stored logs at 200", async () => {
    const { useLogStream } = await import("@/hooks/use-log-stream");

    const { result } = renderHook(() => useLogStream());

    await waitFor(() => {
      expect(fakeFetchFn).toHaveBeenCalledOnce();
    });

    act(() => {
      for (let i = 0; i < 210; i++) {
        pushEvent(
          "log",
          JSON.stringify({ id: i, level: "INFO", message: `msg-${i}` }),
        );
      }
    });

    await waitFor(() => {
      expect(result.current.logs).toHaveLength(200);
    });

    // First item should be the most recent (id 209)
    expect(result.current.logs[0]).toEqual(
      expect.objectContaining({ id: 209 }),
    );

    closeStream();
  });

  it("appends organisationId query param when provided", async () => {
    const { useLogStream } = await import("@/hooks/use-log-stream");

    renderHook(() => useLogStream(42));

    await waitFor(() => {
      expect(fakeFetchFn).toHaveBeenCalledOnce();
    });

    expect(lastFetchUrl).toBe("/api/v1/cavgo/logs/stream?organisationId=42");

    closeStream();
  });

  it("reconnects after stream closes", async () => {
    const { useLogStream } = await import("@/hooks/use-log-stream");

    const { result } = renderHook(() => useLogStream());

    await waitFor(() => {
      expect(fakeFetchFn).toHaveBeenCalledOnce();
    });

    expect(result.current.connected).toBe(true);

    // Close the stream — this triggers the reconnect setTimeout inside the
    // fetch .then() handler. We need to flush the microtask first so the
    // setTimeout is registered, then advance the timer.
    act(() => {
      closeStream();
    });

    // Flush microtasks so the async fetch handler runs and registers the
    // setTimeout(…, 3000).
    await act(async () => {});

    // Now advance past the 3s reconnect delay.
    await act(async () => {
      vi.advanceTimersByTime(3100);
    });

    await waitFor(() => {
      expect(fakeFetchFn).toHaveBeenCalledTimes(2);
    });

    closeStream();
  });

  it("reconnects after a non-ok response", async () => {
    globalThis.fetch = vi.fn(async () => new Response(null, { status: 401 }));

    const { useLogStream } = await import("@/hooks/use-log-stream");

    const { result } = renderHook(() => useLogStream());

    await waitFor(() => {
      expect(result.current.connected).toBe(false);
    });

    await act(async () => {
      vi.advanceTimersByTime(3100);
    });

    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalledTimes(2);
    });
  });

  it("does not reconnect after intentional unmount", async () => {
    const { useLogStream } = await import("@/hooks/use-log-stream");

    const { unmount } = renderHook(() => useLogStream());

    await waitFor(() => {
      expect(fakeFetchFn).toHaveBeenCalledOnce();
    });

    unmount();

    await act(async () => {
      vi.advanceTimersByTime(5000);
    });

    expect(fakeFetchFn).toHaveBeenCalledOnce();
  });

  it("sets connected to true when stream opens", async () => {
    const { useLogStream } = await import("@/hooks/use-log-stream");

    const { result } = renderHook(() => useLogStream());

    expect(result.current.connected).toBe(false);

    await waitFor(() => {
      expect(result.current.connected).toBe(true);
    });

    closeStream();
  });

  it("does not connect when session is missing", async () => {
    mockedSession = null;

    const { useLogStream } = await import("@/hooks/use-log-stream");

    renderHook(() => useLogStream());

    await act(async () => {
      vi.advanceTimersByTime(1000);
    });

    expect(fakeFetchFn).not.toHaveBeenCalled();
  });
});
