# ─────────────────────────────────────────────────────────────────────────────
# ZyntaPOS Marketing Website — Multi-stage Docker build
#
# Stage 1: Node 22 builds the Astro static site
# Stage 2: nginx:alpine serves the compiled dist/
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM node:22-alpine AS builder

WORKDIR /app

# Install dependencies first (layer cache)
COPY package.json package-lock.json ./
RUN npm ci --frozen-lockfile

# Copy source and build
COPY . .
RUN npx astro build

# ── Stage 2: Serve ────────────────────────────────────────────────────────────
FROM nginx:1-alpine

# Copy compiled static site
COPY --from=builder /app/dist /usr/share/nginx/html

# Custom nginx config (proper caching + routing for Astro static output)
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:80/ || exit 1
