FROM node:26-alpine@sha256:e88a35be04478413b7c71c455cd9865de9b9360e1f43456be5951032d7ac1a66 AS build
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.29-alpine@sha256:0c79d56aee561a1d81c63f00eee5fb5fe29279560cdc55e91425133104c7fbe6
USER root
RUN apk add --no-cache --upgrade \
    c-ares=1.34.8-r0 \
    ca-certificates-bundle=20260611-r0 \
    curl=8.20.0-r0 \
    libcrypto3=3.5.7-r0 \
    libcurl=8.20.0-r0 \
    libexpat=2.8.2-r0 \
    libssl3=3.5.7-r0 \
    libxml2=2.13.9-r1
USER 101
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
