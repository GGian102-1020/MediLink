FROM node:24-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM caddy:2.10-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=builder /app/dist /usr/share/caddy
EXPOSE 80
