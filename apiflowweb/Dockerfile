# syntax=docker/dockerfile:1

# ---- Build stage: Angular production build ----
FROM node:24-alpine AS build
WORKDIR /app

# Optional extra CA certificates (TLS-inspecting proxies/antivirus), provided by docker-compose
# from docker/certs. Usually empty. Trusted in this stage only.
COPY --from=certs . /tmp/extra-certs/
RUN cat /tmp/extra-certs/*.crt > /tmp/extra-ca.pem 2>/dev/null || touch /tmp/extra-ca.pem
ENV NODE_EXTRA_CA_CERTS=/tmp/extra-ca.pem

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npx ng build --configuration production

# ---- Runtime stage: Nginx serves the static bundle and proxies /api to the backend ----
FROM nginx:1.29-alpine

# The official image renders /etc/nginx/templates/*.template with envsubst at startup
# (only variables that are defined in the environment are substituted).
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/apiflowweb/browser /usr/share/nginx/html

ENV BACKEND_UPSTREAM=backend:8080
EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 \
  CMD wget -qO- http://127.0.0.1/ > /dev/null || exit 1
