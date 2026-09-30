'use strict';
/**
 * 推送记录查询（管理后台）
 *
 * 数据源：t_push_log（每条推送一条），关联 t_user / t_family / t_menu_item 拼可读字段。
 * 不走小程序后端：这是管理员视图，不应该带「当前主厨」鉴权 — 直接 requireAdmin 即可。
 *
 * 接口：
 *   GET /api/push/stats       今日统计（总量 / 失败 / 各类型 / dry-run）
 *   GET /api/push/logs        列表（支持 familyId / type / success / limit / offset 过滤）
 *
 * 过滤参数约定：
 *   familyId  按家庭过滤（数字；缺省＝全部）
 *   type      'NEW_ORDER' | 'STATUS_CHANGED' | ''（缺省＝全部）
 *   success   1（成功）| 0（失败）| ''（全部，缺省）
 *   limit     默认 100，最大 500
 *   offset    默认 0
 */
const express = require('express');
const { ok, fail } = require('../auth');
const { q, one, scalar } = require('../db');

const router = express.Router();

const ALLOWED_TYPES = new Set(['NEW_ORDER', 'STATUS_CHANGED']);

/**
 * 把推送日志行整成后台展示的形状：用户昵称、家庭名、关联订单/菜品、模板字段都在一行里。
 * 注意：t_menu_item 可能在不同 family；这里只显示菜名 + familyId，不做冗余 join。
 *
 * 关键细节：stock mysql2 在 pool.execute(...) 里不会把「IN (?) + 数组」自动展开成「IN (?,?,?)」，
 * 所以这里手工生成占位符，否则 JOIN 永远返回空（用户昵称只能看到 openid 前 8 位）。
 */
async function enrichRows(rows) {
  if (!rows.length) return [];
  const userIds = [...new Set(rows.map(r => r.user_id).filter(Boolean))];
  const familyIds = [...new Set(rows.map(r => r.family_id).filter(Boolean))];
  const itemIds = [...new Set(rows.map(r => r.menu_item_id).filter(Boolean))];

  const users = userIds.length
    ? await q(
        `SELECT open_id, nickname FROM t_user WHERE open_id IN (${userIds.map(() => '?').join(',')})`,
        userIds
      )
    : [];
  const families = familyIds.length
    ? await q(
        `SELECT id, name FROM t_family WHERE id IN (${familyIds.map(() => '?').join(',')})`,
        familyIds
      )
    : [];
  const items = itemIds.length
    ? await q(
        `SELECT id, order_id, dish_name, status FROM t_menu_item WHERE id IN (${itemIds.map(() => '?').join(',')})`,
        itemIds
      )
    : [];
  const orderIds = [...new Set(items.map(i => i.order_id).filter(Boolean))];
  const orders = orderIds.length
    ? await q(
        `SELECT id, user_nickname, item_count, status FROM t_order WHERE id IN (${orderIds.map(() => '?').join(',')})`,
        orderIds
      )
    : [];

  const uById = new Map(users.map(u => [u.open_id, u.nickname || u.open_id.slice(0, 8)]));
  const fById = new Map(families.map(f => [String(f.id), f.name]));
  const oById = new Map(orders.map(o => [String(o.id), o]));
  const iById = new Map(items.map(i => [String(i.id), i]));

  return rows.map(r => {
    const item = r.menu_item_id != null ? iById.get(String(r.menu_item_id)) : null;
    const order = item && item.order_id != null ? oById.get(String(item.order_id)) : null;
    return {
      id: r.id,
      type: r.type,
      success: !!r.success,
      errorMsg: r.error_msg || '',
      createdAt: r.created_at,
      userOpenId: r.user_id,
      nickname: uById.get(r.user_id) || r.user_id.slice(0, 8),
      familyId: r.family_id,
      familyName: fById.get(String(r.family_id)) || `家庭#${r.family_id}`,
      menuItemId: r.menu_item_id,
      dishName: item ? item.dish_name : null,
      itemStatus: item ? item.status : null,
      orderId: order ? order.id : (item ? item.order_id : null),
      orderBuyerNickname: order ? order.user_nickname : null,
      payload: r.payload || '',
    };
  });
}

router.get('/push/logs', async (req, res) => {
  const { familyId, type = '', success = '' } = req.query;
  let { limit = '100', offset = '0' } = req.query;
  limit = Math.min(Math.max(parseInt(limit, 10) || 100, 1), 500);
  offset = Math.max(parseInt(offset, 10) || 0, 0);

  const where = [];
  const params = [];
  if (familyId) {
    where.push('pl.family_id = ?');
    params.push(parseInt(familyId, 10) || 0);
  }
  if (type) {
    if (!ALLOWED_TYPES.has(type)) return fail(res, 400, `type 必须是 ${[...ALLOWED_TYPES].join(' / ')} 之一`);
    where.push('pl.type = ?');
    params.push(type);
  }
  if (success === '1' || success === '0') {
    where.push('pl.success = ?');
    params.push(Number(success));
  }

  const whereSql = where.length ? `WHERE ${where.join(' AND ')}` : '';
  // limit / offset 直接拼（已经过 sanitize）—— mysql2 prepare 会拒绝数字外的占位符
  const sql = `
    SELECT pl.id, pl.user_id, pl.family_id, pl.menu_item_id, pl.type, pl.success,
           pl.error_msg, pl.payload, pl.created_at
      FROM t_push_log pl
      ${whereSql}
     ORDER BY pl.id DESC
     LIMIT ${limit} OFFSET ${offset}`;
  const rows = await q(sql, params);
  const data = await enrichRows(rows);

  // 总数（带相同过滤条件），用于分页
  const totalRow = await scalar(
    `SELECT COUNT(*) AS c FROM t_push_log pl ${whereSql}`,
    params
  );

  return ok(res, { total: Number(totalRow) || 0, list: data });
});

router.get('/push/stats', async (req, res) => {
  // 今日 0:00 起的总量 / 失败 / 各类型
  const today = await q(
    `SELECT type, success, COUNT(*) AS c
       FROM t_push_log
      WHERE created_at >= CURDATE()
      GROUP BY type, success`
  );
  const todayByType = { NEW_ORDER: { success: 0, failed: 0 }, STATUS_CHANGED: { success: 0, failed: 0 } };
  let todayTotal = 0, todayFailed = 0;
  for (const r of today) {
    todayTotal += Number(r.c);
    if (!r.success) todayFailed += Number(r.c);
    if (todayByType[r.type]) {
      todayByType[r.type][r.success ? 'success' : 'failed'] = Number(r.c);
    }
  }

  // 待发 / 重试队列
  const pendingRow = await scalar(
    `SELECT COUNT(*) AS c FROM t_push_pending WHERE status = 0`
  );
  const deadRow = await scalar(
    `SELECT COUNT(*) AS c FROM t_push_pending WHERE status = 2`
  );

  // dry-run 占比（最近 100 条）：给出「是不是还在真发」的快速信号
  const recent = await q(
    `SELECT success, error_msg, COUNT(*) AS c
       FROM (
         SELECT success, error_msg FROM t_push_log ORDER BY id DESC LIMIT 100
       ) t
      GROUP BY success, error_msg`
  );
  let recentTotal = 0, recentDryRun = 0;
  for (const r of recent) {
    recentTotal += Number(r.c);
    if (r.error_msg === 'dry-run') recentDryRun += Number(r.c);
  }

  return ok(res, {
    todayTotal,
    todayFailed,
    todayByType,
    pending: Number(pendingRow) || 0,
    dead: Number(deadRow) || 0,
    recent: {
      total: recentTotal,
      dryRun: recentDryRun,
      dryRunPct: recentTotal ? Math.round((recentDryRun * 1000) / recentTotal) / 10 : 0,
    },
  });
});

module.exports = router;