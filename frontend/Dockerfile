# syntax=docker/dockerfile:1

# 前端镜像：pnpm 构建静态资源，Nginx 托管并代理后端 API / WebSocket
FROM node:20-bookworm-slim AS build

WORKDIR /app

# 仓库里 .npmrc 已配置 npmmirror，安装更快
ENV SKIP_SIMPLE_GIT_HOOKS=1 \
    NODE_OPTIONS=--max-old-space-size=4096
RUN npm install -g pnpm@9

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml .npmrc ./
COPY packages ./packages
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

FROM nginx:1.27-alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
