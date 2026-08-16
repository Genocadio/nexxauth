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
  // The backend serves everything at the clean root origin: platforms at
  // /{slug}/... and the platform-wide console endpoints at /auth/*, /users/*,
  // /slug-suggestions (no /api/v1 anywhere). The frontend still addresses them
  // under the /api/v1 namespace, and this proxy strips it (rules are
  // first-match-wins).
  async rewrites() {
    return [
      {
        source: "/api/v1/auth/:path*",
        destination: `${BACKEND_URL}/auth/:path*`,
      },
      {
        source: "/api/v1/users/:path*",
        destination: `${BACKEND_URL}/users/:path*`,
      },
      {
        source: "/api/v1/slug-suggestions",
        destination: `${BACKEND_URL}/slug-suggestions`,
      },
      {
        source: "/api/v1/:path*",
        destination: `${BACKEND_URL}/:path*`,
      },
    ];
  },
};

export default nextConfig;
