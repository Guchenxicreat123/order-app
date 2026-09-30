'use strict';
/**
 * 独立后台管理服务（order-admin-api）
 *
 * 设计要点：
 *  1. 登录只校验 t_admin_user 的账号密码 —— 没有 is_chef / 角色 / 家庭 / APP_ADMIN_OPENID 等任何限制。
 *  2. 所有数据接口都直接读写 MySQL（本服务自己持连接池），不再转发给小程序后端。
 *  3. 路径与旧后台保持一致，前端只需把 baseURL 指到本服务。
 */
const express = require('express');
const {
  ensureAdminTable, login, changePassword, requireAdmin,
  ok, fail, failFrom, DEFAULT_USER, DEFAULT_PASS,
} = require('./auth');
const { pool } = require('./db');

const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '2mb' }));

// ---- CORS：后台可能被不同来源打开（LAN IP / 域名 / 代理），这里放开 ----
app.use((req, res, next) => {
  res.setHeader('Access-Control-Allow-Origin', req.headers.origin || '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,PUT,DELETE,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type,Authorization,X-Family-Id');
  res.setHeader('Access-Control-Max-Age', '3600');
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});

const health = async (req, res) => {
  try {
    await pool.query('SELECT 1');
    res.json({ code: 0, message: 'ok', data: { db: true } });
  } catch (e) {
    res.status(500).json({ code: 500, message: 'db down', data: { db: false } });
  }
};
app.get('/health', health);
app.get('/api/health', health); // 前端同源反代下用这个地址探活

// ==================== 认证（唯一的门槛就是账号密码） ====================
async function doLogin(req, res) {
  try {
    const { username, password } = req.body || {};
    const out = await login(username, password);
    return res.json({ code: 0, message: 'ok', data: out });
  } catch (e) {
    return fail(res, e.status || 500, e.message || '登录失败');
  }
}
app.post('/api/admin/login', doLogin);
app.post('/api/chef/login', doLogin); // 兼容旧前端：老版本把登录路径写成 /chef/login
app.post('/api/admin/logout', (req, res) => ok(res, true));

app.get('/api/admin/me', requireAdmin, (req, res) => ok(res, req.admin));

app.post('/api/admin/change-password', requireAdmin, async (req, res) => {
  try {
    const { oldPassword, newPassword } = req.body || {};
    await changePassword(req.admin.username, oldPassword, newPassword);
    return ok(res, true);
  } catch (e) {
    return fail(res, e.status || 500, e.message || '修改失败');
  }
});

// ==================== 业务接口（全部放行，直连数据库） ====================
app.use(requireAdmin);
app.use('/api', require('./routes/orders'));
app.use('/api', require('./routes/dishes'));
app.use('/api', require('./routes/library'));   // 分类 / 配料 / 配料分类
app.use('/api', require('./routes/publicLib'));  // 公共菜品库 / 公共配料库
app.use('/api', require('./routes/families'));   // 家庭
app.use('/api', require('./routes/users'));      // 用户
app.use('/api', require('./routes/meta'));       // 当前账号信息 / 兼容旧接口
app.use('/api', require('./routes/pushLogs'));   // 推送记录（管理后台视图）

// 统一兜底：未匹配的接口
app.use((req, res) => fail(res, 404, `接口不存在：${req.method} ${req.path}`));

/**
 * 错误兜底：任何异常都回 JSON，避免前端拿到 HTML。
 * 数据库约束冲突（唯一键等）由 failFrom 翻译成人话。
 */
app.use((err, req, res, next) => {
  if (err && err.status && !err.code) {
    return fail(res, err.status, err.message);
  }
  console.error('[error]', err && err.stack ? err.stack : err);
  return failFrom(res, err);
});

const PORT = Number(process.env.PORT || 8008);

(async () => {
  try {
    await ensureAdminTable();
    app.listen(PORT, '0.0.0.0', () => {
      console.log(`[admin-api] listening on :${PORT}`);
      console.log(`[admin-api] 默认管理员：${DEFAULT_USER} / ${DEFAULT_PASS}（首次启动自动创建）`);
    });
  } catch (e) {
    console.error('[admin-api] 启动失败：', e);
    process.exit(1);
  }
})();
