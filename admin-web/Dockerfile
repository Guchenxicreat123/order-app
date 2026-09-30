FROM node:20-alpine
WORKDIR /app

# 静态站点由本容器的 server.js 提供，并把 /api 反代到独立管理服务 order-admin-api。
# 不再使用 npx serve：它无法反代，导致前端只能写死内网 IP 直连小程序后端。
COPY server.js ./
COPY dist ./dist

ENV PORT=8007
ENV ADMIN_API_HOST=admin-api
ENV ADMIN_API_PORT=8008

EXPOSE 8007

CMD ["node", "server.js"]
