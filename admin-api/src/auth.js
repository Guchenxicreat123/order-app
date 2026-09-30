'use strict';
/**
 * 后台管理员认证。
 *
 * 与小程序后端彻底解耦：只认 t_admin_user 这张表里的账号密码，
 * 不再看 t_user.is_chef、APP_ADMIN_OPENID、家庭角色等任何条件。
 */
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { q, one } = require('./db');

// 是否启用"检测默认密码就拒绝启动"的严格模式。
// 默认开启。生产环境强烈建议保留为 true，避免裸跑 admin/123456 被扫。
// 关闭：环境变量 ADMIN_ALLOW_DEFAULT_PASS=true
const ALLOW_DEFAULT_PASS = process.env.ADMIN_ALLOW_DEFAULT_PASS === 'true';

const JWT_SECRET = process.env.ADMIN_JWT_SECRET || 'order-admin-api-dev-secret-change-me';
const TOKEN_TTL = process.env.ADMIN_TOKEN_TTL || '12h';
const DEFAULT_USER = process.env.ADMIN_DEFAULT_USERNAME || 'admin';
const DEFAULT_PASS = process.env.ADMIN_DEFAULT_PASSWORD || '123456';

const DDL = `
CREATE TABLE IF NOT EXISTS t_admin_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name  VARCHAR(64)  DEFAULT NULL,
  remark        VARCHAR(255) DEFAULT NULL,
  token_epoch   INT          NOT NULL DEFAULT 0,
  last_login_at DATETIME     DEFAULT NULL,
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台管理员账号（独立于小程序用户）';
`;

/** 建表；若一个管理员都没有，则用默认账号密码初始化一个 */
async function ensureAdminTable() {
  await q(DDL);
  // 老表补列（幂等）：token_epoch 用于"改密后旧令牌立即失效"
  try {
    await q('ALTER TABLE t_admin_user ADD COLUMN token_epoch INT NOT NULL DEFAULT 0');
  } catch (e) {
    if (e && e.code !== 'ER_DUP_FIELDNAME') throw e;
  }
  const n = await one('SELECT COUNT(*) AS c FROM t_admin_user');
  if (!n || Number(n.c) === 0) {
    // ============ 安全检查：拒绝使用默认密码启动 ============
    if (DEFAULT_PASS === '123456') {
      if (ALLOW_DEFAULT_PASS) {
        console.warn('[auth] ⚠️  检测到 ADMIN_DEFAULT_PASSWORD=123456（默认值）');
        console.warn('[auth] ⚠️  ADMIN_ALLOW_DEFAULT_PASS=true，已放行；生产环境强烈建议修改');
      } else {
        console.error('========================================================');
        console.error('[auth] ❌ 检测到 ADMIN_DEFAULT_PASSWORD=123456（默认值）');
        console.error('[auth] ❌ 拒绝以默认密码启动后台服务（生产环境裸跑风险）');
        console.error('[auth] ❌ 修法：在 .env 设置 ADMIN_DEFAULT_PASSWORD=<至少 8 位的强密码>');
        console.error('[auth] ❌ 临时放行：ADMIN_ALLOW_DEFAULT_PASS=true');
        console.error('========================================================');
        throw new Error('refused to start with default admin password');
      }
    }
    const hash = bcrypt.hashSync(DEFAULT_PASS, 10);
    await q(
      'INSERT INTO t_admin_user (username, password_hash, display_name) VALUES (?, ?, ?)',
      [DEFAULT_USER, hash, '超级管理员']
    );
    console.log(`[auth] 已初始化默认管理员：${DEFAULT_USER} / ${'*'.repeat(DEFAULT_PASS.length)}（已脱敏）`);
  } else {
    // 已存在管理员时，也检测密码是不是默认 123456（覆盖升级场景）
    if (DEFAULT_PASS === '123456' && !ALLOW_DEFAULT_PASS) {
      console.warn('[auth] ⚠️  ADMIN_DEFAULT_PASSWORD=123456 仅用于首次启动；如数据库已有管理员则不影响');
    }
  }
}

function sign(user) {
  return jwt.sign(
    {
      sub: user.username,
      typ: 'admin',
      name: user.display_name || user.username,
      ep: user.token_epoch == null ? 0 : Number(user.token_epoch),
    },
    JWT_SECRET,
    { expiresIn: TOKEN_TTL }
  );
}

/**
 * 账号密码登录。唯一的校验就是这个。
 * 返回 { token, username, displayName }
 */
async function login(username, password) {
  const name = String(username || '').trim();
  if (!name) throw httpError(400, '请输入管理员账号');
  if (!password) throw httpError(400, '请输入管理员密码');

  const user = await one('SELECT * FROM t_admin_user WHERE username = ?', [name]);
  if (!user) throw httpError(401, '账号或密码错误');
  const ok = await bcrypt.compare(String(password), user.password_hash);
  if (!ok) throw httpError(401, '账号或密码错误');

  await q('UPDATE t_admin_user SET last_login_at = NOW() WHERE id = ?', [user.id]);
  return {
    token: sign(user),
    username: user.username,
    displayName: user.display_name || user.username,
  };
}

/** 自助修改密码 */
async function changePassword(username, oldPassword, newPassword) {
  const next = String(newPassword || '');
  if (next.length < 4) throw httpError(400, '新密码至少 4 位');
  if (next.length > 64) throw httpError(400, '新密码最多 64 位');
  const user = await one('SELECT * FROM t_admin_user WHERE username = ?', [username]);
  if (!user) throw httpError(401, '登录已失效，请重新登录');
  // 改密同时递增 token_epoch：所有已签发的旧令牌立即失效
  await q('UPDATE t_admin_user SET password_hash = ?, token_epoch = token_epoch + 1 WHERE id = ?', [
    bcrypt.hashSync(next, 10),
    user.id,
  ]);
  return true;
}

/** 中间件：校验 Bearer token（含 token_epoch，改密后旧令牌自动失效） */
async function requireAdmin(req, res, next) {
  const hdr = req.headers.authorization || '';
  const token = hdr.startsWith('Bearer ') ? hdr.slice(7).trim() : '';
  if (!token) return fail(res, 401, '请先登录');
  try {
    const payload = jwt.verify(token, JWT_SECRET);
    if (payload.typ !== 'admin') return fail(res, 401, '请先登录');
    const user = await one('SELECT username, display_name, token_epoch FROM t_admin_user WHERE username = ?', [
      payload.sub,
    ]);
    if (!user) return fail(res, 401, '登录已失效，请重新登录');
    const cur = user.token_epoch == null ? 0 : Number(user.token_epoch);
    if (Number(payload.ep == null ? 0 : payload.ep) !== cur) {
      return fail(res, 401, '登录已失效，请重新登录');
    }
    req.admin = { username: user.username, displayName: user.display_name || user.username };
    next();
  } catch (e) {
    return fail(res, 401, '登录已失效，请重新登录');
  }
}

function httpError(status, message) {
  const e = new Error(message);
  e.status = status;
  return e;
}

/**
 * 把数据库约束类错误翻译成人话。
 * 库里没有外键/CHECK/ENUM，唯一键冲突（比如同家庭同名菜）是唯一的常见约束错误；
 * 不翻译的话前端只会看到一句 "Duplicate entry '...' for key '...'"。
 */
const MYSQL_MESSAGES = {
  ER_DUP_ENTRY: '已存在同名记录，请换一个名称',
  ER_NO_REFERENCED_ROW_2: '关联的数据不存在',
  ER_ROW_IS_REFERENCED_2: '该记录仍被其它数据引用，无法删除',
  ER_BAD_NULL_ERROR: '有必填字段为空',
  ER_DATA_TOO_LONG: '有字段超出长度限制',
  ER_TRUNCATED_WRONG_VALUE: '有字段格式不正确',
  ER_WARN_DATA_OUT_OF_RANGE: '有数值超出允许范围',
  WARN_DATA_TRUNCATED: '有字段格式不正确',
};

/** 路由里 catch 到异常时统一走这里，保证错误提示可读且 HTTP 状态码正确 */
function failFrom(res, e, fallbackMessage) {
  const mapped = e && e.code && MYSQL_MESSAGES[e.code];
  if (mapped) {
    console.warn('[db]', e.code, e.sqlMessage || e.message);
    return fail(res, 400, mapped);
  }
  return fail(res, (e && e.status) || 500, (e && e.message) || fallbackMessage || '服务器异常');
}

function ok(res, data) {
  return res.json({ code: 0, message: 'ok', data: data === undefined ? null : data });
}

function fail(res, status, message) {
  return res.status(status).json({ code: status, message, data: null });
}

module.exports = {
  ensureAdminTable,
  login,
  changePassword,
  requireAdmin,
  httpError,
  ok,
  fail,
  failFrom,
  MYSQL_MESSAGES,
  DEFAULT_USER,
  DEFAULT_PASS,
};
