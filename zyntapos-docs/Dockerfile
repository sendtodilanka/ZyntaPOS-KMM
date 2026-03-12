## ZyntaPOS API Documentation — Multi-stage build
## Stage 1: Build static site
FROM node:22-alpine AS builder
WORKDIR /app
COPY package.json ./
RUN npm install --production
COPY . .
RUN node build.js

## Stage 2: Serve with nginx
FROM nginx:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3001
CMD ["nginx", "-g", "daemon off;"]
