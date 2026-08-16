import type { NextConfig } from "next";

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // Minimal self-contained production build (.next/standalone + server.js) so
  // the Docker image needs no node_modules. Start with PORT/HOSTNAME env vars.
  output: "standalone",
  // Proxy API calls to the Nexxauth backend. Keeping them same-origin means
  // the backend needs no CORS configuration and the browser never talks to a
  // second host. Point BACKEND_URL at the real backend for production.
  async rewrites() {
    return [
      {
        source: "/api/v1/:path*",
        destination: `${BACKEND_URL}/api/v1/:path*`,
      },
    ];
  },
};

export default nextConfig;
