# syntax=docker/dockerfile:1

# --- Build stage 1: install dependencies with Bun ----------------------------
# The oven/bun image is used only for `bun install` (fast, lockfile-accurate).
# `next build` and the standalone server run under real Node instead — Turbopack
# segfaults under Bun's runtime, so Node is the runtime for this app.
FROM oven/bun:1.3.14 AS deps
WORKDIR /app
COPY package.json bun.lock* ./
RUN bun install --frozen-lockfile

# --- Build stage 2: build the Next.js app with Node --------------------------
FROM node:22-bookworm-slim AS build
WORKDIR /app

# BACKEND_URL is read at build time by next.config.ts to wire the /api/v1
# proxy rewrite. In compose it points at the backend service.
ARG BACKEND_URL=http://localhost:8080
ENV BACKEND_URL=$BACKEND_URL
ENV NEXT_TELEMETRY_DISABLED=1

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Build (type-checks + lints + emits the standalone output).
RUN node node_modules/next/dist/bin/next build

# --- Runtime stage: self-contained standalone server under Node --------------
FROM node:22-bookworm-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

# Non-root user + curl for the compose healthcheck.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system nauth && useradd --system --gid nauth --no-create-home nauth

# Copy the standalone output (traced deps + server.js) and static assets.
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

EXPOSE 3000
USER nauth

# Health probe for orchestrators; compose's depends_on: service_healthy uses
# this image healthcheck too.
HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=12 \
    CMD curl -fsS http://localhost:3000/ || exit 1

# PORT/HOSTNAME are honored by the standalone server. API_SERVER_URL (org
# portal server-side calls) is injected at runtime by compose.
CMD ["node", "server.js"]
