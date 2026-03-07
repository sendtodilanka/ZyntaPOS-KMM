# Stage 1: Build
FROM node:22-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --frozen-lockfile

COPY . .

ARG VITE_API_URL=https://api.zyntapos.com
ARG VITE_LICENSE_URL=https://license.zyntapos.com
ARG VITE_SYNC_URL=https://sync.zyntapos.com

ENV VITE_API_URL=$VITE_API_URL
ENV VITE_LICENSE_URL=$VITE_LICENSE_URL
ENV VITE_SYNC_URL=$VITE_SYNC_URL

RUN npm run build

# Stage 2: Serve
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3000
CMD ["nginx", "-g", "daemon off;"]
