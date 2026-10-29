FROM node:22-alpine AS frontend-build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM caddy:2.10-alpine
COPY gateway/Caddyfile /etc/caddy/Caddyfile
COPY --from=frontend-build /app/dist/ /srv/medlink/
EXPOSE 443
