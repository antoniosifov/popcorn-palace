# syntax=docker/dockerfile:1

# --- Dependencies: installed from the lock file, cached until it changes ---
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

# --- Build: compile the application into a self-contained server ---
FROM node:22-alpine AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# No API is needed here: pages are prerendered as shells and filled at request time.
RUN npm run build

# --- Runtime: the standalone server and its static files, non-root user ---
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /app/.next/standalone ./
COPY --from=build --chown=app:app /app/.next/static ./.next/static
USER app

EXPOSE 3000
CMD ["node", "server.js"]
