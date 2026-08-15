import type { NextConfig } from "next";

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
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
