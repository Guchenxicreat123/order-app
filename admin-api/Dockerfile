# 独立后台管理服务
# 只做两件事：校验管理员账号密码、直接读写 MySQL。
FROM node:20-alpine

WORKDIR /app

# 先装依赖（利用缓存）
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund

COPY src ./src

ENV NODE_ENV=production
ENV PORT=8008

EXPOSE 8008

CMD ["node", "src/server.js"]
