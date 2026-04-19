# syntax=docker/dockerfile:1.6
# =====================================================================
# Webizon frontend — Nuxt 4 (Nitro) multi-stage image
# ---------------------------------------------------------------------
#  Stage 1 (deps)    : install prod+dev deps with cache mount
#  Stage 2 (build)   : `nuxt build` -> .output
#  Stage 3 (runtime) : node:20-alpine, non-root, runs .output/server
# =====================================================================

# ---------- deps stage ----------
FROM node:20-alpine AS deps

WORKDIR /app

COPY package.json package-lock.json* ./

RUN --mount=type=cache,target=/root/.npm \
    npm install --no-audit --no-fund

# ---------- build stage ----------
FROM node:20-alpine AS build

WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

ENV NODE_ENV=production
RUN npm run build

# ---------- runtime stage ----------
FROM node:20-alpine AS runtime

RUN addgroup -g 10001 -S webizon \
 && adduser  -u 10001 -S webizon -G webizon

WORKDIR /app

COPY --from=build --chown=webizon:webizon /app/.output ./.output

USER 10001:10001

ENV NODE_ENV=production \
    NITRO_HOST=0.0.0.0 \
    NITRO_PORT=3000 \
    HOST=0.0.0.0 \
    PORT=3000

EXPOSE 3000

HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=4 \
    CMD wget -qO- http://127.0.0.1:3000/ || exit 1

CMD ["node", ".output/server/index.mjs"]
