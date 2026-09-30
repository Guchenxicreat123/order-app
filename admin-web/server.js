'use strict';
/**
 * 后台管理端静态站点 + API 反向代理（零依赖，只用 node 内置模块）。
 *
 * 为什么需要它：
 *   1. 后台前端原来是写死 http://192.168.x.x:8006/api（内网 IP + 明文 http），
 *      换设备/换网段/https 就会连不上。现在前端统一请求同源 /api。
 *   2. /api/* 由本进程转发到独立的管理服务 order-admin-api:8008，
 *      既没有跨域问题，也不用再把管理接口暴露到公网端口。
 */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const PORT = Number(process.env.PORT || 8007);
const DIST = path.join(__dirname, 'dist');
const API_TARGET_HOST = process.env.ADMIN_API_HOST || 'admin-api';
const API_TARGET_PORT = Number(process.env.ADMIN_API_PORT || 8008);

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
};

const CSP = [
  "default-src 'self'",
  "script-src 'self' 'unsafe-inline'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  "connect-src 'self'",           // 接口走同源 /api，无需再放行外部地址
  "object-src 'none'",
  "base-uri 'none'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join('; ');

function securityHeaders(res) {
  res.setHeader('Content-Security-Policy', CSP);
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Permissions-Policy', 'geolocation=(), camera=(), microphone=(), payment=()');
}

/** 把 /api 前缀的请求转发给独立管理服务 */
function proxy(req, res) {
  const options = {
    host: API_TARGET_HOST,
    port: API_TARGET_PORT,
    method: req.method,
    path: req.url,
    headers: { ...req.headers, host: `${API_TARGET_HOST}:${API_TARGET_PORT}` },
  };
  const upstream = http.request(options, (up) => {
    res.writeHead(up.statusCode || 502, up.headers);
    up.pipe(res);
  });
  upstream.on('error', (e) => {
    console.error('[proxy] 转发失败：', e.message);
    if (!res.headersSent) {
      res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
    }
    res.end(JSON.stringify({ code: 502, message: `管理服务不可用：${e.message}`, data: null }));
  });
  req.pipe(upstream);
}

/** 静态文件 + SPA 回退（history 模式） */
function serveStatic(req, res, pathname) {
  const rel = decodeURIComponent(pathname).replace(/^\/+/, '');
  let file = path.join(DIST, rel);
  // 目录或根路径 → index.html
  if (!rel || rel.endsWith('/')) file = path.join(file, 'index.html');
  // 防目录穿越
  if (!file.startsWith(DIST)) {
    res.writeHead(403);
    return res.end('forbidden');
  }

  fs.stat(file, (err, st) => {
    if (err || !st.isFile()) {
      // SPA 回退：未知路径一律回 index.html，交给 vue-router
      const index = path.join(DIST, 'index.html');
      return fs.readFile(index, (e2, buf) => {
        if (e2) {
          res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
          return res.end('not found');
        }
        securityHeaders(res);
        res.writeHead(200, { 'Content-Type': MIME['.html'], 'Cache-Control': 'no-cache' });
        res.end(buf);
      });
    }
    const ext = path.extname(file).toLowerCase();
    securityHeaders(res);
    res.writeHead(200, {
      'Content-Type': MIME[ext] || 'application/octet-stream',
      // 带指纹的静态资源可长缓存，index.html 不缓存
      'Cache-Control': rel.startsWith('assets/') ? 'public, max-age=31536000, immutable' : 'no-cache',
    });
    fs.createReadStream(file).pipe(res);
  });
}

http
  .createServer((req, res) => {
    const pathname = (req.url || '/').split('?')[0];
    if (pathname === '/api' || pathname.startsWith('/api/')) return proxy(req, res);
    if (pathname === '/health') {
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
      return res.end(JSON.stringify({ code: 0, message: 'ok', data: { web: true } }));
    }
    serveStatic(req, res, pathname);
  })
  .listen(PORT, '0.0.0.0', () => {
    console.log(`[admin-web] http://0.0.0.0:${PORT}  (dist=${DIST}, /api → ${API_TARGET_HOST}:${API_TARGET_PORT})`);
  });
