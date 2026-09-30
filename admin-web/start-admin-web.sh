#!/bin/bash
# admin-web 静态服务器启动脚本
# 使用方法: bash start-admin-web.sh

DIST_DIR="$(dirname "$0")/dist"
PORT=8007

if [ ! -d "$DIST_DIR" ]; then
  echo "[ERROR] dist 目录不存在: $DIST_DIR"
  exit 1
fi

# 检查端口是否被占用
if ss -tlnp | grep -q ":$PORT "; then
  echo "[WARN] 端口 $PORT 已被占用，先关闭旧进程..."
  fuser -k $PORT/tcp 2>/dev/null
  sleep 1
fi

echo "[INFO] 启动 admin-web 静态服务器..."
echo "[INFO] 访问地址: http://192.168.x.x:$PORT"
echo "[INFO] 按 Ctrl+C 停止"

# 启动 node 服务器（history-api-fallback）
node - << EOF &
const http = require('http');
const fs = require('fs');
const path = require('path');
const DIST = '$DIST_DIR';
const MIME = {
  '.html':'text/html','.js':'application/javascript',
  '.css':'text/css','.json':'application/json',
  '.png':'image/png','.jpg':'image/jpeg','.svg':'image/svg+xml',
  '.ico':'image/x-icon','.woff2':'font/woff2','.woff':'font/woff'
};
const server = http.createServer((req, res) => {
  let url = req.url.split('?')[0];
  let fp = path.join(DIST, url);
  if (url !== '/' && fs.existsSync(fp) && fs.statSync(fp).isFile()) {
    const ext = path.extname(fp);
    res.writeHead(200, {'Content-Type': MIME[ext]||'application/octet-stream','Cache-Control':'no-cache'});
    fs.createReadStream(fp).pipe(res); return;
  }
  res.writeHead(200, {'Content-Type':'text/html','Cache-Control':'no-cache'});
  fs.createReadStream(path.join(DIST,'index.html')).pipe(res);
});
server.listen($PORT, '0.0.0.0', () => console.log('admin-web serving http://0.0.0.0:$PORT'));
EOF

sleep 1
# 验证启动成功
if curl -s -o /dev/null -w "%{http_code}" http://localhost:$PORT/ | grep -q 200; then
  echo "[OK] 服务已就绪"
else
  echo "[ERROR] 服务启动失败"
  exit 1
fi

wait
