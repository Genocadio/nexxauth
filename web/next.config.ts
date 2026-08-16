import type { NextConfig } from "next";

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // Minimal self-contained production build (.next/standalone + server.js) so
  // the Docker image needs no node_modules. Start with PORT/HOSTNAME env vars.
  output: "standalone",
  // Proxy API calls to the Nexxauth backend. Keeping them same-origin means
  // the backend needs no CORS configuration and the browser never talks to a
  // second host. Point BACKEND_URL at the real backend for production.
  //
  // Platforms live at the backend's clean root origin (/{slug}/...), so the
  // frontend addresses them as /api/v1/{slug}/... and this proxy strips the
  // /api/v1 prefix for those routes. Platform-wide endpoints (/api/v1/auth/*,
  // /api/v1/users/*, /api/v1/slug-suggestions) still carry the prefix on the
  // backend, so they are forwarded verbatim first (rules are first-match-wins).
  async rewrites() {
    return [
      {
        source: "/api/v1/auth/:path*",
        destination: `${BACKEND_URL}/api/v1/auth/:path*`,
      },
      {
        source: "/api/v1/users/:path*",
        destination: `${BACKEND_URL}/api/v1/users/:path*`,
      },
      {
        source: "/api/v1/slug-suggestions",
        destination: `${BACKEND_URL}/api/v1/slug-suggestions`,
      },
      {
        source: "/api/v1/:path*",
        destination: `${BACKEND_URL}/:path*`,
      },
    ];
  },
};

export default nextConfig;
